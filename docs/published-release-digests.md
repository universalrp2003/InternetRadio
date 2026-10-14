# Verified published Android APK digests

The latest stable batch was built, verified and published by GitHub Actions run **38521705254**, from source commit `838122db61ef7c177977f04595af0f4a1452bb8a`. The workflow checked the APK package, version, minimum SDK, signing certificate, local SHA-256 and release-asset metadata before promoting the releases. It published the same four APKs to `universalrp2003/RP-Android-Apps` and `universalrp2003/InternetRadio`.

## Current stable releases — 14 October 2026

| App | Version / code | RP-Android-Apps release | InternetRadio fallback | APK asset | Bytes | SHA-256 |
| --- | ---: | --- | --- | --- | ---: | --- |
| Live Guard | 2.46 / 51 | [live-guard-v2.46](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/live-guard-v2.46) | [live-guard-v2.46](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.46) | `CleanSweep-v2.46.apk` | 13,655,296 | `92be908e773d0214c5c8dc0d9bf4254f05541fc791d6494316a0a07cfb6ea80a` |
| Ramesh Radio | 1.11 / 11 | [ramesh-radio-v1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/ramesh-radio-v1.11) | [ramesh-radio-v1.11](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.11) | `Ramesh-Radio-v1.11.apk` | 14,494,129 | `e27c408ac7e199fc480b15e7472e4c3bcbf121f79bb6dbddb5d875c0e48594fc` |
| PulseEQ | 1.5 / 6 | [pulseeq-v1.5](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/pulseeq-v1.5) | [pulseeq-v1.5](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.5) | `PulseEQ-v1.5.apk` | 11,534,600 | `1860ad5282e9d2809143e6923678f467b13f55e9bee9c02b1612b7ab0622be5f` |
| RP Vocal Remover Studio | 0.1.11 / 12 | [rp-vocal-remover-studio-v0.1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/rp-vocal-remover-studio-v0.1.11) | [rp-vocal-remover-studio-v0.1.11](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.11) | `RP-Vocal-Remover-Studio-v0.1.11.apk` | 63,671,220 | `aea56a8676ce73339eb8eae4e29b0760676ed8300f47970490223061f876f609` |

The post-publication GitHub API check confirmed that all eight release records are public, stable (not drafts or prereleases), and have canonical release-page and APK-download URLs. For each app, the destination release is newer than the bridge version, and its asset name, byte size and SHA-256 digest match the `InternetRadio` copy exactly. The APK updaters check `RP-Android-Apps` first and `InternetRadio` second; when an otherwise-valid version is present in both, the new repository has priority. Owner-provided phone screenshots dated 14 October show update-available notices for all four bridge versions; later screenshots show Live Guard reporting 2.46 installed, Ramesh Radio at Play Protect's safe-to-install screen, and PulseEQ at Android's update confirmation. These confirm device-side discovery and updater hand-off, but do not identify the supplying repository or bind the device state to the published SHA-256 digests; final installation is not shown for every app. Keep hash-linked exact-build confirmation distinct from these screenshots.

**FTTH download observation — 14 October:** the owner reports that direct Brave downloads from both repositories over BSNL FTTH were very slow. Screenshots show `CleanSweep-v2.46.apk` (13.66 MB) at about 0.33 MB and 0.46 MB downloaded, with Brave estimating seven minutes remaining. This demonstrated the slowdown was not limited to the in-app updater. The owner later reported that restarting the BSNL router resolved it. That points to a transient router/session/network-path issue, but the exact cause was not established. The 31.8 Mbps speed test used a different endpoint and does not establish throughput to GitHub.

Build-only runs [38514595315](https://github.com/universalrp2003/InternetRadio/actions/runs/38514595315) and [38523346072](https://github.com/universalrp2003/InternetRadio/actions/runs/38523346072) produced candidate APK hashes that differ from the published bytes. [`candidate-build-digests.md`](candidate-build-digests.md) records the latest non-public run; older candidate records remain in Git history. Do not substitute candidate hashes for the published digests above.

## Previous stable bridge releases — 14 October 2026

These versions were published to `InternetRadio` so existing installations could update to a bridge client that knows both repositories. Their release records remain available unchanged:

| App | Release | APK asset | Bytes | SHA-256 |
| --- | --- | --- | ---: | --- |
| Live Guard | [live-guard-v2.45](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.45) | `CleanSweep-v2.45.apk` | 13,652,452 | `426340c89dcdb7df2dd817070054141ccb9b0da0bac12dad9a82d308b300aaad` |
| Ramesh Radio | [ramesh-radio-v1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.10) | `Ramesh-Radio-v1.10.apk` | 14,494,105 | `4bde78049be431e0e6533a9c69e44f13c90c7c823f449b1e385968fafa2dbd06` |
| PulseEQ | [pulseeq-v1.4](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.4) | `PulseEQ-v1.4.apk` | 11,534,600 | `b1164d8899e75d757c1f6a2f88e14fed2a49654747812158ffd38e1bb11cd290` |
| RP Vocal Remover Studio | [rp-vocal-remover-studio-v0.1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.10) | `RP-Vocal-Remover-Studio-v0.1.10.apk` | 63,668,936 | `ccbedc4565c78541efd44cfdf831b465d261b8eaef505b8ba477326755a5c70e` |

## Previous stable releases — 13 October 2026

These historical release pages, tags and APK assets remain on `InternetRadio`:

| App | Release | APK asset | Bytes | SHA-256 |
| --- | --- | --- | ---: | --- |
| Live Guard | [live-guard-v2.44](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.44) | `CleanSweep-v2.44.apk` | 13,632,768 | `6ebe62debf4bdf8c680c9ce39c4af4f59e15de9d2f6a57750031092d9c0fd515` |
| Ramesh Radio | [ramesh-radio-v1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.9) | `Ramesh-Radio-v1.9.apk` | 14,491,793 | `322245c30e1c16e937cf0333e02f2f3270966b79105114ca012e58647c70948b` |
| PulseEQ | [pulseeq-v1.3](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.3) | `PulseEQ-v1.3.apk` | 11,533,640 | `fa5a96407ffd5439aee677fa1855c05ef5b9c5f9bee3199ea397707580ddc3cc` |
| RP Vocal Remover Studio | [rp-vocal-remover-studio-v0.1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.9) | `RP-Vocal-Remover-Studio-v0.1.9.apk` | 63,659,576 | `e49ca9fbf96aed66351d13ae63c3d2878280b6b09be42b050c00de9d97e6b208` |

For every release, installation remains under Android's per-app **Install unknown apps** permission and installer confirmation. The updater does not silently install or bypass Play Protect.
