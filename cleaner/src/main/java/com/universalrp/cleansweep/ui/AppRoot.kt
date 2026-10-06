package com.universalrp.cleansweep.ui

import androidx.activity.compose.BackHandler
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
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.ui.theme.Bg
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary

@Composable
fun AppRoot(vm: MainViewModel) {
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
        // The system back button walks back through the screens that were opened instead
        // of closing the whole app — that was the v2.1 bug ("back closes the application").
        // Only Home lets Android leave the app.
        BackHandler(enabled = state.screen != Screen.HOME) { vm.goBack() }

        when (state.screen) {
            Screen.HOME -> HomeScreen(state, vm)
            Screen.SCANNING -> ScanScreen(state, vm)
            Screen.RESULTS -> ResultsScreen(state, vm)
            Screen.APP_CACHE -> AppCacheScreen(state, vm)
            Screen.ASSISTANT -> AssistantScreen(state, vm)
            Screen.SETTINGS -> SettingsScreen(state, vm)
            Screen.ABOUT -> AboutScreen(vm)
            Screen.HEALTH -> HealthScreen(state, vm)
            Screen.APPS -> AppsScreen(state, vm)
            Screen.SECURITY -> SecurityScreen(state, vm)
            Screen.NETWORK -> NetworkScreen(state, vm)
            Screen.MOBILE -> MobileScreen(state, vm)
            Screen.AI_SETTINGS -> AiSettingsScreen(state, vm)
            Screen.AI_REPORT -> AiReportScreen(state, vm)
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
}
