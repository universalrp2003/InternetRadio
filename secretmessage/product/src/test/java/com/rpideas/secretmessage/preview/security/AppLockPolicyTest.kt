package com.rpideas.secretmessage.preview.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun androidNineAndTenUseWeakBiometricWithDeviceCredential() {
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, AppLockPolicy.allowedAuthenticators(28))
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, AppLockPolicy.allowedAuthenticators(29))
    }

    @Test
    fun otherVersionsUseStrongBiometricWithDeviceCredential() {
        listOf(26, 27, 30, 33, 35, 36).forEach { sdk ->
            assertEquals("API $sdk", BIOMETRIC_STRONG or DEVICE_CREDENTIAL, AppLockPolicy.allowedAuthenticators(sdk))
        }
    }

    @Test
    fun locksOnStopOnlyWhenEnabledAndNotCausedByThePrompt() {
        assertTrue(AppLockPolicy.shouldLockOnStop(lockEnabled = true, authenticationInProgress = false))
        assertFalse(AppLockPolicy.shouldLockOnStop(lockEnabled = true, authenticationInProgress = true))
        assertFalse(AppLockPolicy.shouldLockOnStop(lockEnabled = false, authenticationInProgress = false))
    }

    @Test
    fun promptsAutomaticallyOnlyOnceAfterReturningAndNeverInADismissLoop() {
        assertTrue(
            AppLockPolicy.shouldAutoPrompt(
                lockEnabled = true,
                locked = true,
                authenticationInProgress = false,
                returnedToForeground = true,
                userDismissedPrompt = false,
            ),
        )
        assertFalse(AppLockPolicy.shouldAutoPrompt(true, true, false, returnedToForeground = false, userDismissedPrompt = false))
        assertFalse(AppLockPolicy.shouldAutoPrompt(true, true, false, returnedToForeground = true, userDismissedPrompt = true))
        assertFalse(AppLockPolicy.shouldAutoPrompt(true, true, true, returnedToForeground = true, userDismissedPrompt = false))
        assertFalse(AppLockPolicy.shouldAutoPrompt(true, false, false, returnedToForeground = true, userDismissedPrompt = false))
        assertFalse(AppLockPolicy.shouldAutoPrompt(false, true, false, returnedToForeground = true, userDismissedPrompt = false))
    }
}
