# Getting these apps into F-Droid (and IzzyOnDroid)

Two free, open stores, same client on the phone. Short version:

* **IzzyOnDroid** — fastest, keeps **your** signing key, so nobody reinstalls anything. One
  issue on Codeberg and you are done. Start here.
* **F-Droid main repo** — the big one, but F-Droid **builds the app itself and signs it with its
  own key**, so your family uninstalls the GitHub build once and reinstalls from F-Droid. The
  recipe files are already written (this folder).

Both are free. Neither needs the $25 Google Play account.

---

## A. IzzyOnDroid (recommended first — 10 minutes of your time)

Their requirement is exactly what the repository already produces: an APK signed by you with a
release key, not debuggable, attached to a GitHub Release, with the description text in the
repo.

1. Open <https://codeberg.org/IzzyOnDroid/repo/issues/new/choose> and pick the **app inclusion
   request** template.
2. Paste this, once per app (change the name and id):

```
App name: CleanSweep
Package id: com.universalrp.cleansweep
Source code: https://github.com/universalrp2003/InternetRadio
Release downloads (the APK): https://github.com/universalrp2003/InternetRadio/releases
License: GPL-3.0-or-later
Description: already in the repository at cleaner/fastlane/metadata/android/en-US/ (English
and Tamil). It is a junk cleaner and phone-health app with no ads, no analytics and no
Accessibility service; the optional AI uses the user's own API key.
```

3. That is it. They scan the APK, check the source, usually list it within about a day, and from
   then on every GitHub Release you publish is picked up automatically.

Do the same for `com.universalrp.tamilnadufm` (Ramesh Radio), `com.universalrp.internetradio`
(Internet Radio), `com.universalrp.appforge` (AppForge) and `com.universalrp.pulseeq` (PulseEQ).

**Plus side:** because IzzyOnDroid ships *your* signed APK, your family can switch from the
GitHub downloads to the F-Droid app and keep updating over the installed app.

---

## B. F-Droid main repository

### What happens to the signing key

F-Droid builds your app from source on their own machines and signs it with a key they generate
for you. An APK signed by a different key cannot update an app signed by yours, so **anyone with
the GitHub build uninstalls it once** and reinstalls from F-Droid. After that, updates come
through the F-Droid client. (The alternative — *reproducible builds* — keeps your signature, but
it is a real project of its own; not needed to get listed.)

### What is already prepared

The five recipe files are in `store/fdroid/metadata/`:

```
com.universalrp.cleansweep.yml
com.universalrp.tamilnadufm.yml
com.universalrp.internetradio.yml
com.universalrp.appforge.yml
com.universalrp.pulseeq.yml
```

They point at the release tag, use `subdir:` to select each app's module of this one Gradle
project, build with `assembleRelease`, and take `UpdateCheckMode: RepoManifest` so F-Droid can
find each app's own version in the repository (the five apps version independently, so one
tag per app version would not work).

### The steps you must do (they need your account)

1. Create a free account at <https://gitlab.com> (F-Droid's data lives on GitLab).
2. Fork <https://gitlab.com/fdroid/fdroiddata>.
3. Clone your fork and copy the five files in:

```bash
git clone https://gitlab.com/<your-user>/fdroiddata.git
cd fdroiddata
cp /path/to/InternetRadio/store/fdroid/metadata/*.yml metadata/
git checkout -b universalrp2003-apps
git add metadata/
git commit -m "New apps: CleanSweep, Ramesh Radio, Internet Radio, AppForge, PulseEQ"
git push origin universalrp2003-apps
```

4. Open a Merge Request from that branch to `fdroiddata`'s `master`. In the description, say:

```
Five small apps by one author, all in one Gradle project (subdir selects the module):
- com.universalrp.cleansweep  CleanSweep  — junk cleaner / phone health
- com.universalrp.tamilnadufm Ramesh Radio — Tamil FM + news radio
- com.universalrp.internetradio Internet Radio — radio streaming
- com.universalrp.appforge    AppForge    — build a small app on the phone
- com.universalrp.pulseeq     PulseEQ     — 20-band equalizer (no INTERNET permission)

Licence: GPL-3.0-or-later (LICENSE in the repo root).
Description, screenshots and a Tamil translation live in <module>/fastlane/metadata/android/.
No proprietary dependencies: no Play Services, Firebase, ads, analytics or crash reporting.
Signing: CI builds release variants signed with the project keystore; fdroidserver should
re-sign with the F-Droid key as usual.
```

5. Their CI lints the metadata and builds each app; reviewers may ask questions in the MR. Once
   merged, the apps appear in the repository within about a day.

### If you would rather not make a GitLab account

Use the **Requests For Packaging** tracker instead — someone on the F-Droid team usually picks
it up: <https://gitlab.com/fdroid/rfp/-/issues/new>. The paste-ready text is in
`store/fdroid/RFP_ISSUE.md`.

---

## C. Keeping the recipes current

Every release:

1. Push your work and tag it: `git tag v2026.11.01 && git push origin v2026.11.01` (CI then
   publishes the GitHub Release with the APKs and bundles attached).
2. If F-Droid has already listed the apps, no action is needed — `UpdateCheckMode: RepoManifest`
   and `AutoUpdateMode: Version` make them pick the new version up. If a build entry has to be
   added manually, copy the previous one, change `versionName`, `versionCode` and `commit`.

## D. Honest notes for the reviewers (and for you)

* **The keystore in `ci-keystore/` is not a secret.** It exists so every build from every
  machine has the same signature for the family installs. Its password is in the build file on
  purpose. If you ever publish on a store that wants a private key, generate a new keystore
  and keep it out of the repository.
* **CleanSweep asks for All files access** (`MANAGE_EXTERNAL_STORAGE`). That is the whole point
  of a cleaner and F-Droid allows it; it just needs to be declared honestly, which the listing
  text does. Google Play is the store that refuses it.
* **Screenshots**: the fastlane folders are ready for them
  (`<module>/fastlane/metadata/android/<locale>/images/phoneScreenshots/`). F-Droid shows a
  listing without them, but it looks bare.
