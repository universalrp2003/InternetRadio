package com.universalrp.cleansweep.ai

import org.junit.Assert.*
import org.junit.Test

class AiFailurePolicyTest {
    @Test fun timeoutIsActionable() { assertTrue(AiFailurePolicy.message("socket timeout").contains("timed out")) }
    @Test fun authenticationIsNotMislabelledAsOffline() { assertTrue(AiFailurePolicy.message("HTTP 401: invalid credentials").contains("saved key")) }
    @Test fun deniedAccessIsNotAUniversalKeyFailure() { assertTrue(AiFailurePolicy.message("HTTP 403: denied").contains("account permissions")) }
    @Test fun rateLimitAndQuotaHaveOneVisibleMessage() { assertEquals(AiFailurePolicy.message("HTTP 429"), AiFailurePolicy.message("quota exhausted")) }
    @Test fun modelErrorsSuggestLoadingModels() { assertTrue(AiFailurePolicy.message("HTTP 404: model unavailable").contains("Load available models")) }
    @Test fun providerOutageIsNotADeviceSafetyClaim() { assertTrue(AiFailurePolicy.message("HTTP 503").contains("temporarily unavailable")) }
    @Test fun blankFinalAnswerDoesNotLookLikeSuccess() { assertTrue(AiFailurePolicy.message("no final answer").contains("no final answer")) }
    @Test fun externalErrorDetailsAndKeysAreNeverEchoed() { val result = AiFailurePolicy.message("<html>secret_api_key example.com/private</html>"); assertFalse(result.contains("secret_api_key")); assertFalse(result.contains("example.com")); assertTrue(result.contains("retry")) }
}
