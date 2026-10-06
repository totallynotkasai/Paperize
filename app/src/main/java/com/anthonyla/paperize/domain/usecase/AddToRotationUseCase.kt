package com.anthonyla.paperize.domain.usecase

import android.util.Log
import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.repository.WallpaperRepository
import javax.inject.Inject

/**
 * Joins newly added images to the rotation rounds already in progress, instead of starting every
 * round again. Failing here never fails the import: the images still join at the next round.
 */
class AddToRotationUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(albumId: String, wallpaperIds: Collection<String>) {
        if (wallpaperIds.isEmpty()) return
        val result = Result.runCatching {
            val shuffle = settingsRepository.getScheduleSettings().shuffleEnabled
            wallpaperRepository.addToQueues(albumId, wallpaperIds, shuffle).getOrThrow()
        }
        if (result is Result.Error) Log.w(TAG, "New images will join at the next round", result.exception)
    }

    private companion object {
        const val TAG = "AddToRotationUseCase"
    }
}
