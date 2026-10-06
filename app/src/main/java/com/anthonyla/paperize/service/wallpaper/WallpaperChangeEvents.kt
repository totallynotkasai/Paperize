package com.anthonyla.paperize.service.wallpaper

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** What a change the user asked for in the app did, so the screen that asked can say so. */
data class WallpaperChangeResult(
    val kind: Kind,
    val outcome: Outcome,
    /** Why it failed, when known. */
    val message: String? = null
) {
    enum class Kind { CHANGE, SET_CHOSEN }

    enum class Outcome {
        CHANGED,
        /** The album had no images left, so its selection was cleared. */
        EMPTY_ALBUM,
        /** Live mode, but Paperize isn't the live wallpaper, so nothing visible changed. */
        LIVE_NOT_SET,
        /** No turned-on screen had an album. */
        NOTHING_TO_CHANGE,
        FAILED
    }
}

/**
 * Feedback for changes the user asked for in the app ("Change wallpaper now", "Set wallpaper").
 * The service and the fallback job run in the app's process, so they report here directly.
 */
@Singleton
class WallpaperChangeEvents @Inject constructor() {
    private val pendingCount = MutableStateFlow(0)

    /** How many reported requests are still running; the asking screen shows them as busy. */
    val pending: StateFlow<Int> = pendingCount.asStateFlow()

    private val _results = MutableSharedFlow<WallpaperChangeResult>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Collect only while a screen is visible: with no collector, results become notifications. */
    val results: SharedFlow<WallpaperChangeResult> = _results.asSharedFlow()

    fun begin() = pendingCount.update { it + 1 }

    fun end() = pendingCount.update { (it - 1).coerceAtLeast(0) }

    /** Whether a visible screen took [result]; if not, the caller falls back to a notification. */
    fun publish(result: WallpaperChangeResult): Boolean =
        _results.subscriptionCount.value > 0 && _results.tryEmit(result)
}
