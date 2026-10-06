package com.anthonyla.paperize.domain.source

data class SourceImage(val uri: String, val name: String, val modifiedAt: Long, val mimeType: String? = null)

/** [skippedUnsupported] counts images left out because Paperize cannot decode their format. */
data class SourceFolder(val name: String, val images: List<SourceImage>, val skippedUnsupported: Int = 0)

interface DocumentSource {
    suspend fun retainReadPermission(uri: String)

    /** Gives back a persisted grant; does nothing if none is held. */
    suspend fun releaseReadPermission(uri: String)

    /** URIs of every persisted read grant this app holds (files and folder trees). */
    suspend fun persistedReadGrants(): Set<String>

    suspend fun readImage(uri: String): SourceImage
    suspend fun readFolder(uri: String, onProgress: (Int) -> Unit = {}): SourceFolder
    suspend fun isMissing(uri: String, isTree: Boolean = false): Boolean
}
