# Archived 2.39 notification reader — historical reference ONLY

These files are the **exact 2.39 sources** of Live Guard's opt-in notification reader,
preserved byte-for-byte when the capability was removed from the product in 2.40.

They are kept for two reasons and nothing else:

1. **Honesty.** The 2.39 release genuinely shipped this feature, and the published 2.39
   notes and privacy policy describe it. Deleting the code from the repository would make
   that history unverifiable.
2. **A possible, separately-approved redesign.** If the owner ever wants a reader again, the
   starting point is here instead of guesswork.

## Hard rules

- **Not compiled.** Nothing under `docs/` is an Android source set. This archive is not
  added to Gradle, and `tools/verify_cleaner_core_manifest.py` fails the build if any build
  script starts referencing it.
- **Not a hidden feature.** There is no hidden flavor, product dimension, module, backup
  copy or "developer" switch that reactivates this code. There is no activation path in the
  shipped app.
- **Not distributed.** It must never be shipped as a workaround, an add-on APK or an
  "advanced" download.
- **Separate approval required.** Re-introducing any notification/message access needs a new
  owner decision plus policy and device review — Google's special/warning behaviour for
  sideloaded apps with sensitive capabilities is exactly what blocked installation of 2.39.

## Contents

| Archive path | Original 2.39 production path |
| --- | --- |
| `production-sources/notifications/MessageReaderService.kt` | `cleaner/src/main/java/com/universalrp/cleansweep/notifications/MessageReaderService.kt` |
| `production-sources/notifications/LocalMessageTranslator.kt` | `…/notifications/LocalMessageTranslator.kt` |
| `production-sources/notifications/NotificationReaderPrefs.kt` | `…/notifications/NotificationReaderPrefs.kt` |
| `production-sources/notifications/NotificationReadingPolicy.kt` | `…/notifications/NotificationReadingPolicy.kt` |
| `production-sources/ui/NotificationReaderScreen.kt` | `…/ui/NotificationReaderScreen.kt` |

The pure `NotificationReadingPolicy` (no Android imports) also lives in the **test** source
set as `cleaner/src/test/java/com/universalrp/cleansweep/notifications/NotificationReadingPolicy.kt`.
Its 12 historical unit tests stay in the suite, so the retired rules are still exercised
without shipping any reading capability. Test sources are compiled into test JVMs only —
they are never packaged into the APK, and the release gate checks both debug and release
DEX for these class names.

Removed at the same time, because they existed only for this feature:

- the `MessageReaderService` declaration, its `BIND_NOTIFICATION_LISTENER_SERVICE`
  permission and its `android.service.notification.NotificationListenerService` intent filter
  in `cleaner/src/main/AndroidManifest.xml`;
- the ML Kit `translate:17.0.3` and `language-id:17.0.6` dependencies (the only reader-only
  libraries in `cleaner/build.gradle.kts`);
- the reader route/screen links, the `SetupGuides.NOTIFICATIONS` script and the
  `notification_reader_label` resources in both languages.
