package com.anthonyla.paperize.service.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.anthonyla.paperize.core.EmptyAlbumException
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.util.setBitmapChecked
import com.anthonyla.paperize.domain.model.PreparedWallpaper
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.usecase.ChangeWallpaperUseCase
import com.anthonyla.paperize.domain.usecase.ReapplyEffectsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class WallpaperChangeOutcome(val changed: Boolean = false, val emptyAlbum: Boolean = false)

/** Call while holding WallpaperChangeLock so applying and resetting schedules stay ordered. */
class WallpaperController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wallpaperManager: WallpaperManager,
    private val prepare: ChangeWallpaperUseCase,
    private val render: ReapplyEffectsUseCase,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Move [screen] on to its next image. BOTH changes each turned-on static screen from its own
     * queue, Home first, so screens sharing an album end up on different images. Screens that are
     * turned off are left alone, even though they keep their album.
     */
    suspend fun change(screen: ScreenType, settings: ScheduleSettings): WallpaperChangeOutcome = when (screen) {
        ScreenType.LIVE -> {
            context.sendBroadcast(Intent(Constants.ACTION_RELOAD_WALLPAPER).setPackage(context.packageName))
            WallpaperChangeOutcome(changed = true)
        }
        ScreenType.HOME, ScreenType.LOCK -> changeSelected(settings.albumFor(screen), screen)
        ScreenType.BOTH -> settings.rotatingStaticScreens().fold(WallpaperChangeOutcome()) { outcome, target ->
            combine(outcome, changeSelected(settings.albumFor(target), target))
        }
    }

    private suspend fun changeSelected(albumId: String?, screen: ScreenType): WallpaperChangeOutcome {
        if (albumId == null) return WallpaperChangeOutcome()
        val prepared = prepareOrDisable(albumId, screen) ?: return WallpaperChangeOutcome(emptyAlbum = true)
        applyPrepared(prepared, screen)
        return WallpaperChangeOutcome(changed = true)
    }

    private suspend fun prepareOrDisable(albumId: String, screen: ScreenType): PreparedWallpaper? {
        try {
            return prepare(albumId, screen).getOrThrow()
        } catch (_: EmptyAlbumException) {
            settingsRepository.clearEmptyAlbumSelection(albumId, screen)
            return null
        }
    }

    private suspend fun applyPrepared(prepared: PreparedWallpaper, screen: ScreenType) {
        var accepted = false
        try {
            currentCoroutineContext().ensureActive()
            wallpaperManager.setBitmapChecked(prepared.bitmap, screen.flags())
            accepted = true
            // Once Android accepts the bitmap, cancellation must not leave our current item stale.
            withContext(NonCancellable) {
                screen.staticScreens().forEach { prepare.complete(prepared, it) }
            }
        } catch (e: Exception) {
            if (!accepted) withContext(NonCancellable) { prepare.restore(prepared) }
            throw e
        } finally {
            prepared.bitmap.recycle()
        }
    }

    suspend fun applySpecific(albumId: String, wallpaperId: String, screen: ScreenType, settings: ScheduleSettings) {
        val targets = if (screen == ScreenType.BOTH && !settings.sameStaticPresentation()) {
            listOf(ScreenType.HOME, ScreenType.LOCK)
        } else listOf(screen)
        for (target in targets) {
            val renderScreen = if (target == ScreenType.BOTH) ScreenType.HOME else target
            val bitmap = render(albumId, renderScreen, wallpaperId).getOrThrow()
            applyBitmap(bitmap, target) {
                target.staticScreens().forEach {
                    prepare.completeSpecific(albumId, it, wallpaperId, settings.shuffleEnabled)
                }
            }
        }
    }

    /**
     * Re-render the current image of each turned-on screen with the latest effects. Only while
     * changing is on may a screen with no usable current image move on to its next one; while
     * paused, effects never change which image is shown.
     */
    suspend fun reapply(screen: ScreenType, settings: ScheduleSettings): WallpaperChangeOutcome {
        if (screen == ScreenType.LIVE) return WallpaperChangeOutcome()
        var outcome = WallpaperChangeOutcome()
        for (target in screen.staticScreens()) {
            val albumId = settings.albumFor(target) ?: continue
            val bitmap = render(albumId, target).getOrNull()
            val result = when {
                bitmap != null -> {
                    applyBitmap(bitmap, target)
                    WallpaperChangeOutcome(changed = true)
                }
                settings.enableChanger -> changeSelected(albumId, target)
                else -> WallpaperChangeOutcome()
            }
            outcome = combine(outcome, result)
        }
        return outcome
    }

    private suspend fun applyBitmap(bitmap: Bitmap, screen: ScreenType, onApplied: suspend () -> Unit = {}) {
        try {
            currentCoroutineContext().ensureActive()
            wallpaperManager.setBitmapChecked(bitmap, screen.flags())
            withContext(NonCancellable) { onApplied() }
        } finally {
            bitmap.recycle()
        }
    }

    private fun combine(first: WallpaperChangeOutcome, second: WallpaperChangeOutcome) = WallpaperChangeOutcome(
        changed = first.changed || second.changed, emptyAlbum = first.emptyAlbum || second.emptyAlbum
    )

    private fun ScheduleSettings.sameStaticPresentation() =
        homeEffects == lockEffects && homeScalingType == lockScalingType && !homeScrollingEnabled

    private fun ScreenType.staticScreens() = if (this == ScreenType.BOTH) listOf(ScreenType.HOME, ScreenType.LOCK) else listOf(this)

    private fun ScreenType.flags(): Int = when (this) {
        ScreenType.HOME -> WallpaperManager.FLAG_SYSTEM
        ScreenType.LOCK -> WallpaperManager.FLAG_LOCK
        ScreenType.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        ScreenType.LIVE -> error("Live wallpaper is applied by its engine")
    }
}
