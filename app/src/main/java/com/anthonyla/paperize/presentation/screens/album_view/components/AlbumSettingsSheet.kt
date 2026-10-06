package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.FavoritesMode
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.WallpaperEffects
import com.anthonyla.paperize.presentation.screens.album_view.allImages
import com.anthonyla.paperize.presentation.screens.wallpaper.components.SettingSwitchWithSlider
import com.anthonyla.paperize.presentation.theme.AppSpacing
import kotlinx.coroutines.launch

/**
 * Album settings (plan 5.5): the album's name (5.1), what its favourites do (5.2) and its own
 * effects (5.3). Every change is saved straight away.
 *
 * [onEffectsChange] gets null when custom effects are turned off, and `defer = true` for slider
 * levels, whose re-render waits briefly for further edits. [startingEffects] seeds custom effects
 * when they are turned on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSettingsSheet(
    album: Album,
    liveMode: Boolean,
    shuffleEnabled: Boolean,
    onRename: () -> Unit,
    onFavoritesModeChange: (FavoritesMode) -> Unit,
    startingEffects: suspend () -> WallpaperEffects,
    onEffectsChange: (effects: WallpaperEffects?, defer: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.album_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(AppSpacing.large).semantics { heading() }
            )
            HorizontalDivider()

            ListItem(
                content = { Text(stringResource(R.string.album_name_setting)) },
                supportingContent = { Text(album.name) },
                trailingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                colors = sheetListItemColors(),
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.rename_album), onClick = onRename)
            )
            HorizontalDivider()

            FavoritesSection(album, shuffleEnabled, onFavoritesModeChange)
            HorizontalDivider()

            CustomEffectsSection(album.effects, liveMode, startingEffects, onEffectsChange)
            Spacer(Modifier.height(AppSpacing.large))
        }
    }
}

/** Rows sit straight on the sheet rather than on their own surface. */
@Composable
private fun sheetListItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun SectionTitle(text: String, supporting: String? = null) {
    Column(
        modifier = Modifier.padding(start = AppSpacing.large, end = AppSpacing.large, top = AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FavoritesSection(album: Album, shuffleEnabled: Boolean, onFavoritesModeChange: (FavoritesMode) -> Unit) {
    val images = album.allImages()
    val favorites = images.count { it.favorite }
    SectionTitle(
        text = stringResource(R.string.favorites_title),
        supporting = pluralStringResource(R.plurals.favorites_count, favorites, favorites)
    )
    Column(Modifier.selectableGroup()) {
        FavoritesMode.entries.forEach { mode ->
            val selected = album.favoritesMode == mode
            ListItem(
                content = { Text(stringResource(mode.label)) },
                supportingContent = { Text(stringResource(mode.description)) },
                leadingContent = { RadioButton(selected = selected, onClick = null) },
                colors = sheetListItemColors(),
                modifier = Modifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onFavoritesModeChange(mode) }
                )
            )
        }
    }
    // Say when the chosen mode can't do anything yet.
    val hint = when {
        album.favoritesMode == FavoritesMode.SHOW_MORE_OFTEN && !shuffleEnabled -> R.string.favorites_more_often_needs_shuffle
        album.favoritesMode == FavoritesMode.FAVORITES_ONLY &&
            images.none { it.favorite && !it.excluded && !it.accessLost } -> R.string.favorites_only_none_yet
        else -> null
    }
    if (hint != null) {
        Text(
            text = stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = AppSpacing.large, end = AppSpacing.large, bottom = AppSpacing.medium)
        )
    }
}

private val FavoritesMode.label: Int
    get() = when (this) {
        FavoritesMode.MARKER_ONLY -> R.string.favorites_mode_marker
        FavoritesMode.SHOW_MORE_OFTEN -> R.string.favorites_mode_more_often
        FavoritesMode.FAVORITES_ONLY -> R.string.favorites_mode_only
    }

private val FavoritesMode.description: Int
    get() = when (this) {
        FavoritesMode.MARKER_ONLY -> R.string.favorites_mode_marker_description
        FavoritesMode.SHOW_MORE_OFTEN -> R.string.favorites_mode_more_often_description
        FavoritesMode.FAVORITES_ONLY -> R.string.favorites_mode_only_description
    }

@Composable
private fun CustomEffectsSection(
    saved: WallpaperEffects?,
    liveMode: Boolean,
    startingEffects: suspend () -> WallpaperEffects,
    onEffectsChange: (WallpaperEffects?, Boolean) -> Unit
) {
    // Shown at once while the saved value catches up.
    var effects by remember(saved) { mutableStateOf(saved) }
    val scope = rememberCoroutineScope()
    fun update(updated: WallpaperEffects?, defer: Boolean = false) {
        effects = updated
        onEffectsChange(updated, defer)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = effects != null, role = Role.Switch) { on ->
                if (on) scope.launch { update(startingEffects()) } else update(null)
            }
            .padding(AppSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
            Text(
                text = stringResource(R.string.custom_effects_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(if (liveMode) R.string.custom_effects_description_live else R.string.custom_effects_description_static),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = effects != null, onCheckedChange = null)
    }

    val current = effects ?: return
    Column(
        modifier = Modifier.padding(horizontal = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        AlbumEffect(R.string.change_brightness, R.string.change_the_image_brightness,
            current.enableDarken, current.darkenPercentage,
            onCheckedChange = { update(current.copy(enableDarken = it)) },
            onPercentageChange = { update(current.copy(darkenPercentage = it), defer = true) })
        AlbumEffect(R.string.change_blur, R.string.add_blur_to_the_image,
            current.enableBlur, current.blurPercentage,
            onCheckedChange = { update(current.copy(enableBlur = it)) },
            onPercentageChange = { update(current.copy(blurPercentage = it), defer = true) })
        AlbumEffect(R.string.change_vignette, R.string.darken_the_edges_of_the_image,
            current.enableVignette, current.vignettePercentage,
            onCheckedChange = { update(current.copy(enableVignette = it)) },
            onPercentageChange = { update(current.copy(vignettePercentage = it), defer = true) })
        AlbumEffect(R.string.gray_filter, R.string.make_the_colors_grayscale,
            current.enableGrayscale, current.grayscalePercentage,
            onCheckedChange = { update(current.copy(enableGrayscale = it)) },
            onPercentageChange = { update(current.copy(grayscalePercentage = it), defer = true) })
    }
}

@Composable
private fun AlbumEffect(
    title: Int,
    description: Int,
    checked: Boolean,
    percentage: Int,
    onCheckedChange: (Boolean) -> Unit,
    onPercentageChange: (Int) -> Unit
) {
    SettingSwitchWithSlider(
        title = title,
        description = description,
        checked = checked,
        onCheckedChange = onCheckedChange,
        bothEnabled = false,
        homePercentage = percentage,
        lockPercentage = percentage,
        onPercentageChange = { value, _ -> onPercentageChange(value) }
    )
}
