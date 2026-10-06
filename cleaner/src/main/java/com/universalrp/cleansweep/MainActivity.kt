package com.universalrp.cleansweep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalrp.cleansweep.ui.AppRoot
import com.universalrp.cleansweep.ui.theme.CleanSweepTheme

class MainActivity : ComponentActivity() {

    /**
     * Android 8/9/10 only: deleting a scanned file needs runtime WRITE_EXTERNAL_STORAGE.
     * Asking here (once) is what makes the cleaner actually free space on phones like the
     * Oppo A3s, where the app used to report "0 MB cleaned" over and over.
     */
    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    /** Android 13+: the charging card needs this, or nothing shows in the status bar. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLegacyStorageIfNeeded()
        requestNotificationsIfNeeded()
        setContent {
            CleanSweepTheme {
                val vm: MainViewModel = viewModel()

                // Refresh permissions/storage info every time the user comes back
                // from a Settings screen (files access, usage access).
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                AppRoot(vm)
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestLegacyStorageIfNeeded() {
        if (Build.VERSION.SDK_INT >= 30) return
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        val missing = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) storagePermission.launch(permissions)
    }
}
