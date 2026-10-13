package com.universalrp.cleansweep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalrp.cleansweep.data.Lang
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

    /** True once the widget's "Clean" tap has been turned into a scan for this activity. */
    private var widgetActionHandled = false
    private val alertOwner = "activity:" + java.util.UUID.randomUUID().toString()

    /** Android 13+: the charging card needs this, or nothing shows in the status bar. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // v2.6: remember a crash on the phone itself, so the next bug report can say what
        // actually happened instead of "MIUI said it stopped".
        CrashLog.install(this)
        // 2.42: resolve the language BEFORE the first composition. tr()/Lang.t() read the
        // process-wide Lang.current, which defaults to Tamil, so a screen composed before this
        // line ran could render Tamil on a phone whose saved language is English — one half of
        // the mixed-language cards the owner reported. LanguageSourcePolicy documents the rule.
        Lang.set(Lang.languageIn(this))
        // Sensitive permissions are explained and requested from reviewed setup, not silently on launch.
        com.universalrp.cleansweep.updates.LiveGuardUpdates.checkOnLaunch(this)
        setContent {
            CleanSweepTheme {
                val vm: MainViewModel = viewModel()

                // The widget's "Clean" button opens the app on the scanner (Android does not
                // allow starting that work straight from a widget tap). v2.6: this used to
                // only *show* the scanning screen, so the scan screen sat at "Starting…"
                // with no scan behind it. Now it really starts one, and the action is only
                // handled once per activity so a rotation cannot restart it.
                DisposableEffect(Unit) {
                    if (!widgetActionHandled &&
                        intent?.action == com.universalrp.cleansweep.widget.CleanSweepWidget.ACTION_CLEAN
                    ) {
                        widgetActionHandled = true
                        vm.openScanner()
                    } else if (!widgetActionHandled && intent?.action == com.universalrp.cleansweep.widget.CleanSweepWidget.ACTION_DETAILS) {
                        widgetActionHandled = true
                        vm.navigate(Screen.HEALTH)
                    }
                    if (intent?.action == com.universalrp.cleansweep.notify.SetupReminder.ACTION) vm.navigate(Screen.FEATURE_SETUP)
                    onDispose { }
                }

                // Refresh permissions/storage info every time the user comes back
                // from a Settings screen (files access, usage access).
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) { vm.refresh(); vm.checkSetupReminders() }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                AppRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.universalrp.cleansweep.notify.AutomaticAlerts.start(applicationContext, alertOwner)
    }

    override fun onStop() {
        com.universalrp.cleansweep.notify.AutomaticAlerts.stop(alertOwner)
        super.onStop()
    }

    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requestLegacyStorageIfNeeded() {
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
