# Live Guard 2.42 — device test plan (what still needs a real phone)

2.42/code47 was published **without** a recorded device install, which the owner's 12 October
2026 decision permits because the release adds no dangerous permission (see
[`live-guard-2.42-scope.md`](live-guard-2.42-scope.md) item 9). That means every item below is
**open**: source-level tests and CI gates passed, and no claim is made that any of this was
observed on hardware. Nothing here is Google approval or all-device certification.

Record results by editing this file, one line per check, with the phone, the Android/ROM build
and what was actually seen. Where a device result is quoted anywhere else, it must say
**"one phone, one build"**.

## 1. OPPO A3s charging reading — tested twice, honest answer recorded (highest priority)

Phone: OPPO A3s, Android 8.1 / ColorOS.

**Result of the 2.42 test (12 October 2026): the card still showed `0.00 W`.** The kernel answers
zero through *both* the current property and the battery broadcast, so no current exists to
rescue; see [`live-guard-2.42-device-evidence.md`](live-guard-2.42-device-evidence.md). 2.43
therefore reports *not reported* instead of a measured zero.

Checks for **2.43** on the A3s, while charging at a low-ish level and again mid-charge:

- [ ] The battery card shows **no watt figure and no `0.00 W`**; the current reads *not reported*.
- [ ] No provenance line claiming a broadcast reading appears (a zero is not a reading).
- [ ] Percentage, voltage, temperature and the charge history are unchanged and still sensible.
- [ ] With the charger unplugged, still no charging watts at all.
- [ ] **One screenshot** sent back, as the owner asked. If a *future* ColorOS/Android build of this
      phone ever reports a non-zero current in either path, the card will show real watts again
      with the provenance line — that is the fallback working, not a regression.

On any phone whose counter works (the Redmi 13 5G):

- [ ] Same phone, same charger: watts, amps and the sign note read exactly as they did on 2.41
      and 2.42 (2.43 only removes the impossible exactly-zero reading).
- [ ] The provenance line does **not** appear there (the Redmi's current property works, so the
      source is the property, not the broadcast).
- [ ] The 44 °C heat warning, the screen-on/off note and the health screen all still agree with
      the card — one policy decides the watts, so they cannot diverge.

## 2. Automatic Voice A / Voice B

- [ ] With no voice ever chosen, an assistant answer is **spoken** and no error message appears.
- [ ] Voice & daily watch shows the two automatically chosen voices in the A and B slots.
- [ ] Consecutive answers alternate between the two, and each answer is read **once**.
- [ ] Manual "Read aloud" on one answer uses that answer's assigned voice.
- [ ] Preview and "Use as Voice A/B" still work, and a manual save still requires a completed
      preview and two distinct voices.
- [ ] Tamil and English keep separate choices; switching the answer language switches the pair.
- [ ] On a phone with only **one** usable voice for a language: both slots show it, the card
      says alternation is not possible yet, and answers are still spoken.
- [ ] With no usable voice for the language: nothing is spoken, **no error is shown**, and the
      voice diagnostics report the real stage.
- [ ] Turning the master voice switch off still silences automatic answers (unchanged).

## 3. Ongoing status-bar meter after an update and after a reboot

- [ ] Install 2.42 over 2.41 **without opening the app first**: the ongoing charging card
      appears when the charger is connected, with no switch ever tapped.
- [ ] Reboot the phone: the card and the background monitor come back.
- [ ] Turn the card OFF in Settings, then update or reboot: it **stays off**. A saved OFF is a
      choice and must never be overwritten.
- [ ] Turn it back ON: it returns, and the notification "Stop" control still ends the monitor.
- [ ] Android 13+ without notification permission: the service may run but nothing shows in the
      status bar. Confirm the app explains that, rather than appearing broken.
- [ ] The floating pill is still **off** until the owner switches it on and grants "Display over
      other apps" — that is intended, not a regression.
- [ ] OEM reality check: note whether HyperOS/ColorOS battery optimization killed the monitor,
      and record it honestly. No cold-background survival is claimed.

## 4. Background battery-health monitoring default

- [ ] Fresh install and update-over-install both leave it ON, and Settings shows it ON.
- [ ] The hourly watch actually runs (low/full/heat warnings and the daily brief arrive as
      configured) without opening Voice & daily watch.
- [ ] Switching it OFF stops the hourly watch and is remembered across an update.
- [ ] Automatic **online** daily summaries remain OFF unless separately enabled — enabling
      background monitoring must not enable them.

## 5. Security check system-app scope

- [ ] The "Include preinstalled system apps" switch in the Security screen is ON by default, on
      a fresh install and after an update.
- [ ] A hash check with it ON really does include preinstalled packages (the count and the
      progress reflect it), and system apps that cannot be read are reported as skipped.
- [ ] Turning it OFF narrows the sweep and is remembered.
- [ ] Nothing is selected, deleted, disabled or granted automatically; an unknown or unchecked
      hash is still described as proving nothing.

## 6. Permission routing — must be checked on BOTH ROMs

Security check → the iMobile SMS-permission item (or any SMS/banking-SMS finding) → **Manage**.

- [ ] **Redmi 13 5G / HyperOS:** opens that app's own permission page — not MIUI's "Other
      permissions" hub, not a list of other apps, not the Settings homepage. Record the exact
      page title seen.
- [ ] If the ROM's own editor is missing, confirm the fallback chain ends at the app's App info
      page, and that the honest manual-path text is shown when the ROM refuses everything.
- [ ] **OPPO A3s / ColorOS:** the same finding opens the app's permission page; note the actual
      ColorOS page name, since the manual path in the app names one.
- [ ] A duplicate-label case (two apps with the same visible name) still routes to the selected
      package, never to the other one.
- [ ] The other findings are unchanged: overlay, usage access, install-unknown-apps,
      notification access, accessibility, device admin, screen lock, system update.

## 7. Main screen layout

- [ ] Default font scale: the first card is **not** clipped under the header or the LED strip;
      there is a visible gap; no two rows touch.
- [ ] **Largest font scale**: re-check the same screen. Nothing clipped, nothing overlapping,
      the header does not wrap into itself, card text stays readable.
- [ ] Smallest screen the owner has, and split-screen/one-handed mode if available.
- [ ] Gaps look even: one screen edge everywhere, even card gaps, consistent card padding.
- [ ] Long Tamil strings do not overflow or truncate mid-word on the header, the update card,
      the reviewed-setup card and the readings matrix.
- [ ] Scroll position still returns to the same card after visiting another screen.

## 8. Language consistency

- [ ] Cold start with Tamil saved: the **first** frame is already Tamil — header, cards and all.
      No English flash, no mixed screen.
- [ ] Toggle தமிழ் → EN on the home screen: **every** card changes at once, including "App
      updates" / "Verified in-app download; Android confirms installation.", "Reviewed feature
      setup", "Live Guard readings" and "Estimated phone age".
- [ ] Toggle back EN → தமிழ்: the same, in the other direction.
- [ ] Repeat the toggle on each screen (Health, Security, Voice, Settings, Insights, Assistant,
      Feature setup), since the screen tree is rebuilt on a toggle: no screen keeps stale text,
      and no screen loses a dialog or a half-typed field in a way that matters.
- [ ] The home list keeps its scroll position across a toggle.
- [ ] Notifications, the widget and the charging card follow the selected app language.
- [ ] The owner's phone-age estimate is still on the home dashboard, labelled as an estimate.

## 9. Publication record

- [ ] The published APK digest matches the tag run's digest table in
      `docs/candidate-build-digests.md` and in the release notes.
- [ ] The release notes state plainly that **no device test covered these bytes**, and that the
      permission-delta gate — not a device install — is why publication was allowed.
- [ ] Play Protect's actual wording on a real install is recorded **only if and when** the owner
      installs 2.42. Nothing may be written down before that happens.
- [ ] 2.41, 2.40, 2.39 and older assets remain byte-for-byte unchanged; nothing was overwritten.
