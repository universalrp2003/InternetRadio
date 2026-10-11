# Live Guard 2.40 — retained-permission audit

**Purpose:** check that every permission the core app still declares is necessary for a
feature the owner asked to keep, is disclosed where the user meets it, and can be denied or
revoked without breaking the rest of the app.

**Important framing:** these permissions are **not** evidence that any of them caused the
Play Protect block, and their retention is **not** Google's approval. The block's exact
cause is unproven (see
[`live-guard-play-protect-install-block.md`](live-guard-play-protect-install-block.md)).
Removing the notification reader does not guarantee the block disappears.

Legend: **N** = necessity, **D** = disclosure, **R** = denial/revocation path.

## Removed in this change (no longer declared at all)

| Capability | Status |
| --- | --- |
| `BIND_NOTIFICATION_LISTENER_SERVICE` + `NotificationListenerService` intent filter | Removed with `MessageReaderService` |
| ML Kit translate / language-id libraries | Removed (reader-only) |
| Any SMS (`READ_SMS`/`RECEIVE_SMS`/`SEND_SMS`), Contacts, call-log, Accessibility, device-admin capability | **Never declared** in core; enforced absent by `tools/verify_cleaner_core_manifest.py` |

## Retained permissions

| Permission | Why it is needed (N) | Where the user sees it (D) | If denied / revoked (R) |
| --- | --- | --- | --- |
| `POST_NOTIFICATIONS` | Live Guard's **own** alerts: battery low/full, charging card, thermal, storage, daily brief, quiet reviewed-setup reminders | Runtime prompt before background/charging choices; feature-setup copy explains it and offers "Review Android notification permission" | App keeps working; background/charging notification choices stay unchanged and are reported as such; the Voice screen and Home still work |
| `MANAGE_EXTERNAL_STORAGE` (All files access) | User-selected junk/cache/duplicate **scan and delete** of files the user picks | File-cleanup feature copy + Android's own All-files-access screen; nothing is auto-selected or auto-deleted | Scans show what exists but deletion fails; the app reports the real outcome instead of "0 MB cleaned" |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) / `WRITE_EXTERNAL_STORAGE` (maxSdk 29) | Legacy path for API 26–29 where All-files access does not exist | Same feature copy; requested at runtime only when the cleanup flow needs it | Cleanup is refused honestly on those API levels rather than silently failing |
| `QUERY_ALL_PACKAGES` | Installed-app inventory for cleanup scope, cache sizes and the security review | Security/privacy copy: the review inspects **which other apps hold sensitive settings**; it does **not** read their notification or message content | Inventory lists fewer apps; no crash, no data loss |
| `PACKAGE_USAGE_STATS` (special access) | Per-app cache sizes and usage observations | Cache-screen copy explains the special access and opens the Android screen | Cache sizes stay unknown; the list still shows names; nothing is auto-selected or deleted |
| `SYSTEM_ALERT_WINDOW` | The explicitly enabled floating charging-watts pill | Pill section explains "Display over other apps" and the Stop/off switch | Pill simply does not draw; the app and its alerts are unaffected |
| `READ_PHONE_STATE` | Mobile & data screen: operator, SIM names, 5G/4G and signal readings | Mobile-screen copy; nothing is read outside that screen | Card shows unavailable values instead of guessing |
| `ACCESS_FINE_LOCATION` (maxSdk 32) / `NEARBY_WIFI_DEVICES` / `ACCESS_WIFI_STATE` | Wi-Fi details, "who is on my network", cell-tower ids on older APIs | Wi-Fi screen asks only while it is open; copy states local-only use | Wi-Fi/network details degrade to what Android allows; no other feature depends on location |
| `REQUEST_INSTALL_PACKAGES` | Verified in-app APK update hand-off | Update card explains Android's Install-unknown-apps screen for this app only, and that installer confirmation plus Play Protect/scanning decisions always remain Android's | Update stops at the Android permission screen; the app keeps working at the installed version. No silent install, no bypass, nothing forced |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Optional AI ask/report, TODAY-only news, update metadata, LM Arena card | AI/About screens say which request goes where and that keys are yours | App works fully offline except those explicit actions |
| `RECEIVE_BOOT_COMPLETED` | Restores the user's chosen monitoring, widgets and hourly health worker after a restart | Feature-setup/background copy; off by default until chosen | Chosen monitoring simply resumes when the app is next opened |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | The charging status card runs only while the cable is connected and stops itself when unplugged | Charging-card switch + ongoing notification with a Stop control | Card does not run; no permanent service is silently enabled |

## Things this audit does **not** claim

- It does not claim that these permissions are acceptable to Google, Play Protect or any
  store reviewer — that is their decision and is not something this repository can promise.
- It does not claim any of them caused, or did not cause, the installation block.
- It does not claim that a user's denial path is identical on every OEM ROM; stock Android
  paths are described, and HyperOS/MIUI-style restrictions remain a real limit that must be
  documented rather than assumed.

## Cleanup note

Retiring the removed reader's leftovers is done by `CorePrivacyMigration` and covers only:
the reader preferences file, the `voice_messages` key, the reader's stale voice
diagnostics, and the obsolete `reader` skipped-reminder id. Nothing else — no keys, no
language, no A/B voice selections, no switches, no histories, no other app's data.
