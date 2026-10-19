package com.rpideas.secretmessage.preview.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class AppLockAvailability { AVAILABLE, NOT_AVAILABLE }

/**
 * Optional local app lock using AndroidX BiometricPrompt: fingerprint/face or the phone's own
 * screen-lock PIN, pattern or password. Create it in Activity.onCreate.
 */
class AppLockController(private val activity: FragmentActivity) {
    var locked by mutableStateOf(false)
        private set

    var lastMessage by mutableStateOf<String?>(null)
        private set

    private var authenticationInProgress = false
    private var returnedToForeground = false
    private var userDismissedPrompt = false

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticationInProgress = false
                userDismissedPrompt = false
                locked = false
                lastMessage = null
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticationInProgress = false
                userDismissedPrompt = true
                lastMessage = errString.toString()
            }
        },
    )

    fun availability(): AppLockAvailability {
        val result = BiometricManager.from(activity)
            .canAuthenticate(AppLockPolicy.allowedAuthenticators(Build.VERSION.SDK_INT))
        return if (result == BiometricManager.BIOMETRIC_SUCCESS) {
            AppLockAvailability.AVAILABLE
        } else {
            AppLockAvailability.NOT_AVAILABLE
        }
    }

    /** A cold start with the lock enabled begins locked. */
    fun lockForStart(lockEnabled: Boolean) {
        locked = lockEnabled
    }

    fun onStart() {
        returnedToForeground = true
    }

    /** Returns false when the lock is enabled but this phone can no longer authenticate. */
    fun onResume(lockEnabled: Boolean): Boolean {
        val shouldPrompt = AppLockPolicy.shouldAutoPrompt(
            lockEnabled = lockEnabled,
            locked = locked,
            authenticationInProgress = authenticationInProgress,
            returnedToForeground = returnedToForeground,
            userDismissedPrompt = userDismissedPrompt,
        )
        returnedToForeground = false
        if (!lockEnabled) {
            locked = false
            return true
        }
        return if (shouldPrompt) authenticate() else true
    }

    fun onStop(lockEnabled: Boolean) {
        if (AppLockPolicy.shouldLockOnStop(lockEnabled, authenticationInProgress)) {
            locked = true
            userDismissedPrompt = false
        }
    }

    /** Show the unlock prompt. Returns false when the phone has no usable screen lock. */
    fun authenticate(): Boolean {
        if (authenticationInProgress) return true
        if (availability() != AppLockAvailability.AVAILABLE) return false
        return try {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Secret Message")
                .setSubtitle("Use your fingerprint, face or screen-lock PIN")
                .setAllowedAuthenticators(AppLockPolicy.allowedAuthenticators(Build.VERSION.SDK_INT))
                .build()
            authenticationInProgress = true
            userDismissedPrompt = false
            prompt.authenticate(info)
            true
        } catch (error: RuntimeException) {
            authenticationInProgress = false
            false
        }
    }

    fun unlock(message: String? = null) {
        if (authenticationInProgress) {
            prompt.cancelAuthentication()
            authenticationInProgress = false
        }
        locked = false
        lastMessage = message
    }
}
