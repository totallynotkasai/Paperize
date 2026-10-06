package com.anthonyla.paperize.service.wallpaper

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** What a change the user asked for in the app did, so the app can say so. */
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

/** A result waiting to be shown; [id] tells two identical results apart. */
data class PendingChangeResult(val id: Long, val result: WallpaperChangeResult, val publishedAt: Long)

/**
 * Feedback for changes the user asked for in the app ("Change wallpaper now", "Set wallpaper").
 * The service and the fallback job run in the app's process, so they report here directly.
 *
 * A new wallpaper often makes Android re-create the app's activity (the system colours follow the
 * wallpaper), so a result is kept until a screen has shown it in full rather than handed only to
 * whoever is listening at that instant.
 */
@Singleton
class WallpaperChangeEvents internal constructor(
    private val appInForeground: () -> Boolean,
    private val clock: () -> Long
) {
    @Inject constructor() : this(
        appInForeground = { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
        clock = SystemClock::elapsedRealtime
    )

    private val pendingCount = MutableStateFlow(0)

    /** How many reported requests are still running; the asking screen shows them as busy. */
    val pending: StateFlow<Int> = pendingCount.asStateFlow()

    private val ids = AtomicLong()
    private val unshown = MutableStateFlow<PendingChangeResult?>(null)

    /** The latest result no screen has finished showing; older ones are dropped unseen. */
    val results: Flow<PendingChangeResult> = unshown.filterNotNull().filter { pending ->
        (clock() - pending.publishedAt <= MAX_AGE_MS).also { fresh -> if (!fresh) shown(pending) }
    }

    fun begin() = pendingCount.update { it + 1 }

    fun end() = pendingCount.update { (it - 1).coerceAtLeast(0) }

    /** Whether the app will show [result]; if it is in the background, the caller notifies instead. */
    fun publish(result: WallpaperChangeResult): Boolean {
        if (!appInForeground()) return false
        unshown.value = PendingChangeResult(ids.incrementAndGet(), result, clock())
        return true
    }

    /** A screen showed [pending] in full; a newer result stays. */
    fun shown(pending: PendingChangeResult) {
        unshown.compareAndSet(pending, null)
    }

    private companion object {
        /** A result not shown within this long (e.g. the app was closed meanwhile) is dropped. */
        const val MAX_AGE_MS = 10_000L
    }
}
