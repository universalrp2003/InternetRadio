package com.rpideas.secretmessage.preview.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Decisions for the optional local app lock (adapted from the WhisperPad BiometricAuthManager).
 *
 * AndroidX Biometric rejects BIOMETRIC_STRONG | DEVICE_CREDENTIAL on Android 9 and 10 (API 28–29),
 * so those versions use BIOMETRIC_WEAK | DEVICE_CREDENTIAL. The lock only hides this phone's UI; it
 * is not used for message protection and no biometric data leaves Android.
 */
object AppLockPolicy {
    fun allowedAuthenticators(sdkInt: Int): Int =
        if (sdkInt == 28 || sdkInt == 29) {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        }

    /** Lock when the app leaves the foreground, unless the unlock prompt itself caused it. */
    fun shouldLockOnStop(lockEnabled: Boolean, authenticationInProgress: Boolean): Boolean =
        lockEnabled && !authenticationInProgress

    /**
     * Prompt automatically once after returning to the foreground. A prompt the user dismissed is
     * not reopened in a loop; the lock screen offers an Unlock button instead.
     */
    fun shouldAutoPrompt(
        lockEnabled: Boolean,
        locked: Boolean,
        authenticationInProgress: Boolean,
        returnedToForeground: Boolean,
        userDismissedPrompt: Boolean,
    ): Boolean = lockEnabled && locked && !authenticationInProgress && returnedToForeground && !userDismissedPrompt
}
