package com.universalrp.updates

import java.net.URI

/**
 * Ordered public release sources for the staged RP Ideas migration.
 *
 * The new repository is checked first; the old InternetRadio source remains a verified
 * fallback during the migration grace period. Oor Arivippu uses both sources from its first
 * stable release. Do not remove the old source until all four legacy bridge versions are confirmed.
 */
object ReleaseRepositorySources {
    data class Source(val owner: String, val repository: String) {
        val slug: String get() = "$owner/$repository"
        val apiUrl: String get() = "https://api.github.com/repos/$slug/releases"
        val canonicalPath: String get() = "/$slug"

        fun releasesPage(page: Int, perPage: Int = PAGE_SIZE): String =
            "$apiUrl?per_page=${perPage.coerceIn(1, PAGE_SIZE)}&page=${page.coerceAtLeast(1)}"

        fun assetPath(tag: String, fileName: String): String =
            "$canonicalPath/releases/download/$tag/$fileName"

        fun releasePagePath(tag: String): String = "$canonicalPath/releases/tag/$tag"
    }

    const val PAGE_SIZE = 100
    const val MAX_RELEASE_PAGES = 20

    val ordered: List<Source> = listOf(
        Source("universalrp2003", "RP-Android-Apps"),
        Source("universalrp2003", "InternetRadio"),
    )

    fun bySlug(slug: String): Source? = ordered.firstOrNull { it.slug == slug }

    fun isOfficialAsset(url: String, source: Source, tag: String, fileName: String): Boolean =
        isCanonicalHttpsUrl(url, source.assetPath(tag, fileName))

    fun isOfficialReleasePage(url: String, source: Source, tag: String): Boolean =
        isCanonicalHttpsUrl(url, source.releasePagePath(tag))

    private fun isCanonicalHttpsUrl(value: String, expectedPath: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.path == expectedPath
    }.getOrDefault(false)
}
