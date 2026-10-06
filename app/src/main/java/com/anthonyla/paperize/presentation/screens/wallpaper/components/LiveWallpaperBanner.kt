package com.anthonyla.paperize.presentation.screens.wallpaper.components

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import com.anthonyla.paperize.R
import com.anthonyla.paperize.service.livewallpaper.PaperizeLiveWallpaperService
import com.anthonyla.paperize.presentation.theme.AppSpacing

/**
 * Shown in live mode while Paperize is not the live wallpaper on any screen. Nothing is reset:
 * setting the wallpaper again picks up the same album and settings.
 */
@Composable
fun LiveWallpaperBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.small),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(start = AppSpacing.large, top = AppSpacing.large, end = AppSpacing.small),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = null)
                Text(
                    text = stringResource(R.string.live_wallpaper_not_set_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(
                text = stringResource(R.string.live_wallpaper_not_set_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = AppSpacing.small)
            )
            val xiaomi = isXiaomiFamily()
            if (xiaomi) {
                Text(
                    text = stringResource(R.string.live_wallpaper_permission_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = AppSpacing.small)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (xiaomi) {
                    TextButton(onClick = { openAppInfo(context) }) { Text(stringResource(R.string.app_info)) }
                }
                TextButton(onClick = { openLiveWallpaperPicker(context) }) {
                    Text(stringResource(R.string.set_live_wallpaper))
                }
            }
        }
    }
}

/** Opens the system preview of Paperize's live wallpaper, or the closest picker available. */
fun openLiveWallpaperPicker(context: Context) {
    val preview = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        ComponentName(context, PaperizeLiveWallpaperService::class.java)
    )
    val fallbacks = listOf(preview, Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER), Intent(Settings.ACTION_SETTINGS))
    for (intent in fallbacks) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}

private fun openAppInfo(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
    }
}

/**
 * HyperOS / MIUI close the live-wallpaper picker straight away until the app's own "Change
 * wallpaper" permission is allowed, so these phones get a hint.
 */
private fun isXiaomiFamily(): Boolean =
    Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")
