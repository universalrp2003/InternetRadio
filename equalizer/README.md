# PulseEQ — 20-band sound equalizer for Android

A real equalizer with its own DSP: **20 bands**, presets for **music, movies, speech** and more,
**LED light-bar spectrum** that follows the actual audio, and a **foreground service** so it keeps
working after you leave the app. Audio processing is offline. Internet permission is used only for optional GitHub update checks.

**v1.0 highlights:** 20 peaking filters per channel + preamp + soft limiter • 17 presets •
live LED spectrum • background service with notification • system-wide effect attempt with honest
reporting • built-in 20-band player • cooperating-player support (Poweramp, VLC, Musicolet…).

## The 20 bands

31 Hz, 45, 63, 90, 125, 180, 250, 355, 500, 710, 1 k, 1.4 k, 2 k, 2.8 k, 4 k, 5.7 k, 8 k,
11.4 k, 16.1 k, 20 k — spaced a half octave apart, ±12 dB each.

Every band is a real RBJ peaking biquad, one per channel, running at the stream's own sample rate.
Bands set to 0 dB are skipped entirely, so "Flat" costs almost nothing. The preamp follows your
boosts automatically (the loudest band is pulled to 0 dB) and a soft limiter sits on the output,
which is why a +12 dB bass boost sounds loud rather than broken.

## Presets

Flat (reference) • Music • Movies • Speech • Podcast • Vocal • Bass boost • Treble boost • Rock •
Pop • Jazz • Classical • Dance/EDM • Hip-hop • Gaming • Night/quiet • Bollywood.

Each shows a sparkline of its curve; apply one and keep editing — moving any fader switches you to
“Custom”, which is remembered.

## Where the sound is equalized — read this

Android does not give normal apps a blanket filter over every app's audio. PulseEQ does what is
actually possible, and tells you exactly which of these applied:

| Path | What it covers | Guaranteed? |
|---|---|---|
| **PulseEQ player** (Player tab) | Local audio files you pick | ✅ Yes — samples pass through our own 20-band DSP |
| **Cooperating players** | Apps that announce their audio session (Poweramp, VLC, Musicolet, Vinyl…) | ✅ Yes, for those apps |
| **System-wide effect** | Every app, via one effect on the output mix | ⚠️ Best effort — Google deprecated it and many Android 11+ ROMs refuse it for normal apps; PulseEQ reports what your phone allowed |
| **Other apps in general** (YouTube, Spotify, games) | — | ❌ Not without root. PulseEQ will not pretend otherwise |

The apps that claim true system-wide EQ without root do it with internal audio capture
(MediaProjection) plus privileged session tricks — that path needs Shizuku/hidden APIs, adds
latency, and still cannot process apps that block capture (Spotify and Chrome famously do).
PulseEQ deliberately does not ship a half-working capture hack; instead the Player tab gives you a
guaranteed 20-band path, and the system effect is tried honestly on your ROM.

## LED lights and animation

The 20 columns are stacks of LED segments in the classic green → amber → red ladder, with a white
peak marker. They are not decoration:

- In the **player** they show the real per-band energy measured inside the DSP (20 band-pass
  analysers feed the bars), with a smooth fall-off and peak-hold.
- With **LED animation off** they show your EQ curve instead, so you can see the shape you drew.
- Idle bars dim and shimmer gently; brightness is adjustable in Settings, and bypassing greys them out.

## Running in the background

Turn on **Run in the background** in More → Background & coverage. PulseEQ then runs as a
foreground service with a persistent notification showing the active preset, with:

- `START_STICKY` so Android restarts it if memory is tight,
- an optional partial wake lock so the DSP survives screen-off and Doze,
- a **Stop** action in the notification that releases the audio effect immediately,
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` while playing, `specialUse` while it is just holding the
  effect — plus a shortcut to battery-optimisation settings, because "never closed" on modern
  Android ultimately depends on the battery settings the user grants.

## Privacy

- **No INTERNET permission** — nothing can be uploaded, by design.
- **No microphone permission** — the LEDs read the audio you are playing, not the room.
- No accounts, no ads, no analytics. Settings live in the app's private storage.

## Building

### GitHub Actions (no Android SDK needed)
Push to `main` (or *Actions → Build APK → Run workflow*) and download the `pulseeq-apk` artifact.

### Android Studio
Open the repo root and run the `equalizer` configuration.

### Command line
```bash
gradle wrapper
./gradlew :equalizer:assembleDebug
# APK at equalizer/build/outputs/apk/debug/equalizer-debug.apk
```

## Honest limitations

- System-wide EQ depends on your ROM (see the table above) — most Android 11+ phones block it for
  non-root apps; PulseEQ says so in the app instead of showing a fake "ACTIVE" badge.
- The device effect engine usually has fewer bands than 20 (often 5). PulseEQ interpolates your
  20-band curve onto whatever the phone offers, and shows the real band layout in Settings.
- The built-in player handles **local files only** (no streaming; Internet is used only for update checks).
- Volume/limiter changes are applied in software: if your phone's own "Dolby"/"Dirac" effect is also
  active, the two can fight; turning the built-in one off gives the cleanest result.
