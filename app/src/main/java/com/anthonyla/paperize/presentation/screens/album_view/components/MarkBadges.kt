package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.theme.AppSpacing

/** How strongly an image excluded from rotation is dimmed in the grid. */
internal const val EXCLUDED_ALPHA = 0.45f

/** Small marks for a favourite and for an image excluded from rotation; readable over any image. */
@Composable
internal fun MarkBadges(favorite: Boolean, excluded: Boolean, modifier: Modifier = Modifier) {
    if (!favorite && !excluded) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
        if (favorite) MarkBadge(Icons.Default.Favorite, FavoriteRed)
        if (excluded) MarkBadge(Icons.Default.DoNotDisturbOn, Color.White)
    }
}

@Composable
private fun MarkBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

private val FavoriteRed = Color(0xFFFF6B81)

/** One label for an image tile: its name, then what is special about it. */
@Composable
internal fun imageDescription(name: String, favorite: Boolean, excluded: Boolean, unavailable: Boolean): String =
    listOfNotNull(
        name,
        stringResource(R.string.state_favorite).takeIf { favorite },
        stringResource(R.string.state_excluded).takeIf { excluded },
        stringResource(R.string.state_unavailable).takeIf { unavailable }
    ).joinToString(stringResource(R.string.list_separator))
