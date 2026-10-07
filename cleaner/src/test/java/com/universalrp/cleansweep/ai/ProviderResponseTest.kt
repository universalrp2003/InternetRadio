package com.universalrp.cleansweep.ai
import org.junit.Assert.*
import org.junit.Test
class ProviderResponseTest {
    private fun extract(body: String): String {
        val method = AiClient::class.java.getDeclaredMethod("extractText", String::class.java)
        method.isAccessible = true
        return method.invoke(AiClient, body) as String
    }
    @Test fun toolOnlyResponseIsNotDisplayed() {
        assertEquals("", extract("""{"choices":[{"message":{"role":"assistant","reasoning":"internal","tool_calls":[{"function":{"name":"web.run"}}]},"finish_reason":"tool_calls"}]}"""))
    }
    @Test fun normalContentIsExtracted() {
        assertEquals("Hello", extract("""{"choices":[{"message":{"content":"Hello"}}]}"""))
    }
    @Test fun malformedJsonIsNotDisplayed() { assertEquals("", extract("{broken")) }
}
