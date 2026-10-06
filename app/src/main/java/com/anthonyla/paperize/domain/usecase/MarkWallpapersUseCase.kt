package com.anthonyla.paperize.domain.usecase

import android.util.Log
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.repository.AlbumRepository
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Favourite and exclude images (plan 5.2), and keep the rotation rounds in progress in line, so a
 * change shows up from the next wallpaper change rather than the next round. The image currently
 * on screen stays until then.
 */
class MarkWallpapersUseCase @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val settingsRepository: SettingsRepository
) {
    /** Returns how many images changed. */
    suspend fun setFavorite(albumId: String, wallpaperIds: Collection<String>, favorite: Boolean): Result<Int> =
        Result.runCatching {
            val favoritesOnlyBefore = wallpaperRepository.rotatesFavoritesOnly(albumId)
            val changed = albumRepository.setFavorite(albumId, wallpaperIds, favorite).getOrThrow()
            if (changed.isNotEmpty()) {
                syncRounds(albumId, favoritesOnlyBefore) { shuffle ->
                    wallpaperRepository.favoritesChanged(albumId, changed, favorite, shuffle).getOrThrow()
                }
            }
            changed.size
        }

    /** Returns how many images changed. Excluded images keep their place and are passed over. */
    suspend fun setExcluded(albumId: String, wallpaperIds: Collection<String>, excluded: Boolean): Result<Int> =
        Result.runCatching {
            val favoritesOnlyBefore = wallpaperRepository.rotatesFavoritesOnly(albumId)
            val changed = albumRepository.setExcluded(albumId, wallpaperIds, excluded).getOrThrow()
            if (changed.isNotEmpty()) {
                syncRounds(albumId, favoritesOnlyBefore) { shuffle ->
                    // A round built while they were excluded doesn't hold them; they join it now.
                    if (!excluded) wallpaperRepository.addToQueues(albumId, changed, shuffle).getOrThrow()
                }
            }
            changed.size
        }

    /** Keeping the rounds in line never fails the change itself; they catch up at the next round. */
    private suspend fun syncRounds(albumId: String, favoritesOnlyBefore: Boolean, adjust: suspend (shuffle: Boolean) -> Unit) {
        try {
            if (wallpaperRepository.rotatesFavoritesOnly(albumId) != favoritesOnlyBefore) {
                // "Favourites only" just took effect (the first usable favourite) or lapsed (the
                // last one went), so the set of rotating images changed as a whole.
                wallpaperRepository.clearQueues(albumId).getOrThrow()
            } else {
                adjust(settingsRepository.getScheduleSettings().shuffleEnabled)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Rotation rounds will catch up at the next round", e)
        }
    }

    private companion object {
        const val TAG = "MarkWallpapersUseCase"
    }
}
