# Verified published Android APK digests

The current bridge releases were published in GitHub Actions run **38501139771** from source commit `85afc48c20edd86d19a81e65cdc307c29535ab1d`. The workflow verifies each built APK's package, version, minimum SDK, signing certificate and local SHA-256 before publication, stages the four releases as drafts, compares asset size and SHA-256 with GitHub's release API, then publishes and verifies the public release URLs/assets. The public API records below independently confirm that each release is not a draft and each asset URL is canonical.

## Current stable bridge releases — 14 October 2026

| App | Release | APK asset | Bytes | SHA-256 |
| --- | --- | --- | ---: | --- |
| Live Guard | [live-guard-v2.45](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.45) | `CleanSweep-v2.45.apk` | 13,652,452 | `426340c89dcdb7df2dd817070054141ccb9b0da0bac12dad9a82d308b300aaad` |
| Ramesh Radio | [ramesh-radio-v1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.10) | `Ramesh-Radio-v1.10.apk` | 14,494,105 | `4bde78049be431e0e6533a9c69e44f13c90c7c823f449b1e385968fafa2dbd06` |
| PulseEQ | [pulseeq-v1.4](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.4) | `PulseEQ-v1.4.apk` | 11,534,600 | `b1164d8899e75d757c1f6a2f88e14fed2a49654747812158ffd38e1bb11cd290` |
| RP Vocal Remover Studio | [rp-vocal-remover-studio-v0.1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.10) | `RP-Vocal-Remover-Studio-v0.1.10.apk` | 63,668,936 | `ccbedc4565c78541efd44cfdf831b465d261b8eaef505b8ba477326755a5c70e` |

These are bridge releases on the existing `InternetRadio` endpoint. The APK updaters query `RP-Android-Apps` first and then `InternetRadio`, selecting the newest valid release; the UI does not show repository-by-repository status. The owner confirmed on 14 October that all four bridge apps work and manual update checks complete without errors, so the one-to-two-month grace period is in progress. The new public repository still has no app releases, so retrieval from that source remains to be demonstrated. Do not archive or disable the old endpoint until the grace period and new-source checks are complete.

## Previous stable releases — 13 October 2026

These original release records and assets remain unchanged in `InternetRadio`:

| App | Release | APK asset | Bytes | SHA-256 |
| --- | --- | --- | ---: | --- |
| Live Guard | [live-guard-v2.44](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.44) | `CleanSweep-v2.44.apk` | 13,632,768 | `6ebe62debf4bdf8c680c9ce39c4af4f59e15de9d2f6a57750031092d9c0fd515` |
| Ramesh Radio | [ramesh-radio-v1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.9) | `Ramesh-Radio-v1.9.apk` | 14,491,793 | `322245c30e1c16e937cf0333e02f2f3270966b79105114ca012e58647c70948b` |
| PulseEQ | [pulseeq-v1.3](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.3) | `PulseEQ-v1.3.apk` | 11,533,640 | `fa5a96407ffd5439aee677fa1855c05ef5b9c5f9bee3199ea397707580ddc3cc` |
| RP Vocal Remover Studio | [rp-vocal-remover-studio-v0.1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.9) | `RP-Vocal-Remover-Studio-v0.1.9.apk` | 63,659,576 | `e49ca9fbf96aed66351d13ae63c3d2878280b6b09be42b050c00de9d97e6b208` |

The APK updater uses stable release tags and verifies asset digests. Installing a downloaded APK remains subject to Android's per-app **Install unknown apps** setting and the platform's confirmation screen; this workflow does not install silently or bypass Play Protect.
