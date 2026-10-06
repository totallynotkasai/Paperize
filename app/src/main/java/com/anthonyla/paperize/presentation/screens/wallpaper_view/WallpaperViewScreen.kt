package com.anthonyla.paperize.presentation.screens.wallpaper_view

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.presentation.common.components.ChangeResultSnackbars
import com.anthonyla.paperize.presentation.theme.AppSpacing
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

/** The viewer always shows the image on black, so its controls stay light in either theme. */
private val ViewerContent = Color.White
private val ViewerSecondaryContent = Color.White.copy(alpha = 0.8f)
private val ViewerBarScrim = Color.Black.copy(alpha = 0.6f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperViewScreen(
    wallpaperUri: String,
    wallpaperName: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WallpaperViewViewModel = hiltViewModel()
) {
    val zoomState = rememberZoomState()
    val wallpaperMode by viewModel.wallpaperMode.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    var showApplyDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    ChangeResultSnackbars(viewModel.changeResults, snackbarHostState)

    LightSystemBarIcons()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        contentColor = ViewerContent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = ViewerContent,
                    navigationIconContentColor = ViewerContent
                ),
                modifier = Modifier.background(
                    Brush.verticalGradient(listOf(ViewerBarScrim, Color.Transparent))
                ),
                title = {
                    if (zoomState.scale <= 1.01f) {
                        Text(
                            text = wallpaperName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = ViewerContent)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.Transparent,
                contentColor = ViewerContent,
                modifier = Modifier.background(
                    Brush.verticalGradient(listOf(Color.Transparent, ViewerBarScrim))
                ),
                actions = {
                    if (wallpaperMode == WallpaperMode.STATIC) {
                        Button(
                            onClick = { showApplyDialog = true },
                            enabled = !applying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.large)
                        ) {
                            Text(stringResource(if (applying) R.string.setting_wallpaper else R.string.set_wallpaper))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.individual_wallpaper_static_only),
                            color = ViewerSecondaryContent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.large)
                        )
                    }
                }
            )
        }
    ) { padding ->
        // The image may be zoomed under the bars; their scrims keep the controls readable.
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(padding)
                    .zoomable(zoomState)
            ) {
                AsyncImage(
                    model = wallpaperUri.toUri(),
                    contentDescription = wallpaperName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showApplyDialog) {
        AlertDialog(
            onDismissRequest = { showApplyDialog = false },
            title = { Text(stringResource(R.string.set_wallpaper)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ScreenType.HOME to R.string.home_screen_btn,
                        ScreenType.LOCK to R.string.lock_screen_btn,
                        ScreenType.BOTH to R.string.home_and_lock_screens
                    ).forEach { (screenType, label) ->
                        TextButton(
                            onClick = {
                                showApplyDialog = false
                                viewModel.applyTo(screenType)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApplyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Light status and navigation bar icons over the black viewer; the theme's own come back on exit. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}
