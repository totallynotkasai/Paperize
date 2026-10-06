package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.domain.model.Folder
import com.anthonyla.paperize.presentation.theme.AppSpacing

/**
 * A folder in the album grid: its cover image with the name and image count over a scrim, and a
 * folder badge that tells it apart from single images. Screen readers hear one label for all of it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItem(
    folder: Folder,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    unavailable: Boolean = false
) {
    val context = LocalContext.current
    val count = folder.wallpapers.size
    val excludedCount = folder.wallpapers.count { it.excluded }
    val allExcluded = count > 0 && excludedCount == count
    // "12 wallpapers · 3 excluded" once some of the folder's images are excluded from rotation.
    val countText = pluralStringResource(R.plurals.wallpaper_count, count, count).let { total ->
        if (excludedCount == 0) total
        else stringResource(R.string.count_with_excluded, total, pluralStringResource(R.plurals.excluded_count, excludedCount, excludedCount))
    }
    val description = stringResource(R.string.content_desc_folder, folder.displayName, countText) +
        if (unavailable) ", " + stringResource(R.string.unavailable) else ""

    val cover = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(folder.coverUri?.toUri())
            .size(Size(Constants.GRID_THUMBNAIL_WIDTH, Constants.GRID_THUMBNAIL_HEIGHT))
            .build(),
        contentScale = ContentScale.Crop
    )
    val coverState by cover.state.collectAsState()
    val hasCover = coverState is AsyncImagePainter.State.Success

    val transition = updateTransition(isSelected, label = "FolderItemSelection")
    val paddingTransition by transition.animateDp(label = "padding") { selected ->
        if (selected) 5.dp else 0.dp
    }
    val roundedCornerShapeTransition by transition.animateDp(label = "roundedCornerShape") { selected ->
        if (selected) 24.dp else 16.dp
    }
    val contentAlpha = when {
        isSelected -> 0.7f
        unavailable -> UNAVAILABLE_ALPHA
        allExcluded -> EXCLUDED_ALPHA
        else -> 1f
    }

    Card(
        modifier = modifier
            .padding(paddingTransition)
            .semantics {
                contentDescription = description
                if (isSelectionMode) selected = isSelected
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(roundedCornerShapeTransition),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCover) {
                Image(
                    painter = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = contentAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(0.5f)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                )
            }

            // Name and count on a scrim, readable over any cover. The tile's own label covers them.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clearAndSetSemantics {}
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(horizontal = AppSpacing.small, vertical = AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = folder.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (unavailable) {
                UnavailableBadge(null, Modifier.align(Alignment.TopStart).padding(AppSpacing.small))
            }

            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopEnd).padding(AppSpacing.small)
                )
            }
        }
    }
}
