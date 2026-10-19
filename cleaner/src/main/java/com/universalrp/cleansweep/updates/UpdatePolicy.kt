package com.universalrp.cleansweep.updates

import java.net.URI
import com.universalrp.updates.ReleaseRepositorySources

object UpdatePolicy {
    const val PACKAGE = "com.universalrp.cleansweep"
    const val SIGNER_SHA256 = "9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55"
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    fun newer(candidate: String, installed: String): Boolean {
        fun parts(value: String): List<Int>? {
            if (!value.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}"))) return null
            return value.split('.').map { it.toIntOrNull() ?: return null }
        }
        val a = parts(candidate) ?: return false; val b = parts(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }; val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
    fun officialAsset(url: String, fileName: String): Boolean =
        ReleaseRepositorySources.ordered.any { source ->
            runCatching {
                val uri = URI(url)
                val suffix = uri.path.removePrefix("${source.canonicalPath}/releases/download/")
                uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
                    uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                    uri.path.startsWith("${source.canonicalPath}/releases/download/") &&
                    uri.path.substringAfterLast('/') == fileName && suffix.split('/').size == 2
            }.getOrDefault(false)
        }

    fun officialAsset(url: String, fileName: String, tag: String, repository: ReleaseRepositorySources.Source): Boolean =
        ReleaseRepositorySources.isOfficialAsset(url, repository, tag, fileName)
    fun digest(value: String?): String? = value?.removePrefix("sha256:")?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
    fun trustedSigner(signers: Set<String>, installed: Set<String>): Boolean = signers == setOf(SIGNER_SHA256) && signers == installed
    fun validSize(bytes: Long): Boolean = bytes in 1..MAX_APK_BYTES
}
