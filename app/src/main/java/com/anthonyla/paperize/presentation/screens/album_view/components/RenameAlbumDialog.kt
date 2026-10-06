package com.anthonyla.paperize.presentation.screens.album_view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.anthonyla.paperize.R
import com.anthonyla.paperize.presentation.common.components.AlbumNameDialog
import com.anthonyla.paperize.presentation.screens.album_view.RenameOutcome
import kotlinx.coroutines.launch

/** Rename an album (plan 5.1). Names are unique regardless of case; the dialog says when one is taken. */
@Composable
fun RenameAlbumDialog(
    currentName: String,
    onRename: suspend (String) -> RenameOutcome,
    onDismiss: () -> Unit
) {
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlbumNameDialog(
        title = stringResource(R.string.rename_album),
        initialName = currentName,
        onDismiss = onDismiss,
        onConfirm = { name ->
            saving = true
            error = null
            scope.launch {
                try {
                    when (onRename(name)) {
                        RenameOutcome.RENAMED -> onDismiss()
                        RenameOutcome.NAME_TAKEN -> error = R.string.album_name_exists_error
                        RenameOutcome.FAILED -> error = R.string.album_rename_failed
                    }
                } finally {
                    saving = false
                }
            }
        },
        errorMessage = error?.let { stringResource(it) },
        isSaving = saving
    )
}
