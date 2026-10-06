package com.anthonyla.paperize.domain.usecase

import com.anthonyla.paperize.core.WallpaperSourceType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.util.SupportedImageFormats
import com.anthonyla.paperize.core.util.generateId
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.source.DocumentSource
import com.anthonyla.paperize.domain.source.SourceImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What an import did, for the message shown afterwards. */
data class ImportResult(
    val added: Int,
    val skippedUnsupported: Int = 0,
    val alreadyInAlbum: Boolean = false,
    /** Persisted grants held once the import finished, for the "nearing the limit" warning. */
    val grantsInUse: Int = 0
) {
    val nearGrantLimit: Boolean get() = grantsInUse >= Constants.PERSISTED_URI_GRANT_WARNING
}

/**
 * Taking the grants would exceed Android's per-app limit, past which it silently drops the oldest
 * grants and the images they cover stop working. Nothing was imported.
 */
class GrantLimitException(val needed: Int, val available: Int) :
    Exception("Import needs $needed file grants but only $available are left")

class ImportWallpapersUseCase @Inject constructor(
    private val documents: DocumentSource,
    private val albumRepository: AlbumRepository
) {
    suspend fun addImages(albumId: String, uris: List<String>, onSaving: (Int, Int) -> Unit): ImportResult {
        // The picker's temporary grant is enough to read names and types before keeping access.
        val (images, unsupported) = uris.distinct().map { documents.readImage(it) }
            .partition { SupportedImageFormats.isSupported(it.name, it.mimeType) }
        if (images.isEmpty()) return ImportResult(added = 0, skippedUnsupported = unsupported.size)
        val held = documents.persistedReadGrants()
        val newGrants = images.map { it.uri }.filterNot { it in held }
        checkGrantLimit(held.size, newGrants.size)
        val added = releasingOnFailure(newGrants) {
            images.forEach { documents.retainReadPermission(it.uri) }
            albumRepository.addWallpapersToAlbum(albumId, images.map { it.toWallpaper(albumId) }, onSaving)
                .getOrThrow()
        }
        // Re-adding an image whose access was lost restores it.
        albumRepository.syncAccess(albumId)
        return ImportResult(
            added = added,
            skippedUnsupported = unsupported.size,
            alreadyInAlbum = added == 0,
            grantsInUse = held.size + newGrants.size
        )
    }

    suspend fun addFolder(
        albumId: String,
        uri: String,
        onScanning: (Int) -> Unit,
        onSaving: (Int, Int) -> Unit
    ): ImportResult {
        val held = documents.persistedReadGrants()
        val newGrants = if (uri in held) emptyList() else listOf(uri)
        checkGrantLimit(held.size, newGrants.size)
        documents.retainReadPermission(uri)
        val grantsInUse = held.size + newGrants.size
        // Avoid rescanning an existing folder; picking it again restores its access.
        if (albumRepository.getAlbumById(albumId).first()?.folders.orEmpty().any { it.uri == uri }) {
            albumRepository.syncAccess(albumId)
            return ImportResult(added = 0, alreadyInAlbum = true, grantsInUse = grantsInUse)
        }
        return releasingOnFailure(newGrants) {
            val source = documents.readFolder(uri, onScanning)
            val folderId = generateId()
            val folder = Folder(
                id = folderId, albumId = albumId, uri = uri, name = source.name,
                coverUri = null, dateModified = System.currentTimeMillis(),
                wallpapers = source.images.sortedBy { it.uri }.map { it.toWallpaper(albumId, folderId) }
            )
            val inserted = albumRepository.addFolderToAlbum(albumId, folder, onSaving).getOrThrow()
            ImportResult(
                added = if (inserted) folder.wallpapers.size else 0,
                skippedUnsupported = source.skippedUnsupported,
                alreadyInAlbum = !inserted,
                grantsInUse = grantsInUse
            )
        }
    }

    /**
     * Grant access again to directly added images that lost it. Each picked file matches a lost
     * image by its URI or, when it was picked from a different location, by file name.
     * Returns how many images can be read again.
     */
    suspend fun restoreImageAccess(albumId: String, uris: List<String>): Int {
        val album = albumRepository.getAlbumById(albumId).first() ?: return 0
        val unmatched = album.wallpapers.filter { it.accessLost }.toMutableList()
        val readable = album.wallpapers.filterNot { it.accessLost }.mapTo(HashSet()) { it.uri }
        val matches = uris.distinct().filterNot { it in readable }.mapNotNull { uri ->
            val match = unmatched.firstOrNull { it.uri == uri }
                ?: documents.readImage(uri).name.let { name -> unmatched.firstOrNull { it.fileName == name } }
            match?.also { unmatched.remove(it) }?.let { it to uri }
        }
        if (matches.isEmpty()) return 0
        val held = documents.persistedReadGrants()
        checkGrantLimit(held.size, matches.count { (_, uri) -> uri !in held })
        matches.forEach { (wallpaper, uri) ->
            documents.retainReadPermission(uri)
            if (wallpaper.uri != uri) albumRepository.relinkWallpaper(wallpaper.id, uri).getOrThrow()
        }
        albumRepository.syncAccess(albumId).getOrThrow()
        return matches.size
    }

    /** Returns false when [uri] is not one of the album's folders, so nothing was granted. */
    suspend fun restoreFolderAccess(albumId: String, uri: String): Boolean {
        val album = albumRepository.getAlbumById(albumId).first() ?: return false
        if (album.folders.none { it.uri == uri }) return false
        documents.retainReadPermission(uri)
        albumRepository.syncAccess(albumId).getOrThrow()
        return true
    }

    private fun checkGrantLimit(held: Int, needed: Int) {
        val available = (Constants.MAX_PERSISTED_URI_GRANTS - held).coerceAtLeast(0)
        if (needed > available) throw GrantLimitException(needed, available)
    }

    /** Give back grants taken for an import that did not finish, so failures leak none. */
    private suspend fun <T> releasingOnFailure(newGrants: List<String>, block: suspend () -> T): T = try {
        block()
    } catch (e: Throwable) {
        withContext(NonCancellable) {
            newGrants.forEach { uri ->
                try {
                    documents.releaseReadPermission(uri)
                } catch (releaseError: Exception) {
                    if (releaseError is CancellationException) throw releaseError
                    e.addSuppressed(releaseError)
                }
            }
        }
        throw e
    }
}

internal fun SourceImage.toWallpaper(albumId: String, folderId: String? = null) = Wallpaper(
    id = generateId(), albumId = albumId, folderId = folderId, uri = uri,
    fileName = name, dateModified = modifiedAt,
    sourceType = if (folderId == null) WallpaperSourceType.DIRECT else WallpaperSourceType.FOLDER
)
