package com.anthonyla.paperize.domain.usecase

import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.source.DocumentSource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class FolderRefreshResult(val added: Int, val removed: Int)

/** Brings a folder in line with one scan of it: new files join, deleted files leave. */
class RefreshFolderUseCase @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val documents: DocumentSource,
    private val addToRotation: AddToRotationUseCase
) {
    suspend operator fun invoke(folderId: String): Result<FolderRefreshResult> = Result.runCatching {
        val folder = albumRepository.getFolderById(folderId).first()
            ?: return@runCatching FolderRefreshResult(0, 0)
        // A scan that can't read the folder throws instead of returning nothing.
        val source = documents.readFolder(folder.uri)
        val found = source.images.mapTo(HashSet()) { it.uri }
        // Some providers list a deleted folder as empty; the album refresh removes the whole
        // folder then, so don't empty it here.
        val removed = if (found.isEmpty() && documents.isMissing(folder.uri, isTree = true)) 0
            else albumRepository.removeFolderImagesNotIn(folderId, found).getOrThrow()
        // Same order as the first import; new files then join the end of this folder.
        val wallpapers = source.images.sortedBy { it.uri }.map { it.toWallpaper(folder.albumId, folderId) }
        val added = albumRepository.addWallpapersToAlbum(folder.albumId, wallpapers).getOrThrow()
        if (added > 0) addToRotation(folder.albumId, wallpapers.map { it.id })
        FolderRefreshResult(added, removed)
    }
}
