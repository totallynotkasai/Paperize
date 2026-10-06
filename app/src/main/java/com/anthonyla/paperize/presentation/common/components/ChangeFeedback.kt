package com.anthonyla.paperize.presentation.common.components

import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.anthonyla.paperize.R
import com.anthonyla.paperize.service.wallpaper.PendingChangeResult
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeResult.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Shows the outcome of "Change wallpaper now" or "Set wallpaper" as a snackbar while the app is
 * visible. A result counts as shown ([onShown]) only once its snackbar has run its course, so if
 * the activity is re-created part-way (a new wallpaper often triggers that), it is shown again.
 */
@Composable
fun ChangeResultSnackbars(
    results: Flow<PendingChangeResult>,
    onShown: (PendingChangeResult) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val resources by rememberUpdatedState(LocalResources.current)
    val markShown by rememberUpdatedState(onShown)
    LaunchedEffect(results, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            results.collect { pending ->
                snackbarHostState.showSnackbar(changeResultMessage(resources, pending.result))
                markShown(pending)
            }
        }
    }
}

internal fun changeResultMessage(resources: Resources, result: WallpaperChangeResult): String =
    when (result.outcome) {
        Outcome.CHANGED -> resources.getString(
            if (result.kind == WallpaperChangeResult.Kind.SET_CHOSEN) R.string.wallpaper_set else R.string.wallpaper_changed
        )
        Outcome.EMPTY_ALBUM -> resources.getString(R.string.wallpaper_changer_disabled_empty_album)
        Outcome.LIVE_NOT_SET -> resources.getString(R.string.change_feedback_live_not_set)
        Outcome.NOTHING_TO_CHANGE -> resources.getString(R.string.change_feedback_nothing)
        Outcome.FAILED -> result.message?.let { resources.getString(R.string.change_failed_with_reason, it) }
            ?: resources.getString(R.string.change_failed)
    }
