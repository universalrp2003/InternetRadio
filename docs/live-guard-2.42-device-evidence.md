# Live Guard 2.42 — device evidence (two phones, one build)

**Recorded 12 October 2026 from the owner's own phone and words.** This is one build
(`CleanSweep-v2.42.apk`, code 47) installed in place over 2.41 (46) on **both** of the owner's
phones with **Play Protect ON**. It is not Google's approval, not app-store approval and not
all-device certification. It is also not a claim about any other build.

## The tested bytes

```
TESTED-APK-SHA256: 79400ed9e61898f6e6f3e85c75dc3ee694c580ec58709a3d687fd6ef2af2f9e0
TESTED-RUN-ID: 38222530199
```

- Published asset: `CleanSweep-v2.42.apk`, **13,574,808 bytes**, release `v2026.10.12-2`,
  published 2026-10-12T08:20:38Z, built in CI run **38222530199** from source `f3ce61e`.
- The digests above are the GitHub release asset's own digest as reported by the releases API —
  the same value the in-app updater verifies before it offers an install.
- The owner's phone held the matching sidecar `CleanSweep-v2.42.apk.sha256` (121 bytes) next to
  the APK in Downloads, reading
  `79400ed9e61898f6e6f3e85c75dc3ee694c580ec58709a3d687fd6ef2af2f9e0  cleaner/build/outputs/apk/release/CleanSweep-v2.42.apk`.
  The installed bytes are therefore the published bytes; nothing in between was modified.
- Companion apps were not part of this install: Ramesh Radio 1.8 and PulseEQ 1.2 shipped as their
  previously verified bytes (`b1514b0c…`, `3e2f5baf…`).

## Phones

The owner installed the same APK on **both** phones and answered "Both phone" when asked which
device the screenshots came from:

1. **Xiaomi Redmi 13 5G** — HyperOS 3.0.303.0, Android 16, build `BP2A.250605.031.A3`. The same
   phone as the 2.40 and 2.41 records.
2. **OPPO A3s** — Android 8.1 / ColorOS. The phone behind the item 1 charging regression.

## Observed Play Protect and installer outcome

Six screenshots were delivered in chat on 12 October 2026 at 14:04–14:06 phone time. **The image
files did not persist into this repository's workspace** (the same attachment-transport failure
that lost the original work-list PDF), so they are quoted verbatim here rather than committed;
the owner still holds the originals on the phone. Their filenames, source packages and content:

| # | File (as delivered) | Source package | Content, verbatim where quoted |
|---|---|---|---|
| 1 | `Screenshot_2026-10-12-14-04-02-115_com.cxinventor.file.explorer.jpg` | Cx File Explorer | Downloads → `cleansweep-apk (1)` → `release`: `CleanSweep-v2.42.apk.sha256` (121 B) and `CleanSweep-v2.42.apk` (13.57 MB) |
| 2 | `Screenshot_2026-10-12-14-04-07-614_com.cxinventor.file.explorer.jpg` | Cx File Explorer | Install prompt: Name **Live Guard**, Size 13.57 MB, Modified 12 Oct 2026 8:20 am, App info `com.universalrp.cleansweep 2.42 (47)`, **Installed version 2.41 (46)**, buttons UPDATE / CANCEL |
| 3 | `Screenshot_2026-10-12-14-04-21-624_com.google.android.packageinstaller.jpg` | Android package installer | "Live Guard — Installing…" with progress bar; the Live Guard status-bar reading is visible top-left during the install |
| 4 | `Screenshot_2026-10-12-14-04-27-390_com.android.vending.jpg` | Google Play Protect | "**App scan recommended** — Play Protect hasn't seen this app before. To protect your device and data, send this app to Google for a security scan." with **Scan app** — i.e. Play Protect was ON and the owner let it scan |
| 5 | `Screenshot_2026-10-12-14-04-52-522_com.android.vending.jpg` | Google Play Protect | After the scan: "**This app looks safe** — Live Guard — **You can continue to install it**", buttons Install / Don't install |
| 6 | `Screenshot_2026-10-12-14-06-23-652_com.universalrp.cleansweep.jpg` | Live Guard | About Live Guard shows **CleanSweep v2.42**; the "App updates / Verified in-app download…" card is present |

Installation outcome, in order: the installer offered **UPDATE** over 2.41 (46) → the owner tapped
through → Play Protect asked to scan → the owner scanned → Play Protect said **"This app looks
safe — You can continue to install it"** → the owner installed → the package installer completed
→ About shows **CleanSweep v2.42**. Settings, reviewed choices and saved language carried over
(the in-place upgrade path, not an uninstall).

**Play Protect was never disabled, bypassed or worked around.** Nothing here changes the package
name or the signing key, and the 2.39 install-block advisory
([`live-guard-play-protect-install-block.md`](../live-guard-play-protect-install-block.md)) stands
as written for the older builds.

## What this evidence does and does not cover

Covers: that these exact bytes install in place over 2.41 on both phones with Play Protect ON,
that Play Protect's real wording was "App scan recommended" then "This app looks safe — You can
continue to install it", and that the installed app reports 2.42 (47).

Does **not** cover: any behaviour claim beyond the install. In particular the OPPO A3s charging
reading was checked and is recorded below as **not fixed by 2.42**.

## OPPO A3s charging reading — result: still 0.00 W in 2.42, fixed in 2.43

The owner checked the A3s while charging after installing 2.42 and reported the card **still
shows 0.00 W**. That result is diagnostic: 2.42's fallback reads the battery broadcast's
`current_now` extra when `BATTERY_PROPERTY_CURRENT_NOW` reports 0 or "unsupported", and it
rescues phones whose broadcast carries a real non-zero current. On this kernel **both** paths
answer zero, so no current exists to rescue — and 2.42's mistake was to treat a broadcast zero
as a *measured trickle*, printing `0.00 W` again.

A zero from both sources is indistinguishable from an unsupported sensor, and the standing rule
is that an unsupported sensor must never be rendered as a measured `0.00 W`. **2.43 / code 48**
therefore makes a zero never a measurement: with no non-zero value from either path the reading
is reported as *not reported*, on the A3s and everywhere else. No app can show real watts on a
kernel that reports no current through either Android path; what it can do is stop pretending,
and that is what 2.43 does. The A3s' own charge history (real 4.6–4.8 W sessions) remains
evidence that some path reported current on some builds; it is not evidence about this one.

See [`live-guard-2.42-scope.md`](../live-guard-2.42-scope.md) item 1 and
[`live-guard-2.42-device-test-plan.md`](../live-guard-2.42-device-test-plan.md) for the remaining
open checks (per-ROM permission routing, font-scale layout, the automatic voice pair).
