# Verified published Android APK digests

The four assets below were published in GitHub Actions run **38380663566** from source commit `e4742129f338ee2ae930945cf619a356abac1583`. The release workflow compared each APK's local byte count and SHA-256 with GitHub's release-asset API both before and after publishing. Each release is stable (not a draft or prerelease), and each listed asset is uploaded.

| App | Release | APK asset | Bytes | SHA-256 |
| --- | --- | --- | ---: | --- |
| Live Guard | [live-guard-v2.44](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.44) | `CleanSweep-v2.44.apk` | 13,632,768 | `6ebe62debf4bdf8c680c9ce39c4af4f59e15de9d2f6a57750031092d9c0fd515` |
| Ramesh Radio | [ramesh-radio-v1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.9) | `Ramesh-Radio-v1.9.apk` | 14,491,793 | `322245c30e1c16e937cf0333e02f2f3270966b79105114ca012e58647c70948b` |
| PulseEQ | [pulseeq-v1.3](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.3) | `PulseEQ-v1.3.apk` | 11,533,640 | `fa5a96407ffd5439aee677fa1855c05ef5b9c5f9bee3199ea397707580ddc3cc` |
| RP Vocal Remover Studio | [rp-vocal-remover-studio-v0.1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.9) | `RP-Vocal-Remover-Studio-v0.1.9.apk` | 63,659,576 | `e49ca9fbf96aed66351d13ae63c3d2878280b6b09be42b050c00de9d97e6b208` |

The APK updater uses these stable release tags and verifies asset digests. Installing a downloaded APK remains subject to Android's per-app “Install unknown apps” setting and the platform's confirmation screen; this workflow does not install silently or bypass Play Protect.
