# Live Guard — Play Protect installation block (accurate advisory)

**Status: unresolved on the affected phone.** This advisory applies to the published
Live Guard / CleanSweep **2.39 (versionCode 44)** APK and, until it is tested, to the
unpublished 2.40 candidate.

## What was actually observed

A physical-phone installation attempt of the published Live Guard APK showed Google Play
Protect:

> **Google Play Protect — "App blocked to protect your device" — App: Live Guard**
> "This app can request access to sensitive data. This can increase the risk of identity
> theft or financial fraud."

What that screenshot **does** prove:

- an installation attempt was blocked on that device at that moment;
- the block was attributed to Live Guard.

What it does **not** prove:

- the exact APK hash, version or download source that was blocked (the screenshot shows no
  digest), so **the blocked artifact is not confirmed**;
- that Google's internal verdict is final, or that the app is malware;
- that every device will block it, or that no device ever will.

So: this is a real block that stops installation for at least one owner device. It is
**not** proven to be a false positive, and it is **not** proven to be a permanent
reputation verdict either. Both claims would be guesses.

## The leading suspect (a suspect, not a proven cause)

2.38 and 2.39 added an opt-in notification reader:

- `MessageReaderService` (a `NotificationListenerService`),
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`,
- the `android.service.notification.NotificationListenerService` intent filter.

Google documents a warning specifically for **internet-sideloaded** apps that request
sensitive notification/SMS/accessibility-style capabilities:
<https://developers.google.com/android/play-protect/warning-dev-guidance>, alongside the
enhanced-fraud-protection direction described at
<https://blog.google/intl/en-in/products/launching-enhanced-fraud-protection-pilot-in-india/>.

The important technical point is that **a capability is declared in the APK, not at
runtime**. Turning the reader OFF at runtime, keeping it local-only, never uploading
anything and never asking for Contacts does **not** remove
`BIND_NOTIFICATION_LISTENER_SERVICE` and its intent filter from the package. Anything
that scans the declared capabilities can still see a notification-listener service.

Google has not told us that this capability caused this block. We have no case id, no
verdict text and no hash-linked report from Google. Treat the reader as the leading
suspect and the removal as a necessary, *not sufficient*, step.

## What 2.40 changes

Core Live Guard 2.40 (versionCode 45, **candidate, not published**) removes the capability
completely rather than switching it off:

- the reader service, its permission attribute, its intent filter and its screen are gone;
- the reader-only ML Kit translation/language-ID dependencies are gone;
- the reader route, links, guides and service-label resources are gone;
- the app declares **no** notification-listener, SMS, Contacts, call-log, Accessibility or
  device-admin capability (checked against the source manifest, the merged manifest and
  both debug/release APK DEX by `tools/verify_cleaner_core_manifest.py`);
- private leftovers from 2.39 are retired once, on a background thread, by
  `CorePrivacyMigration` (see `docs/live-guard-2.40-permission-audit.md`).

The historical 2.39 reader sources are archived under
`docs/archived-notification-reader-2.39/` as reference only and are not compiled.

**Removing the reader is not a guaranteed fix.** The block may persist for other reasons
that we cannot see from here (signing/reputation signals, other permissions, the general
"internet-sideloaded app that requests sensitive access" warning pattern, or a device-side
policy). Anyone who says otherwise is guessing.

## What nobody should do

Do **not**, for this app or any other:

- disable, weaken or bypass Play Protect;
- hide or misrepresent the installation source;
- rename the package or change the signing identity to dodge a check;
- tell users to ignore the warning without telling them what the warning means;
- publish a rebuilt APK and claim an earlier device test covered it.

Preserving the existing package and the stable signing certificate
(`9e30f4ea…5d55`) is a deliberate choice: changing either one would be a concealment tactic
and would also break updates for people who already installed the app.

## What the owner should do next

1. Install the **exact candidate 2.40 APK** (same bytes, recorded SHA-256) from the normal
   download route with Play Protect **on**.
2. Record what actually happens: Play Protect result, installer result, error text and
   whether the app installs and opens.
3. If it installs: continue the normal verification, then publish those exact bytes (the
   publication gate in `.github/workflows/build-apk.yml` refuses a different digest).
4. If it is still blocked: **stop publication.** Do a policy/privacy review of what is
   still declared, and use Google's official channels — Play Protect feedback /
   reconsideration through the Play Console and the support routes described in Google's
   developer guidance. Keep the evidence and the exact APK hash with the request.
5. Keep the advisory in this file, `README.md` and `cleaner/README.md` current. Old
   release assets (2.38, 2.39 and earlier) are never overwritten and their historical CI
   results are never erased — they simply stop being recommended while a block is open.

## Honest limits of every check in this repository

- CI proves that the code built, the tests passed, the package/version/API-26/signature
  gates match and what bytes were produced.
- GitHub publication is not Google Play review, not F-Droid review, not Play Protect
  approval and not all-device certification.
- Official Google documents describe warning behaviour for sideloaded apps; they do not
  contain a per-APK verdict for this app.
