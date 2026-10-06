package com.anthonyla.paperize.service.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeRequests
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What the tile shows: whether automatic changing runs. A tap always changes the wallpaper. */
internal enum class TileStatus { ON, PAUSED, NOT_SET_UP }

internal fun tileStatus(settings: ScheduleSettings, mode: WallpaperMode): TileStatus = when {
    !settings.hasRequiredAlbums(mode) -> TileStatus.NOT_SET_UP
    settings.enableChanger -> TileStatus.ON
    else -> TileStatus.PAUSED
}

/** Quick Settings tile: tap for the next wallpaper; highlighted while automatic changing is on. */
@AndroidEntryPoint
class WallpaperTileService : TileService() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var changeRequests: WallpaperChangeRequests

    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningScope?.cancel()
        listeningScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                combine(
                    settingsRepository.getScheduleSettingsFlow(),
                    settingsRepository.getWallpaperModeFlow(),
                    ::tileStatus
                ).distinctUntilChanged().collect(::showStatus)
            }
        }
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listeningScope?.cancel()
        listeningScope = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        changeRequests.changeConfigured()
    }

    private fun showStatus(status: TileStatus) {
        val tile = qsTile ?: return
        val stateText = getString(
            when (status) {
                TileStatus.ON -> R.string.tile_on
                TileStatus.PAUSED -> R.string.tile_paused
                TileStatus.NOT_SET_UP -> R.string.tile_no_album
            }
        )
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.label = getString(R.string.tile_label)
        tile.subtitle = stateText
        tile.stateDescription = stateText
        tile.state = when (status) {
            TileStatus.ON -> Tile.STATE_ACTIVE
            TileStatus.PAUSED -> Tile.STATE_INACTIVE
            // Nothing to change until an album is picked; Android greys the tile out.
            TileStatus.NOT_SET_UP -> Tile.STATE_UNAVAILABLE
        }
        tile.updateTile()
    }
}
