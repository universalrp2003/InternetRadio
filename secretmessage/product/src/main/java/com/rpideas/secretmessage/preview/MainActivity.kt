package com.rpideas.secretmessage.preview

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.rpideas.secretmessage.preview.chat.ChatViewModel
import com.rpideas.secretmessage.preview.security.AppLockAvailability
import com.rpideas.secretmessage.preview.security.AppLockController
import com.rpideas.secretmessage.preview.ui.SecretMessageApp

/**
 * Local-only Secret Message preview hosting the WhisperPad product screens. It has no network
 * transport, pairing, MLS integration or message storage, and it is labelled NOT SECURE.
 */
class MainActivity : FragmentActivity() {
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var appLock: AppLockController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Best-effort screenshot deterrent only; it cannot prevent every capture path.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        chatViewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        appLock = AppLockController(this)
        appLock.lockForStart(chatViewModel.settings.value.appLockEnabled)
        setContent {
            SecretMessageApp(
                viewModel = chatViewModel,
                appLock = appLock,
                versionLabel = BuildConfig.VERSION_NAME,
                onUnlockRequest = ::requestUnlock,
                onAppLockToggle = ::toggleAppLock,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onStart()
    }

    override fun onResume() {
        super.onResume()
        chatViewModel.setForeground(true)
        if (!appLock.onResume(chatViewModel.settings.value.appLockEnabled)) {
            turnOffUnavailableLock()
        }
    }

    override fun onPause() {
        chatViewModel.setForeground(false)
        super.onPause()
    }

    override fun onStop() {
        appLock.onStop(chatViewModel.settings.value.appLockEnabled)
        super.onStop()
    }

    private fun requestUnlock() {
        if (!appLock.authenticate()) turnOffUnavailableLock()
    }

    /** Returns a message to show when the lock cannot be enabled on this phone. */
    private fun toggleAppLock(enabled: Boolean): String? {
        if (!enabled) {
            chatViewModel.setAppLockEnabled(false)
            appLock.unlock()
            return null
        }
        if (appLock.availability() != AppLockAvailability.AVAILABLE) {
            return "Set a screen lock (PIN, pattern, password or biometric) in Android Settings to use the app lock."
        }
        chatViewModel.setAppLockEnabled(true)
        return null
    }

    /** Removing the phone's screen lock requires the current credential, so turning the app lock off is safe. */
    private fun turnOffUnavailableLock() {
        chatViewModel.setAppLockEnabled(false)
        appLock.unlock(message = "App lock was turned off because this phone has no usable screen lock.")
    }
}
