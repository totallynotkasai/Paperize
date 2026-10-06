package com.anthonyla.paperize.service.livewallpaper.renderer

import android.graphics.Bitmap
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.Wallpaper
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** How a live engine picks the image it shows. */
enum class LiveSelection {
    /** Show the recorded current image again; advance only when there is none or it can't be read. */
    RESUME,

    /** Take the next image from the queue. */
    ADVANCE,

    /** Show the current image, else the next one, without consuming the queue. */
    PEEK
}

/**
 * Chooses the image a live engine shows and decodes it. Like static mode, up to
 * [Constants.MAX_WALLPAPER_LOAD_RETRIES] queued images are tried until one decodes. The chosen
 * image is then pinned, so a surface change redraws it instead of advancing again.
 *
 * [decode] blocks and returns null for an image that cannot be read.
 */
class LiveWallpaperImageLoader(
    private val repository: WallpaperRepository,
    private val albumId: String,
    private val shuffle: Boolean,
    val selection: LiveSelection,
    private val decode: (wallpaper: Wallpaper, width: Int, height: Int) -> Bitmap?,
    pinned: Wallpaper? = null
) : ImageLoader {

    /** The image this loader shows, once chosen. */
    @Volatile var wallpaper: Wallpaper? = pinned
        private set

    override suspend fun load(targetWidth: Int, targetHeight: Int): Bitmap? {
        wallpaper?.let { return decode(it, targetWidth, targetHeight) }
        if (selection != LiveSelection.ADVANCE) {
            repository.getCurrentWallpaper(albumId, ScreenType.LIVE)?.let { current ->
                decode(current, targetWidth, targetHeight)?.let { bitmap ->
                    wallpaper = current
                    return bitmap
                }
            }
        }
        return if (selection == LiveSelection.PEEK) {
            peekNext(targetWidth, targetHeight)
        } else {
            advance(targetWidth, targetHeight)
        }
    }

    private suspend fun peekNext(targetWidth: Int, targetHeight: Int): Bitmap? {
        val next = repository.getNextWallpaperInQueue(albumId, ScreenType.LIVE) ?: run {
            repository.ensureWallpaperQueue(albumId, ScreenType.LIVE, shuffle).getOrThrow()
            repository.getNextWallpaperInQueue(albumId, ScreenType.LIVE)
        } ?: return null
        return decode(next, targetWidth, targetHeight)?.also { wallpaper = next }
    }

    private suspend fun advance(targetWidth: Int, targetHeight: Int): Bitmap? {
        repeat(Constants.MAX_WALLPAPER_LOAD_RETRIES) {
            val candidate = repository.getAndDequeueWallpaper(albumId, ScreenType.LIVE) ?: run {
                repository.ensureWallpaperQueue(albumId, ScreenType.LIVE, shuffle).getOrThrow()
                repository.getAndDequeueWallpaper(albumId, ScreenType.LIVE)
            } ?: return null
            var bitmap: Bitmap? = null
            try {
                bitmap = decode(candidate, targetWidth, targetHeight)
                currentCoroutineContext().ensureActive()
            } catch (e: CancellationException) {
                // A superseded request must not consume the image it had picked.
                bitmap?.recycle()
                withContext(NonCancellable) {
                    repository.restoreWallpaperToQueueFront(albumId, ScreenType.LIVE, candidate.id)
                }
                throw e
            }
            if (bitmap != null) {
                wallpaper = candidate
                return bitmap
            }
            // Unreadable images are skipped for this cycle; the album refresh handles removals.
        }
        return null
    }
}
