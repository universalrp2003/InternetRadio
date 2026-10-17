package com.rpideas.secretmessage.wifitest

/**
 * A short-lived same-Wi-Fi invite: the host phone's private IPv4 address, its listening port and a
 * one-time token. It is a bearer invite for one connection attempt, not identity verification; the
 * people still compare the safety code. Smoke-test invites (`secretmessage://join`) are refused.
 */
internal data class InviteLink(val host: String, val port: Int, val token: String) {
    fun toUri(): String = "$PREFIX?host=$host&port=$port&token=$token&v=$VERSION"

    companion object {
        const val SCHEME = "secretmessage"
        const val HOST = "chat"
        const val PREFIX = "$SCHEME://$HOST"

        /** Bump together with the native WIFI_CHAT protocol tag (secretmessage-wifi-chat/N). */
        const val VERSION = "1"

        /** Finds the invite in pasted or shared text; the message of the exception is for the screen. */
        fun parse(text: String): InviteLink {
            val start = text.indexOf(PREFIX)
            require(start >= 0) {
                if (text.contains("secretmessage://join")) {
                    "That invite is for the separate MLS smoke test app. Create the invite in Secret Message Wi-Fi TEST."
                } else {
                    "No Secret Message Wi-Fi invite was found. Copy the whole secretmessage://chat link and try again."
                }
            }
            val link = text.substring(start).trim().split(' ', '\n', '\r', '\t').first().trimEnd('.', ',', ')', ']')
            val parameters = link.substringAfter('?', "")
                .split('&')
                .mapNotNull { part ->
                    val equals = part.indexOf('=')
                    if (equals <= 0) null else part.substring(0, equals) to part.substring(equals + 1)
                }
                .toMap()
            require(parameters["v"] == VERSION) {
                "This invite comes from a different Wi-Fi test build. Install the same build on both phones and create a new invite."
            }
            val host = parameters["host"].orEmpty()
            require(isPrivateIpv4(host)) { "The invite does not contain a private Wi-Fi address." }
            val port = parameters["port"]?.toIntOrNull()
            require(port != null && port in 1..65535) { "The invite's port is invalid." }
            val token = parameters["token"].orEmpty()
            require(token.length == 32 && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "The invite's one-time token is invalid."
            }
            return InviteLink(host, port, token)
        }

        /** True for 10.x.x.x, 172.16–31.x.x and 192.168.x.x addresses in dotted-decimal form. */
        fun isPrivateIpv4(value: String): Boolean {
            val parts = value.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { part ->
                if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return false
                part.toInt().also { if (it > 255) return false }
            }
            return octets[0] == 10 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168)
        }
    }
}
