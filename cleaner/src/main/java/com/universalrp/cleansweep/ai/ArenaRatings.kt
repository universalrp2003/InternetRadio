package com.universalrp.cleansweep.ai

import android.util.Base64
import com.universalrp.cleansweep.net.BoundedHttp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Official LM Arena public repository; selected model matching happens locally, not in the request URL. */
object ArenaRatings {
    const val SOURCE = "https://github.com/lmarena/arena-catalog/blob/main/data/leaderboard-text.json"
    const val LEADERBOARD = "https://lmarena.ai/leaderboard/text"
    data class Snapshot(val rows: List<ArenaRatingPolicy.Score>, val changedAt: Long, val fetchedAt: Long, val commit: String)
    @Volatile private var cached: Snapshot? = null
    suspend fun load(refresh: Boolean = false): Snapshot? = withContext(Dispatchers.Default) {
        val previous = cached
        if (!refresh && previous != null && System.currentTimeMillis() - previous.fetchedAt in 0..86_400_000L) return@withContext previous
        withTimeoutOrNull(15_000) {
            val base = "https://api.github.com/repos/lmarena/arena-catalog"
            val headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "LiveGuard")
            val commitsReply = BoundedHttp.request("$base/commits?path=data%2Fleaderboard-text.json&per_page=1", 5_000, headers = headers)
            check(commitsReply.code == 200)
            val record = JSONArray(commitsReply.body).getJSONObject(0)
            val commit = record.getString("sha").also { check(it.matches(Regex("[a-f0-9]{40}"))) }
            val changed = Instant.parse(record.getJSONObject("commit").getJSONObject("committer").getString("date")).toEpochMilli()
            // Pin the contents lookup to the file's actual commit to avoid combining new scores with an old date.
            val metaReply = BoundedHttp.request("$base/contents/data/leaderboard-text.json?ref=$commit", 4_000, headers = headers)
            check(metaReply.code == 200)
            val meta = JSONObject(metaReply.body)
            check(meta.getLong("size") in 1..2_000_000)
            val blob = meta.getString("sha").also { check(it.matches(Regex("[a-f0-9]{40}"))) }
            val reply = BoundedHttp.request("$base/git/blobs/$blob", 6_000, headers = headers, maxBytes = 3_000_000)
            check(reply.code == 200)
            val encoded = JSONObject(reply.body); check(encoded.getString("encoding") == "base64")
            val bytes = Base64.decode(encoded.getString("content"), Base64.DEFAULT)
            check(bytes.size in 1..2_000_000 && bytes.size.toLong() == meta.getLong("size"))
            // Git blob SHA covers the exact bytes returned at the immutable commit, not a guessed web score.
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            digest.update("blob ${bytes.size}\u0000".toByteArray()); digest.update(bytes)
            check(digest.digest().joinToString("") { "%02x".format(it) } == blob)
            val full = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONObject(ArenaRatingPolicy.CATEGORY)
            val rows = full.keys().asSequence().take(2_000).mapNotNull { id ->
                val row = full.optJSONObject(id) ?: return@mapNotNull null
                val rating = row.optDouble("rating", Double.NaN).takeIf { ArenaRatingPolicy.valid(it) } ?: return@mapNotNull null
                ArenaRatingPolicy.Score(id, rating, row.optDouble("rating_q025", Double.NaN).takeIf { ArenaRatingPolicy.valid(it) },
                    row.optDouble("rating_q975", Double.NaN).takeIf { ArenaRatingPolicy.valid(it) })
            }.toList()
            Snapshot(rows, changed, System.currentTimeMillis(), commit).also { cached = it }
        }
    }
}
