package com.universalrp.updates

/**
 * One list, six apps.
 *
 * Every app's About screen renders this list, so adding or bumping an app happens in
 * exactly one place. Before this file each app hard-coded its own list — and four of them
 * still pointed at the old `InternetRadio` repository and left out Oor Arivippu and
 * Secret Message entirely.
 *
 * `version` is the current published tag suffix in
 * `universalrp2003/RP-Android-Apps`; `releaseBase` points at the new repository, with the
 * old one kept only as the updater fallback.
 */
data class SisterApp(
    /** Stable id, used to mark the app the user is currently in. */
    val id: String,
    /** Name shown in English. */
    val name: String,
    /** Tamil name, where the app has one. */
    val tamilName: String,
    /** Tag prefix, e.g. `live-guard`. */
    val tagPrefix: String,
    /** Current version shown to the user. */
    val version: String,
    /** Android applicationId, used to open the app directly when it is installed. */
    val packageName: String,
    /** The About-screen label (kept close to the old wording so tests and translations match). */
    val aboutLabel: String,
)

object SisterApps {

    private const val RELEASE_BASE =
        "https://github.com/universalrp2003/RP-Android-Apps/releases/tag"

    /** The old repository stays a working fallback until the November migration review. */
    private const val FALLBACK_BASE =
        "https://github.com/universalrp2003/InternetRadio/releases/tag"

    val all: List<SisterApp> = listOf(
        SisterApp(
            id = "liveguard",
            name = "Live Guard",
            tamilName = "லைவ் கார்ட்",
            tagPrefix = "live-guard",
            version = "2.46",
            packageName = "com.universalrp.cleansweep",
            aboutLabel = "Live Guard — GitHub release",
        ),
        SisterApp(
            id = "radio",
            name = "Ramesh Radio",
            tamilName = "ரமேஷ் ரேடியோ",
            tagPrefix = "ramesh-radio",
            version = "1.11",
            packageName = "com.universalrp.tamilnadufm",
            aboutLabel = "Ramesh Radio — GitHub release",
        ),
        SisterApp(
            id = "pulseeq",
            name = "PulseEQ",
            tamilName = "பல்ஸ்இக்யூ",
            tagPrefix = "pulseeq",
            version = "1.5",
            packageName = "com.universalrp.pulseeq",
            aboutLabel = "PulseEQ — GitHub release",
        ),
        SisterApp(
            id = "vocalremover",
            name = "RP Vocal Remover Studio",
            tamilName = "வோக்கல் ரிமூவர் ஸ்டுடியோ",
            tagPrefix = "rp-vocal-remover-studio",
            version = "0.1.11",
            packageName = "com.universalrp.vocalremover",
            aboutLabel = "RP VOCAL REMOVER STUDIO — GitHub release",
        ),
        SisterApp(
            id = "oorarivippu",
            name = "Oor Arivippu",
            tamilName = "ஊர் அறிவிப்பு",
            tagPrefix = "oor-arivippu",
            version = "1.1.0",
            packageName = "com.oorarivippu.voiceannouncer",
            aboutLabel = "Oor Arivippu — GitHub release",
        ),
        SisterApp(
            id = "secretmessage",
            name = "Secret Message",
            tamilName = "ரகசிய செய்தி",
            tagPrefix = "secret-message",
            version = "1.1.0",
            packageName = "com.rpideas.secretmessage",
            aboutLabel = "Secret Message — GitHub release",
        ),
    )

    /** `label to url`, in the order the About screens have always shown them. */
    fun releaseLinks(): List<Pair<String, String>> =
        all.map { it.aboutLabel to releaseUrl(it) }

    /** Same list, without the app the user is already looking at. */
    fun releaseLinks(excludeId: String?): List<Pair<String, String>> =
        all.filter { it.id != excludeId }.map { it.aboutLabel to releaseUrl(it) }

    fun releaseUrl(app: SisterApp): String = "$RELEASE_BASE/${app.tagPrefix}-v${app.version}"

    /** The verified fallback used by the updater while the migration grace period runs. */
    fun fallbackUrl(app: SisterApp): String = "$FALLBACK_BASE/${app.tagPrefix}-v${app.version}"

    fun byId(id: String): SisterApp? = all.firstOrNull { it.id == id }
}
