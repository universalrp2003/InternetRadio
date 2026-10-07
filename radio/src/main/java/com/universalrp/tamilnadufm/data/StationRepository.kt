package com.universalrp.tamilnadufm.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * All the station lists the app needs:
 *
 *  - **seed** — a JSON list shipped inside the APK (kept fresh by CI), so the app
 *    is useful on first launch and favourites always resolve to a name/logo,
 *  - **custom** — stations the user added or edited, stored locally,
 *  - **favourites / recent** — small id lists in SharedPreferences,
 *  - **discovered** — live directory results, kept in memory for the session.
 */
class StationRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tamilnadufm", Context.MODE_PRIVATE)

    @Volatile
    private var seed: List<RadioStation> = emptyList()

    /** Stops the seed reader from re-parsing a 1 MB asset on every keystroke. */
    suspend fun seedStations(): List<RadioStation> {
        val cached = seed
        if (cached.isNotEmpty()) return cached
        val loaded = withContext(Dispatchers.IO) { readSeed() }
        seed = loaded
        return loaded
    }

    private fun readSeed(): List<RadioStation> = try {
        val text = appContext.assets.open("stations_seed.json")
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(text)
        val array = root.optJSONArray("stations") ?: JSONArray()
        val list = ArrayList<RadioStation>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val station = RadioStation.fromJson(item)
            if (station.url.isNotBlank()) list.add(station)
        }
        list
    } catch (t: Throwable) {
        emptyList()
    }

    // ---------------------------------------------------------------- custom

    fun customStations(): List<RadioStation> = try {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        val array = JSONArray(raw)
        val list = ArrayList<RadioStation>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            list.add(RadioStation.fromJson(item, custom = true))
        }
        list
    } catch (t: Throwable) {
        emptyList()
    }

    fun addCustom(station: RadioStation) {
        val list = customStations().filterNot { it.url.equals(station.url, ignoreCase = true) }
        val array = JSONArray()
        (list + station.copy(isCustom = true)).forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_CUSTOM, array.toString()).apply()
    }

    fun removeCustom(url: String) {
        val array = JSONArray()
        customStations().filterNot { it.url.equals(url, ignoreCase = true) }
            .forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_CUSTOM, array.toString()).apply()
    }

    // ------------------------------------------------------------ favourites

    fun favourites(): Set<String> = prefs.getStringSet(KEY_FAVOURITES, emptySet()) ?: emptySet()

    fun isFavourite(station: RadioStation): Boolean =
        favourites().contains(station.url.lowercase())

    fun toggleFavourite(station: RadioStation): Boolean {
        val key = station.url.lowercase()
        val current = favourites().toMutableSet()
        val added = if (current.contains(key)) {
            current.remove(key); false
        } else {
            current.add(key); true
        }
        prefs.edit().putStringSet(KEY_FAVOURITES, current).apply()
        return added
    }

    // ---------------------------------------------------------------- recent

    fun recent(): List<String> = try {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    } catch (t: Throwable) {
        emptyList()
    }

    fun markPlayed(station: RadioStation) {
        val key = station.url.lowercase()
        val list = recent().filterNot { it == key }
        val trimmed = (listOf(key) + list).take(MAX_RECENT)
        val array = JSONArray()
        trimmed.forEach { array.put(it) }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
        // Keep the last station so the app can offer to resume it.
        prefs.edit()
            .putString(KEY_LAST_URL, station.url)
            .putString(KEY_LAST_NAME, station.name)
            .putString(KEY_LAST_FAVICON, station.favicon)
            .putString(KEY_LAST_ID, station.id)
            .apply()
    }

    fun lastPlayed(): RadioStation? {
        val url = prefs.getString(KEY_LAST_URL, null) ?: return null
        return RadioStation(
            id = prefs.getString(KEY_LAST_ID, null) ?: url,
            name = prefs.getString(KEY_LAST_NAME, null) ?: "Last station",
            url = url,
            favicon = prefs.getString(KEY_LAST_FAVICON, null) ?: "",
        )
    }

    // ------------------------------------------------------------ last error

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    // ------------------------------------------------------------ play queue (v1.3)
    //
    // Next/Previous must work when the app is closed (widget, notification, headset), so
    // the playback service cannot ask the ViewModel what "next" means. Instead every play()
    // saves the list it is playing from here; the service rebuilds it when its own queue
    // has a single item left (or nothing — after a reboot of the service).

    /**
     * Saves the queue as parallel url/name lists plus the position that is playing.
     * Local files are stored as their URI strings next to stream urls — the service tells
     * them apart by scheme (http = stream, anything else = a file on the phone).
     */
    fun saveQueue(urls: List<String>, names: List<String>, index: Int) {
        try {
            val capped = urls.take(MAX_QUEUE)
            val named = names.take(MAX_QUEUE)
            val json = JSONObject()
                .put("urls", JSONArray(capped))
                .put("names", JSONArray(named))
                .put("index", index.coerceIn(0, (capped.size - 1).coerceAtLeast(0)))
                .toString()
            prefs.edit().putString(KEY_QUEUE, json).apply()
        } catch (t: Throwable) {
            // A queue that cannot be saved is simply forgotten; playback is unaffected.
        }
    }

    /** The last saved queue: urls, names, and the index that was playing. Null when none. */
    fun loadQueue(): Triple<List<String>, List<String>, Int>? = try {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val json = JSONObject(raw)
        val urlArray = json.optJSONArray("urls") ?: return null
        val nameArray = json.optJSONArray("names")
        val urls = (0 until urlArray.length()).mapNotNull {
            urlArray.optString(it).takeIf { s -> s.isNotBlank() }
        }
        if (urls.isEmpty()) return null
        val names = (0 until urls.size).map { i -> nameArray?.optString(i).orEmpty() }
        Triple(urls, names, json.optInt("index", 0).coerceIn(0, urls.size - 1))
    } catch (t: Throwable) {
        null
    }

    private companion object {
        const val KEY_CUSTOM = "custom_stations"
        const val KEY_FAVOURITES = "favourite_urls"
        const val KEY_RECENT = "recent_urls"
        const val KEY_LAST_URL = "last_url"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_LAST_FAVICON = "last_favicon"
        const val KEY_LAST_ID = "last_id"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_QUEUE = "play_queue"
        const val MAX_RECENT = 25
        const val MAX_QUEUE = 400
    }
}
