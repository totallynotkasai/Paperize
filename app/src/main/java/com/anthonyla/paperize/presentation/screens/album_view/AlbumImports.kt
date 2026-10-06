package com.anthonyla.paperize.presentation.screens.album_view

import android.util.Log
import com.anthonyla.paperize.domain.usecase.GrantLimitException
import com.anthonyla.paperize.domain.usecase.ImportResult
import com.anthonyla.paperize.domain.usecase.ImportWallpapersUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How an import ended, kept until the album's screen has shown it. */
sealed interface ImportOutcome {
    data class Finished(val result: ImportResult) : ImportOutcome
    data class LimitReached(val needed: Int, val available: Int) : ImportOutcome
    data class Failed(val permissionProblem: Boolean) : ImportOutcome
}

data class AlbumImport(
    val progress: ImportProgress = ImportProgress.Idle,
    val outcome: ImportOutcome? = null
)

/**
 * Runs album imports outside any screen, so leaving the album (or the app, while Paperize keeps
 * running) doesn't cancel them. One import per album at a time; its outcome waits here until the
 * album's screen collects it, even if that screen is opened again later.
 */
@Singleton
class AlbumImports internal constructor(
    private val importWallpapers: ImportWallpapersUseCase,
    private val scope: CoroutineScope
) {
    @Inject constructor(importWallpapers: ImportWallpapersUseCase) :
        this(importWallpapers, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val imports = MutableStateFlow<Map<String, AlbumImport>>(emptyMap())
    private val jobs = mutableMapOf<String, Job>()

    fun state(albumId: String): Flow<AlbumImport> =
        imports.map { it[albumId] ?: AlbumImport() }.distinctUntilChanged()

    fun isRunning(albumId: String): Boolean = synchronized(jobs) { jobs[albumId]?.isActive == true }

    /** Returns false if this album is already importing. */
    fun addImages(albumId: String, uris: List<String>): Boolean =
        start(albumId, ImportProgress.Saving(0, uris.size)) { report ->
            importWallpapers.addImages(albumId, uris) { saved, total -> report(ImportProgress.Saving(saved, total)) }
        }

    /** Returns false if this album is already importing. */
    fun addFolder(albumId: String, uri: String): Boolean =
        start(albumId, ImportProgress.Scanning(0)) { report ->
            importWallpapers.addFolder(
                albumId, uri,
                onScanning = { report(ImportProgress.Scanning(it)) },
                onSaving = { saved, total -> report(ImportProgress.Saving(saved, total)) }
            )
        }

    fun cancel(albumId: String) {
        synchronized(jobs) { jobs[albumId] }?.cancel()
    }

    /** The screen has shown [albumId]'s outcome. */
    fun consumeOutcome(albumId: String) = edit(albumId) { it.copy(outcome = null) }

    private fun start(
        albumId: String,
        initialProgress: ImportProgress,
        block: suspend (report: (ImportProgress) -> Unit) -> ImportResult
    ): Boolean = synchronized(jobs) {
        if (jobs[albumId]?.isActive == true) return false
        edit(albumId) { AlbumImport(progress = initialProgress) }
        jobs[albumId] = scope.launch {
            val outcome = try {
                ImportOutcome.Finished(block { progress -> edit(albumId) { it.copy(progress = progress) } })
            } catch (e: CancellationException) {
                throw e
            } catch (e: GrantLimitException) {
                ImportOutcome.LimitReached(e.needed, e.available)
            } catch (e: SecurityException) {
                Log.e(TAG, "Import permission failed", e)
                ImportOutcome.Failed(permissionProblem = true)
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                ImportOutcome.Failed(permissionProblem = false)
            }
            edit(albumId) { AlbumImport(outcome = outcome) }
        }.also { job ->
            // Cancelled imports (also before they start) end quietly.
            job.invokeOnCompletion {
                synchronized(jobs) { if (jobs[albumId] === job) jobs.remove(albumId) }
                edit(albumId) { it.copy(progress = ImportProgress.Idle) }
            }
        }
        true
    }

    private fun edit(albumId: String, transform: (AlbumImport) -> AlbumImport) = imports.update { all ->
        val updated = transform(all[albumId] ?: AlbumImport())
        if (updated == AlbumImport()) all - albumId else all + (albumId to updated)
    }

    private companion object {
        const val TAG = "AlbumImports"
    }
}
