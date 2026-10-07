package com.universalrp.appforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalrp.appforge.BScreen
import com.universalrp.appforge.BuilderViewModel

@Composable
fun ForgeRoot(vm: BuilderViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.dismissMessage()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        when (state.screen) {
            BScreen.HOME -> ForgeHomeScreen(state, vm)
            BScreen.EDITOR -> EditorScreen(state, vm)
            BScreen.PREVIEW -> PreviewScreen(state, vm)
            BScreen.EXPORT -> ExportScreen(state, vm)
            BScreen.ABOUT -> ForgeAboutScreen(vm)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = SurfaceHigh,
                contentColor = TextPrimary,
            )
        }
    }

    if (state.showTemplates) {
        TemplatePickerDialog(vm)
    }
}
