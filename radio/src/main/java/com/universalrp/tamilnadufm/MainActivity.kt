package com.universalrp.tamilnadufm

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.universalrp.tamilnadufm.audio.AudioFx
import com.universalrp.tamilnadufm.player.PlaybackService
import com.universalrp.tamilnadufm.ui.AppRoot
import com.universalrp.tamilnadufm.ui.TamilFmTheme

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AudioFx.load(this)

        // Android 13+: the background-player notification needs this.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TamilFmTheme {
                com.universalrp.updates.AppUpdates.Control("Ramesh-Radio", automatic = true)
                val vm: MainViewModel = viewModel()
                AppRoot(vm, controller.value)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (controllerFuture == null) {
            // Bind to the playback service: this is what keeps the sound going in
            // the background, and what gives us the notification, headset buttons
            // and lock-screen controls.
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            val future = MediaController.Builder(this, token).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    if (controllerFuture === future) controller.value = runCatching { future.get() }.getOrNull()
                },
                MoreExecutors.directExecutor(),
            )
        }
    }

    override fun onStop() {
        controller.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}
