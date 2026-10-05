package com.universalrp.cleansweep.ai

import com.universalrp.cleansweep.data.AppCacheInfo
import com.universalrp.cleansweep.data.ScanReport
import com.universalrp.cleansweep.data.StorageInfo
import com.universalrp.cleansweep.data.formatBytes

/** One bubble in the assistant chat. */
data class AssistantMessage(
    val fromUser: Boolean,
    val text: String,
    val actionLabel: String? = null,
    val action: AssistantAction = AssistantAction.NONE,
    val atMs: Long = System.currentTimeMillis(),
)

/** Optional one-tap follow-up that the assistant can offer with an answer. */
enum class AssistantAction { NONE, SCAN, OPEN_APP_CACHE, OPEN_RESULTS, OPEN_SETTINGS }

/**
 * A snapshot of everything the assistant is allowed to know.
 * It is built from the current screen state — nothing is fetched from a server,
 * because CleanSweep has no INTERNET permission at all.
 */
data class AssistantContext(
    val storage: StorageInfo? = null,
    val report: ScanReport? = null,
    val lastCleanBytes: Long = 0L,
    val lastCleanItems: Int = 0,
    val hasAllFilesAccess: Boolean = false,
    val usageAccess: Boolean = false,
    val appCacheCount: Int = 0,
    val totalAppCacheBytes: Long = 0L,
    val topAppCache: AppCacheInfo? = null,
)

/**
 * CleanSweep's on-device assistant.
 *
 * It is a fully offline intent-matching engine: the question is normalised in
 * memory, matched against intent rules, and answered with text that is computed
 * from your real storage numbers. There is no network call, no API key, no
 * account and no model download — your questions never leave the phone.
 */
object Assistant {

    // ------------------------------------------------------------------ public

    fun welcome(ctx: AssistantContext, verbose: Boolean): AssistantMessage {
        val storageLine = ctx.storage?.let {
            "You have ${it.free.formatBytes()} free out of ${it.total.formatBytes()}."
        } ?: "Storage numbers are still loading."

        val full = buildString {
            append("Hi! I'm the CleanSweep assistant — I run entirely on this phone, offline.\n\n")
            append("$storageLine I can explain what is eating your space, tell you what is safe to delete, ")
            append("and walk you through clearing app caches step by step.\n\n")
            append("Try one of the suggestions below, or ask me anything in your own words.")
        }
        return AssistantMessage(fromUser = false, text = if (verbose) full else storageLine)
    }

    /** Context-aware quick questions shown as chips in the chat. */
    fun suggestionChips(ctx: AssistantContext): List<String> {
        val chips = mutableListOf<String>()
        if (ctx.report == null) {
            chips += "What can you do?"
            chips += "How do I free up space fast?"
            chips += "What is safe to delete?"
        } else {
            chips += "What did the scan find?"
            chips += "What is taking the most space?"
            chips += "What is safe to delete?"
        }
        if (!ctx.usageAccess) chips += "How do I clear app caches?"
        else if (ctx.totalAppCacheBytes > 0L) chips += "Clear app caches"
        if (!ctx.hasAllFilesAccess) chips += "Why do you need file access?"
        if (ctx.lastCleanBytes > 0L) chips += "How much did we free already?"
        chips += "Is my data private?"
        return chips.distinct().take(5)
    }

    /** Main entry point: answer a free-form question from the user. */
    fun answer(rawQuestion: String, ctx: AssistantContext, verbose: Boolean): AssistantMessage {
        val q = normalise(rawQuestion)
        val reply: AssistantMessage = when {
            q.isEmpty() -> AssistantMessage(false, "Type a question and I'll do my best — I work offline, so I answer from what I can see on your phone.")

            q.matchesAny("hello", "hi ", "hi!", "hey", "namaste", "vanakkam", "good morning", "good evening") ->
                greeting(ctx)

            q.matchesAny("thank", "thanks", "thx", "super", "great job", "nice work") ->
                AssistantMessage(false, "Happy to help! Tap “Scan & clean junk” whenever you want another pass — and if you're chasing space, ask me “what is taking the most space?” afterwards.")

            // ---- about the assistant itself
            q.matchesAny("who are you", "what are you", "your name", "are you ai", "what can you do", "help me", "how do you work", "can you help") ->
                AssistantMessage(
                    false,
                    buildString {
                        append("I'm the CleanSweep assistant — a small offline helper that lives inside this app.\n\n")
                        append("I can:\n")
                        append("• Report what is using your storage right now, in plain language\n")
                        append("• Explain every junk category and what happens if you delete it\n")
                        append("• Walk you through clearing the cache of any app (Android makes this a two-tap job)\n")
                        append("• Answer questions about privacy, duplicates, big files, downloads and more\n\n")
                        append("Everything I say is computed on this phone from your real numbers. I'm not a cloud chatbot — CleanSweep doesn't even have internet permission.")
                    },
                )

            // ---- privacy
            q.matchesAny("privacy", "private", "data safe", "send my data", "upload", "server", "internet", "offline", "spy", "track", "collect", "cloud", "account", "login") ->
                AssistantMessage(
                    false,
                    buildString {
                        append("Your data never leaves this phone.\n\n")
                        append("• CleanSweep has no INTERNET permission — the Android system would block any upload attempt\n")
                        append("• I run entirely on-device: your question is matched against built-in rules right here\n")
                        append("• No ads, no analytics, no accounts, and no file names are ever transmitted\n\n")
                        append("The only things stored are your app settings, protected folders and the last-clean summary, all in the app's private storage.")
                    },
                )

            // ---- the accessibility / automation question (v1.3 removed it)
            q.matchesAny("accessibility", "auto clean", "automatically clear", "automatic clean", "tap for me", "why manual", "auto-clean") ->
                AssistantMessage(
                    false,
                    buildString {
                        append("I don't use Accessibility any more — as of v1.3 CleanSweep has no Accessibility service at all.\n\n")
                        append("Android deliberately forbids one app from wiping another app's cache (that needs root), and the old automation asked for a very powerful permission just to tap buttons for you. We removed it, so CleanSweep now asks for the smallest possible set of permissions.\n\n")
                        append("Instead you get the guided flow: CleanSweep opens each selected app's storage page, you tap “Clear cache”, then tap “Next app” in CleanSweep. Two taps per app, and you always see exactly what is happening.")
                    },
                    actionLabel = "Open app cache",
                    action = AssistantAction.OPEN_APP_CACHE,
                )

            // ---- app cache
            q.matchesAny("cache", "caches", "clear cache", "app storage", "clear data") ||
                (q.looksLikeAppName() && q.matchesAny("clear", "clean", "cache", "storage", "size", "data")) ->
                appCacheReply(q, ctx)

            // ---- scan / results
            q.matchesAny("scan", "scanned", "what did you find", "what did the scan find", "found anything", "results", "report") ->
                scanReply(ctx)

            // ---- storage / free space
            q.matchesAny("free space", "free up", "free up space", "storage full", "space", "how full", "how much space", "memory full", "no space", "storage") ->
                storageReply(ctx)

            // ---- biggest things
            q.matchesAny("most space", "biggest", "largest", "hog", "eating", "takes most", "taking up", "top files", "what is using") ->
                biggestReply(ctx)

            // ---- safety
            q.matchesAny("safe", "dangerous", "risky", "delete", "what can i delete", "should i delete", "harm", "lose") ->
                safetyReply(ctx)

            // ---- duplicates
            q.matchesAny("duplicate", "duplicates", "same file", "identical", "copies") ->
                AssistantMessage(
                    false,
                    buildString {
                        append("Duplicates are files with identical content (same size and same hash) sitting in more than one place.\n\n")
                        append("• CleanSweep always keeps the newest copy, so the version you saved last survives\n")
                        append("• Files smaller than your minimum size (default 100 KB) are ignored — matching thousands of tiny files is not worth it\n")
                        append("• Only the extra copies are ticked; you can untick any single file before cleaning\n\n")
                        append("Duplicates are usually photos copied between folders (DCIM, WhatsApp, Download) — that is where the easy wins are.")
                    },
                    actionLabel = if (ctx.report != null) "View scan results" else null,
                    action = if (ctx.report != null) AssistantAction.OPEN_RESULTS else AssistantAction.NONE,
                )

            // ---- downloads / apk / thumbnails / empty folders / large files
            q.matchesAny("download", "downloads") ->
                AssistantMessage(
                    false,
                    "Files in your Download folder older than the age you set (default 30 days) can be swept — that's the “Old downloads” category, off by default because it's your folder, not mine.\n\nCheck it, untick anything you still need, and clean. Installers (.apk) sitting there are listed separately under “APK installer files”.",
                    actionLabel = if (ctx.report != null) "View scan results" else null,
                    action = if (ctx.report != null) AssistantAction.OPEN_RESULTS else AssistantAction.NONE,
                )

            q.matchesAny("apk", "installer", "install file") ->
                AssistantMessage(
                    false,
                    "APK installer files are the setup packages left behind after installing an app. Once the app is installed, the installer is dead weight.\n\nTurn on “Only flag APKs of installed apps” in Settings and CleanSweep will only ever suggest installers you no longer need, keeping the rest for apps you haven't installed yet.",
                    actionLabel = "Open settings",
                    action = AssistantAction.OPEN_SETTINGS,
                )

            q.matchesAny("thumbnail", "thumbs", ".thumbnails") ->
                AssistantMessage(
                    false,
                    "Thumbnail caches are the little preview images your gallery keeps for speed. Deleting them is completely safe — the gallery simply rebuilds them, which can take a few seconds the next time you open it.",
                )

            q.matchesAny("empty folder", "empty folders", "useless folder") ->
                AssistantMessage(
                    false,
                    "Empty folders are the most harmless thing CleanSweep can remove — nothing inside them by definition. They usually appear after you delete photos, uninstall an app, or after a previous clean.\n\nCleanSweep even catches the folders that only became empty because the junk inside them was just removed, and only deletes a folder if everything inside it was also going away.",
                )

            q.matchesAny("large file", "large files", "big file", "huge file") ->
                AssistantMessage(
                    false,
                    "“Large files” is a review-only category: it lists files above your threshold (default 200 MB) but never preselects them.\n\nThis is where the shock usually is — one old video, a game's data or a downloaded movie. Open the list, see what it is, and delete only what you recognise.",
                    actionLabel = if (ctx.report != null) "View scan results" else null,
                    action = if (ctx.report != null) AssistantAction.OPEN_RESULTS else AssistantAction.NONE,
                )

            // ---- system limits
            q.matchesAny("android/data", "android/obb", "why can't", "why cant", "not cleaning", "didn't delete", "failed", "cannot delete") ->
                AssistantMessage(
                    false,
                    buildString {
                        append("Since Android 11 the system locks Android/data and Android/obb for every app, including cleaners — that is why some space can look untouchable.\n\n")
                        append("If a file could not be deleted, CleanSweep marks it as failed and leaves everything else untouched. Common reasons:\n")
                        append("• The file lives inside Android/data or Android/obb\n")
                        append("• Another app is holding the file open (close it and rescan)\n")
                        append("• The file sits in a folder you protected in Settings — protected folders are never touched\n\n")
                        append("Nothing is ever deleted without your confirmation, so a failed delete can never take something else with it.")
                    },
                )

            // ---- ram / battery honesty
            q.matchesAny("ram", "boost", "speed up", "faster phone", "cool down", "heating", "battery", "lag") ->
                AssistantMessage(
                    false,
                    buildString {
                        append("Honest answer: cleaning junk does not speed up your phone, and RAM “boosters” are theatre — Android already manages memory, and killing apps makes things slower, not faster.\n\n")
                        append("What actually helps:\n")
                        append("• Freeing storage (this app) — a nearly full phone genuinely is slower\n")
                        append("• Clearing a huge app cache so it has headroom\n")
                        append("• Restarting the phone once in a while\n\n")
                        append("CleanSweep only claims the space it really frees.")
                    },
                )

            // ---- protected folders / settings
            q.matchesAny("protect", "exclude", "skip folder", "settings", "option", "hidden folder") ->
                AssistantMessage(
                    false,
                    "In Settings you can:\n\n• Add protected folders (type e.g. “WhatsApp” or “DCIM/Camera”) — they are never scanned or suggested\n• Scan hidden folders (off by default, slightly slower, finds more junk)\n• Tune duplicate minimum size, large-file threshold, old-download age and sound effects\n\nProtected folders are the safest way to say “never touch this”.",
                    actionLabel = "Open settings",
                    action = AssistantAction.OPEN_SETTINGS,
                )

            // ---- schedule / routine
            q.matchesAny("when should", "how often", "routine", "schedule", "regular", "weekly") ->
                AssistantMessage(
                    false,
                    "A comfortable routine:\n\n1. Once a week: scan and clean the suggestions\n2. Right after that: open App cache and do the two-tap pass for the apps you actually use (Instagram, YouTube, WhatsApp are usually the top three)\n3. Once a month: check the Large files list for forgotten videos\n\nThat is enough — more often than that and there is nothing new to find.",
                )

            // ---- last clean
            q.matchesAny("how much did we free", "already freed", "last clean", "previous clean", "freed so far") ->
                if (ctx.lastCleanBytes > 0L)
                    AssistantMessage(false, "Your last clean freed ${ctx.lastCleanBytes.formatBytes()} by removing ${ctx.lastCleanItems} items. Nice.\n\nRun a scan again whenever you want to see what has piled up since then.")
                else
                    AssistantMessage(false, "We haven't cleaned anything yet in this install. Tap “Scan & clean junk” and I'll tell you exactly what is worth removing.", actionLabel = "Scan now", action = AssistantAction.SCAN)

            // ---- file access
            q.matchesAny("file access", "all files", "permission", "allow storage", "why do you need") ->
                if (ctx.hasAllFilesAccess)
                    AssistantMessage(false, "File access is granted, so I can see and clean junk across your shared storage. If you ever revoke it in Android Settings, scanning switches off until you re-grant it.")
                else
                    AssistantMessage(false, "Android only lets an app scan your photos, downloads and other folders if you grant “All files access” — it is the switch that makes junk cleaning possible.\n\nGrant it and everything stays on this phone; there is no internet permission to worry about.", actionLabel = "Grant file access", action = AssistantAction.SCAN)

            q.matchesAny("usage access", "usage permission", "app cache permission") ->
                if (ctx.usageAccess)
                    AssistantMessage(false, "Usage access is granted, so I can show real per-app cache sizes. ${if (ctx.totalAppCacheBytes > 0L) "Right now ${ctx.totalAppCacheBytes.formatBytes()} of cache is sitting across ${ctx.appCacheCount} apps." else ""}")
                else
                    AssistantMessage(false, "To show how much cache each app holds, CleanSweep needs the “Usage access” permission. Open App cache and tap “Grant usage access” — Android will show you the list, find CleanSweep and switch it on.", actionLabel = "Open app cache", action = AssistantAction.OPEN_APP_CACHE)

            // ---- fallback
            else -> fallback(ctx)
        }
        return if (!verbose) shorten(reply) else reply
    }

    // --------------------------------------------------------------- replies

    private fun greeting(ctx: AssistantContext): AssistantMessage {
        val storage = ctx.storage
        val hint = when {
            storage == null -> "Storage numbers are still loading — give me a second and tap Scan."
            storage.free < 2L * 1024L * 1024L * 1024L ->
                "Heads up: you're down to ${storage.free.formatBytes()} free, so a clean is genuinely worth it today."
            ctx.report != null -> "I still have your last scan in memory — ask “what did the scan find?” any time."
            else -> "Tap “Scan & clean junk” and I'll explain everything that turns up."
        }
        return AssistantMessage(false, "Hello! I'm your offline cleaning assistant.\n\n$hint")
    }

    private fun scanReply(ctx: AssistantContext): AssistantMessage {
        val report = ctx.report
            ?: return AssistantMessage(
                false,
                "Nothing scanned yet — I have no results to talk about.\n\nTap below and I'll walk through what CleanSweep finds: leftover junk, thumbnails, duplicate files, left-behind installers, empty folders, old downloads and large files.",
                actionLabel = "Scan now",
                action = AssistantAction.SCAN,
            )
        val parts = mutableListOf<String>()
        parts += "The last scan checked ${report.filesScanned} files in ${report.dirsScanned} folders and found ${report.totalCount} junk items (${report.totalBytes.formatBytes()} in total)."
        val top = report.categories.filter { it.files.isNotEmpty() }.sortedByDescending { it.totalBytes }.take(3)
        if (top.isEmpty()) {
            parts += "Nothing worth deleting this time — your storage is clean. 🎉"
        } else {
            parts += "Biggest categories:"
            parts += top.joinToString("\n") { c ->
                "• ${c.kind.label}: ${c.files.size} items, ${c.totalBytes.formatBytes()}"
            }
        }
        parts += "Selected right now: ${report.selectedCount()} items, ${report.selectedBytes().formatBytes()}."
        return AssistantMessage(
            false,
            parts.joinToString("\n\n"),
            actionLabel = "View results",
            action = AssistantAction.OPEN_RESULTS,
        )
    }

    private fun storageReply(ctx: AssistantContext): AssistantMessage {
        val storage = ctx.storage
            ?: return AssistantMessage(false, "I can't read your storage numbers yet — pull up the home screen and I'll have them in a moment.")
        val usedPct = (storage.usedFraction * 100).toInt()
        val text = buildString {
            append("You have ${storage.free.formatBytes()} free out of ${storage.total.formatBytes()} — ${storage.used.formatBytes()} used ($usedPct%).\n\n")
            when {
                usedPct >= 90 -> append("That is genuinely tight; Android slows down when storage runs out. Cleaning junk and app caches is the fastest fix.")
                usedPct >= 75 -> append("That is on the full side but not critical. A clean now keeps you clear of the danger zone.")
                else -> append("That is a healthy amount of headroom — no emergency, but junk still accumulates silently.")
            }
            append("\n\nFastest wins, in order:\n")
            append("1. Scan & clean junk (leftover temp files, thumbnails, duplicates)\n")
            append("2. App cache of your top apps — usually the single biggest number\n")
            append("3. Large files review for forgotten videos or installers")
            if (ctx.totalAppCacheBytes > 0L) {
                append("\n\nRight now I can see ${ctx.totalAppCacheBytes.formatBytes()} of app cache. ")
                ctx.topAppCache?.let { top ->
                    append("The biggest is ${top.label} at ${top.cacheBytes.formatBytes()}.")
                }
            }
        }
        return AssistantMessage(false, text, actionLabel = "Scan now", action = AssistantAction.SCAN)
    }

    private fun biggestReply(ctx: AssistantContext): AssistantMessage {
        val report = ctx.report
        val lines = mutableListOf<String>()
        var action = AssistantAction.SCAN
        var actionLabel: String? = "Scan now"
        if (report != null) {
            val top = report.categories.filter { it.files.isNotEmpty() }.sortedByDescending { it.totalBytes }.take(4)
            if (top.isNotEmpty()) {
                lines += "From your last scan:"
                top.forEach { c ->
                    lines += "• ${c.kind.label} — ${c.totalBytes.formatBytes()} (${c.files.size} items)"
                    c.files.maxByOrNull { it.size }?.let { biggest ->
                        lines += "     biggest: ${biggest.path.substringAfterLast('/')} (${biggest.size.formatBytes()})"
                    }
                }
                action = AssistantAction.OPEN_RESULTS
                actionLabel = "View results"
            } else {
                lines += "The last scan found no junk at all."
            }
        } else {
            lines += "I haven't scanned yet, so I can only see the storage headline."
        }
        ctx.topAppCache?.let { top ->
            lines += "\nApp cache: ${ctx.totalAppCacheBytes.formatBytes()} across ${ctx.appCacheCount} apps — ${top.label} alone holds ${top.cacheBytes.formatBytes()}."
        }
        ctx.storage?.let {
            lines += "\nStorage: ${it.used.formatBytes()} used, ${it.free.formatBytes()} free."
        }
        return AssistantMessage(false, lines.joinToString("\n"), actionLabel = actionLabel, action = action)
    }

    private fun safetyReply(ctx: AssistantContext): AssistantMessage {
        val text = buildString {
            append("Short version: the presets are chosen to be safe.\n\n")
            append("✅ Always safe: leftover temp/log/partial files, thumbnail caches, empty folders, leftover installers of installed apps\n")
            append("⚠️ Look before you leap: duplicates (CleanSweep keeps the newest copy) and large files (never preselected)\n")
            append("🚫 Never touched: everything in folders you protected, and your photos, videos, documents and WhatsApp media unless you explicitly tick them\n\n")
            append("Nothing is deleted until you press Clean and confirm the summary.")
        }
        return AssistantMessage(
            false,
            text,
            actionLabel = if (ctx.report != null) "Review selection" else "Scan now",
            action = if (ctx.report != null) AssistantAction.OPEN_RESULTS else AssistantAction.SCAN,
        )
    }

    private fun appCacheReply(q: String, ctx: AssistantContext): AssistantMessage {
        val appName = KNOWN_APPS.firstOrNull { q.contains(it.first) }
        val totalLine = if (ctx.totalAppCacheBytes > 0L)
            "On your phone the app cache currently adds up to ${ctx.totalAppCacheBytes.formatBytes()} across ${ctx.appCacheCount} apps."
        else ""
        val text = buildString {
            if (appName != null) {
                append("To clear ${appName.second}'s cache the safe way:\n\n")
            } else {
                append("Here is the exact two-tap flow for app caches:\n\n")
            }
            append("1. Open App cache in CleanSweep\n")
            append("2. Tick the apps you want (the list is sorted by cache size — the biggest wins are on top)\n")
            append("3. Tap “Clean cache” — CleanSweep opens the first app's storage page\n")
            append("4. Tap “Clear cache” there, come back, and tap “Next app” to continue down the list\n\n")
            append("Android does not allow any cleaner app to wipe another app's cache silently — that needs root. This guided flow is the honest version of “auto clean”, and you stay in control of every tap.\n\n")
            append("For ${appName?.second ?: "most apps"}, cache is messages, thumbnails and previews. Your chats, photos and logins are not in the cache, so nothing personal is lost.")
            if (totalLine.isNotEmpty()) append("\n\n$totalLine")
        }
        return AssistantMessage(false, text, actionLabel = "Open app cache", action = AssistantAction.OPEN_APP_CACHE)
    }

    private fun fallback(ctx: AssistantContext): AssistantMessage {
        val ideas = suggestionChips(ctx).take(3)
        return AssistantMessage(
            false,
            buildString {
                append("I didn't quite catch that — I'm an offline assistant, so I match questions against a fixed set of cleaning topics.\n\n")
                append("Try rephrasing, or ask one of these:\n")
                ideas.forEach { append("• $it\n") }
                append("\nIf it's about storage, cleaning, caches, privacy or this app's settings, I can help.")
            },
            actionLabel = if (ctx.report != null) "View results" else "Scan now",
            action = if (ctx.report != null) AssistantAction.OPEN_RESULTS else AssistantAction.SCAN,
        )
    }

    // --------------------------------------------------------------- helpers

    private fun shorten(m: AssistantMessage): AssistantMessage {
        val core = m.text.substringBefore("\n\n").trim()
        return m.copy(text = core)
    }

    private fun normalise(s: String): String = s.lowercase()
        .replace('’', '\'')
        .replace(Regex("[?!.,;:()\\[\\]\"“”]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isGreeting(q: String): Boolean {
        val words = q.split(' ').filter { it.isNotBlank() }
        val greetings = setOf("hello", "hi", "hey", "namaste", "vanakkam", "hola", "hii", "helo")
        if (words.any { it in greetings }) return true
        return q.startsWith("good morning") || q.startsWith("good afternoon") || q.startsWith("good evening")
    }

    private fun String.matchesAny(vararg keys: String): Boolean = keys.any { contains(it) }

    private fun String.looksLikeAppName(): Boolean = KNOWN_APPS.any { contains(it.first) }

    private val KNOWN_APPS = listOf(
        "whatsapp" to "WhatsApp",
        "instagram" to "Instagram",
        "youtube" to "YouTube",
        "facebook" to "Facebook",
        "telegram" to "Telegram",
        "chrome" to "Chrome",
        "spotify" to "Spotify",
        "snapchat" to "Snapchat",
        "netflix" to "Netflix",
        "maps" to "Google Maps",
        "gmail" to "Gmail",
    )
}
