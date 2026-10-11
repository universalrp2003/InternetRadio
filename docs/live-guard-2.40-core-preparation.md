# Live Guard 2.40 core preparation (notification reader removed)

**Candidate, uncompiled at the time of writing:** `versionName 2.40`, `versionCode 45`.
This is a *prepared* change set. It is **not** uploaded, verified by Google, published,
or installed on a physical phone. The published release remains 2.39/code44
(`v2026.10.11-3`); its assets are immutable.

## Why

Google Play Protect blocked installation of the published Live Guard APK on the affected
phone ("App blocked to protect your device"). See
[`live-guard-play-protect-install-block.md`](live-guard-play-protect-install-block.md).
The owner decided (option 1) to prepare the main app **without notification/message
reading** rather than switch the feature off or hide it.

## What was removed from production (complete removal, no activation path)

Deleted from `cleaner/src/main/java/com/universalrp/cleansweep/`:

| File | Disposition |
| --- | --- |
| `notifications/MessageReaderService.kt` | archived byte-for-byte, not compiled |
| `notifications/LocalMessageTranslator.kt` | archived byte-for-byte, not compiled |
| `notifications/NotificationReaderPrefs.kt` | archived byte-for-byte, not compiled |
| `notifications/NotificationReadingPolicy.kt` | archived byte-for-byte **and** kept in TEST sources only |
| `ui/NotificationReaderScreen.kt` | archived byte-for-byte, not compiled |

Also removed:

- `AndroidManifest.xml`: the `MessageReaderService` `<service>`, its
  `BIND_NOTIFICATION_LISTENER_SERVICE` permission attribute and its
  `android.service.notification.NotificationListenerService` intent filter;
- `cleaner/build.gradle.kts`: `com.google.mlkit:translate:17.0.3` and
  `com.google.mlkit:language-id:17.0.6`;
- `MainViewModel.Screen.NOTIFICATION_READER` and the `AppRoot` route;
- the reader links in `SettingsScreen` and `FeatureSetupScreen`, the reader row and imports
  in `data/FeatureSetup.kt`, and `SetupGuides.NOTIFICATIONS`;
- `Announcer.KEY_MESSAGES`, `Announcer.Event.MESSAGES`, `Announcer.speakNotification`,
  `Announcer.cancelNotifications` and the private-message request plumbing
  (`forceOffline`, the reader `startGuard`, the message-only dedup path);
- the unused reader service-label string in `values/strings.xml` and
  `values-ta/strings.xml`.

Archive location (reference only, never a source set, never a flavor/module, never
distributed as a workaround): `docs/archived-notification-reader-2.39/`.

The old pure `NotificationReadingPolicy` (no Android imports) now lives in the **test**
source set so its 12 historical cases still run without shipping any reading capability.

## What deliberately stayed

- `POST_NOTIFICATIONS` and every ordinary Live Guard alert: battery low/full, charging
  card, thermal, storage, unplug, network-change, security, daily brief and reviewed
  setup reminders.
- General speech: manual "read aloud", voice previews, the two selected/previewed A/B
  answer voices (one voice per answer, each answer read once) and the installed-voice
  offline retry. Only the *private-message* speech path was removed.
- The verified in-app updater, its `FileProvider` and the mandatory Android installer
  confirmation; Play Protect cannot be forced or bypassed by this app.
- Package `com.universalrp.cleansweep`, stable signing certificate
  `9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55`, minSdk 26,
  target/compileSdk 36, all supported ABIs, and the updater's 128MiB safety cap plus the
  50MiB compatibility bound for existing 2.39 clients.
- Every other feature listed in `README.md` (health/estimated phone age, cleanup and
  system-app review, include-system default ON when unset with saved OFF preserved, cache
  refresh on open, compact Battery/Data/Phone widgets, observed unplug time/level,
  floating pill, permission-review date honesty, the six insights features, EN/TA 12-hour
  displays, TODAY-only news, cancellable AI budgets).

## Bilingual notice added

Both languages now state plainly that core Live Guard does not read other apps'
notifications or messages and declares no notification-listener/SMS/Contacts/call-log/
Accessibility/device-admin capability, while ordinary Live Guard alerts remain
(`SettingsScreen`, `FeatureSetupScreen`). The localization audit reports
**931 explicit EN/TA catalog keys** with no placeholder or duplicate problems.

## Scoped upgrade privacy cleanup

`data/CorePrivacyMigrationPolicy.kt` (pure rules), `data/CorePrivacyMigration.kt` (the
runner) and `data/CorePrivacyMigrationPolicyTest.kt` (6 prepared cases).

Runs on the IO dispatcher from `MainViewModel` initialization and retires **only**:

1. the SharedPreferences file `liveguard_notification_reader` (the reader's selected
   apps/senders);
2. the `voice_messages` key in `cleansweep_state`;
3. the old voice diagnostic trio (`voice_last_stage`, `voice_last_event`,
   `voice_last_attempt_ms`) **only** when `voice_last_event == "MESSAGES"`;
4. the obsolete `reader` id inside `skipped_reminders`.

An idempotent completion marker (`liveguard_core_privacy`) is written **only after every
step succeeded**; an incomplete cleanup is retried on a later start, and nothing private is
ever logged. It does not reset API keys, app language, A/B voice selections, voice or
monitor switches (including saved OFF choices), the data plan, charging/security histories,
genuine permission-review records or any other app's data. Previously downloaded public
translation model files may simply remain unused in private app storage; blind SDK
directory deletion is not performed and users are not told to clear all app data.

## Checks performed locally (source-only — **not** an APK build)

- `python3 tools/verify_cleaner_core_manifest.py` — source manifest, production tree,
  archive discipline, build-script dependencies;
- 8 unit tests: `python3 -m unittest discover -s tools/tests -p 'test_verify_cleaner_core_manifest.py' -v`;
- `python3 tools/verify_cleaner_localization.py` — 931 EN/TA keys, no duplicates or
  placeholder mismatches, no 24-hour owned clock;
- `python3 tools/verify_cleaner_next_update_source.py` — manifest/update/rating/widget/
  scope contracts, 33 suites / 301 prepared declarations, candidate 2.40/code45/API26;
- `git diff --check` — whitespace.

**Not** performed locally: Kotlin/Compose/Android compilation, JVM execution of the new
candidate, physical installation, Google clearance, publication. The sandbox has no JDK,
Android SDK or Gradle, and Maven/Google artifact hosts are unreachable from it.

## Required JVM gate (must actually run in CI)

`301 required cases` in **each** Cleaner debug/release variant, **33 suites**, plus
`:radio:testDebugUnitTest`, all-module debug/release APK/AAB checks, package/version/
API-26/non-debuggable/stable-signature/packaged-feature checks and the measured universal
APK size. Prepared declarations are not executed tests.

Commands:

```bash
python3 tools/verify_cleaner_core_manifest.py
python3 -m unittest discover -s tools/tests -p 'test_verify_cleaner_core_manifest.py' -v
python3 tools/verify_cleaner_localization.py
python3 tools/verify_cleaner_next_update_source.py
git diff --check
```

## Build → device test → release sequence

1. Apply/review these changes; confirm no hidden reader capability remains.
2. Publish the installation advisory (already in the docs and READMEs) without changing
   any old asset byte.
3. Build a signed candidate **without** publication (`[publish]` is deliberately gated).
4. Pass the JVM/APK/manifest/DEX/signing/API-26/version/size/companion gates.
5. Hand over the exact candidate APK: version, code, source commit, CI run, SHA-256.
   Each candidate run records every produced file with its size and SHA-256 in the run
   summary, in `.apk.sha256` sidecars inside the `cleansweep-apk` artifact, and in
   `docs/candidate-build-digests.md` on candidate branches.
6. The owner installs those exact bytes from the normal download route with Play Protect
   **on** and records the real outcome in
   `docs/live-guard-2.40-device-evidence.md`.
7. If blocked, stop and pursue Google's official route — no bypass, no disguise.
8. Only then publish the **same tested bytes** with a fresh tag. The publication step
   refuses to run for a CleanSweep version newer than 2.39 unless the evidence file exists,
   records the real Play Protect **and** installer outcome, and carries the machine-readable
   `TESTED-APK-SHA256` / `TESTED-RUN-ID` lines. If the rebuild happens to reproduce that
   digest, the freshly built APK is published; otherwise the step downloads the recorded
   run's `cleansweep-apk` artifact, verifies its digest and publishes **those** bytes —
   exact-artifact promotion, implemented in `.github/workflows/build-apk.yml`. A different
   digest with no recorded run id stops publication, so a rebuilt APK can never silently
   inherit an old device test.
9. Post-publication: verify stable/latest release, tag/source, asset names/sizes/digests
   and unchanged older assets, and update README/changelog/privacy/notes/device limits.
