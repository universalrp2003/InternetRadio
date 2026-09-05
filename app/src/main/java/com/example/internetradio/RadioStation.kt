package com.example.internetradio

data class RadioStation(
    val name: String,
    val genre: String,
    val streamUrl: String
)

/**
 * Starter list of free public streams (MP3/AAC). Add your own stations here -
 * any direct stream URL works, including .m3u8 (HLS) links.
 */
val defaultStations = listOf(
    RadioStation(
        "SomaFM Groove Salad",
        "Ambient / Chill beats",
        "https://ice1.somafm.com/groovesalad-128-mp3"
    ),
    RadioStation(
        "SomaFM Drone Zone",
        "Deep ambient",
        "https://ice1.somafm.com/dronezone-128-mp3"
    ),
    RadioStation(
        "SomaFM Secret Agent",
        "Lounge / Spy jazz",
        "https://ice1.somafm.com/secretagent-128-mp3"
    ),
    RadioStation(
        "SomaFM Lush",
        "Vocal chillout",
        "https://ice1.somafm.com/lush-128-mp3"
    ),
    RadioStation(
        "SomaFM DEF CON Radio",
        "Hacker / Electronic",
        "https://ice1.somafm.com/defcon-128-mp3"
    ),
    RadioStation(
        "SomaFM Sonic Universe",
        "Modern jazz",
        "https://ice1.somafm.com/sonicuniverse-128-mp3"
    ),
    RadioStation(
        "Radio Paradise",
        "Eclectic mix",
        "https://stream.radioparadise.com/aac-320"
    ),
    RadioStation(
        "Nightride FM",
        "Synthwave",
        "https://stream.nightride.fm/nightride.mp3"
    )
)
