package com.universalrp.cleansweep.ai

import org.junit.Assert.*
import org.junit.Test

class AssistantModePolicyTest {
    @Test fun onlineEnglishDoesNotClaimOfflineExecution() { val text = AssistantModePolicy.welcome(true, true, false); assertTrue(text.contains("Online AI mode is selected")); assertFalse(text.contains("entirely offline")) }
    @Test fun onlineTamilNamesOnlineMode() { assertTrue(AssistantModePolicy.welcome(true, true, true).contains("ஆன்லைன் AI முறை")) }
    @Test fun configuredModelIsNotExecutionProof() { assertTrue(AssistantModePolicy.welcome(true, true, false).contains("not proof")); assertTrue(AssistantModePolicy.welcome(true, true, true).contains("சான்றல்ல")) }
    @Test fun missingProviderSetupIsExplicitInEnglish() { assertTrue(AssistantModePolicy.welcome(true, false, false).contains("provider still needs setup")) }
    @Test fun missingProviderSetupIsExplicitInTamil() { assertTrue(AssistantModePolicy.welcome(true, false, true).contains("சேவையை இன்னும் அமைக்க")) }
    @Test fun offlineModeIsHonestlyLabelledInBothLanguages() { assertTrue(AssistantModePolicy.welcome(false, true, false).contains("Local/offline mode")); assertTrue(AssistantModePolicy.welcome(false, true, true).contains("உள்ளூர்/ஆஃப்லைன்")) }
}
