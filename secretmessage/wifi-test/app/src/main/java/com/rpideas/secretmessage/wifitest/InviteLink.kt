package com.rpideas.secretmessage.wifitest

/**
 * A short-lived invite for one chat: over the Internet, a random room on the owner's relay; on
 * the same Wi-Fi, the host phone's private IPv4 address and listening port. Both carry a one-time
 * token. An invite is a bearer invite for one connection attempt, not identity verification; the
 * people still compare the safety code. Smoke-test invites (`secretmessage://join`) are refused.
 */
internal sealed class InviteLink {
    abstract val token: String

    abstract fun toUri(): String

    /** Both phones meet in [room] on the relay ([RelayLink.RELAY_URL] is built into the app). */
    data class Internet(val room: String, override val token: String) : InviteLink() {
        override fun toUri(): String = "$PREFIX?relay=$room&token=$token&v=$VERSION"
    }

    /** The other phone connects directly to [host]:[port] on the same Wi-Fi. */
    data class Wifi(val host: String, val port: Int, override val token: String) : InviteLink() {
        override fun toUri(): String = "$PREFIX?host=$host&port=$port&token=$token&v=$VERSION"
    }

    companion object {
        const val SCHEME = "secretmessage"
        const val HOST = "chat"
        const val PREFIX = "$SCHEME://$HOST"

        /**
         * The invite format. Version 2 added Internet invites; bump it when the invite format
         * changes, and bump it together with the native WIFI_CHAT protocol tag
         * (secretmessage-wifi-chat/N) when the session protocol changes.
         */
        const val VERSION = "2"

        /** Finds the invite in pasted or shared text; the message of the exception is for the screen. */
        fun parse(text: String): InviteLink {
            val start = text.indexOf(PREFIX)
            require(start >= 0) {
                if (text.contains("secretmessage://join")) {
                    "That invite is for the separate MLS smoke test app. Create the invite in Secret Message."
                } else {
                    "No Secret Message invite was found. Copy the whole secretmessage://chat link and try again."
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
                "This invite comes from a different version of Secret Message. Install the same version on both " +
                    "phones and create a new invite."
            }
            val token = parameters["token"].orEmpty()
            require(isHex(token, 32)) { "The invite's one-time token is invalid." }
            parameters["relay"]?.let { room ->
                require(isHex(room, 32) && room == room.lowercase()) { "The invite's relay room is invalid." }
                return Internet(room, token)
            }
            val host = parameters["host"].orEmpty()
            require(isPrivateIpv4(host)) { "The invite does not contain a private Wi-Fi address." }
            val port = parameters["port"]?.toIntOrNull()
            require(port != null && port in 1..65535) { "The invite's port is invalid." }
            return Wifi(host, port, token)
        }

        private fun isHex(value: String, length: Int): Boolean =
            value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

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
