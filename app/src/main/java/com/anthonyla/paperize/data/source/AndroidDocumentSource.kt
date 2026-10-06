package com.anthonyla.paperize.data.source

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.anthonyla.paperize.core.util.isDocumentMissing
import com.anthonyla.paperize.core.util.scanFolderImages
import com.anthonyla.paperize.domain.source.DocumentSource
import com.anthonyla.paperize.domain.source.SourceFolder
import com.anthonyla.paperize.domain.source.SourceImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AndroidDocumentSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DocumentSource {
    override suspend fun retainReadPermission(uri: String): Unit = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override suspend fun releaseReadPermission(uri: String): Unit = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Android throws when no persisted grant exists, e.g. one it already dropped.
            Log.d(TAG, "No grant to release for $uri", e)
        }
    }

    override suspend fun persistedReadGrants(): Set<String> = withContext(Dispatchers.IO) {
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet()) { it.uri.toString() }
    }

    override suspend fun readImage(uri: String): SourceImage = withContext(Dispatchers.IO) {
        val document = DocumentFile.fromSingleUri(context, uri.toUri())
        SourceImage(
            uri = uri,
            name = document?.name ?: uri.substringAfterLast('/'),
            modifiedAt = document?.lastModified() ?: 0L,
            mimeType = document?.type ?: context.contentResolver.getType(uri.toUri())
        )
    }

    override suspend fun readFolder(uri: String, onProgress: (Int) -> Unit): SourceFolder = withContext(Dispatchers.IO) {
        val tree = uri.toUri()
        var skipped = 0
        val images = tree.scanFolderImages(context, onProgress) { skipped = it }.map {
            SourceImage(it.uri.toString(), it.name, it.lastModified)
        }
        SourceFolder(DocumentFile.fromTreeUri(context, tree)?.name ?: uri.substringAfterLast('/'), images, skipped)
    }

    override suspend fun isMissing(uri: String, isTree: Boolean): Boolean = withContext(Dispatchers.IO) {
        val source = uri.toUri()
        val document = if (isTree) {
            DocumentsContract.buildDocumentUriUsingTree(source, DocumentsContract.getTreeDocumentId(source))
        } else source
        document.isDocumentMissing(context.contentResolver)
    }

    private companion object {
        const val TAG = "AndroidDocumentSource"
    }
}
