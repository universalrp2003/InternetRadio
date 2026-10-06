# Publish these apps for free — the exact steps

**Route: F-Droid and IzzyOnDroid. No Google Play, no $25.** Everything on my side is already in
the repository, so both of these are copy-paste jobs. Google Play is the one route that costs
money and it is the one route that would refuse CleanSweep's All-files access anyway, so we skip
it.

- **IzzyOnDroid** — the app is listed in about a day, and it **keeps your signature**, so nobody
  reinstalls anything. Recommended first.
- **F-Droid (the main store)** — free, takes a few weeks because a reviewer checks the recipes
  and the server builds the apps with **F-Droid's own key**. That means the family uninstalls the
  GitHub APK once and installs from F-Droid.

Both need a free account that only you can create (I cannot sign up as you). IzzyOnDroid uses
**Codeberg**, F-Droid uses **GitLab**. Nothing else about them needs an account, no card is asked
for, and no fee is paid at any point.

---

## A. IzzyOnDroid — fastest, and your key stays

1. Make a free account at <https://codeberg.org> (e-mail + password only).
2. Open <https://codeberg.org/IzzyOnDroid/repo/issues/new/choose> and pick the app-inclusion
   template (any "new issue" button works).
3. Paste the text below and submit. Then wait a day or two.

```text
Please add my apps to IzzyOnDroid.

Repository: https://github.com/universalrp2003/InternetRadio
Licence: GPL-3.0-or-later (LICENSE in the repository root)
Author: Ramesh prathap .R <universalrp2003@gmail.com>
Latest release tag: v2026.10.06 (all APKs attached to the GitHub Release)
APK signing: the release APKs are signed with a stable keystore committed at
ci-keystore/release.p12, so future releases keep the same signature and your
updater will keep working.

Five apps share this one Gradle project, one module each:
- CleanSweep  (com.universalrp.cleansweep)   module cleaner   2.6, versionCode 11
- Ramesh Radio (com.universalrp.tamilnadufm) module radio     1.2, versionCode 2
- Internet Radio (com.universalrp.internetradio) module app   1.0, versionCode 2
- AppForge    (com.universalrp.appforge)     module builder   1.0, versionCode 1
- PulseEQ     (com.universalrp.pulseeq)      module equalizer 1.0, versionCode 1

No Google Play Services, no Firebase, no ads, no analytics, no crash reporting.
PulseEQ declares no INTERNET permission at all. CleanSweep asks for All-files
access because it is a cleaner, and nothing is ever deleted without the user
selecting it and confirming. Its optional AI uses an API key the user pastes
in themselves; no key ships with the app.

Descriptions are already in fastlane format in each module under
<module>/fastlane/metadata/android/ (en-US everywhere, ta-IN for CleanSweep and
Ramesh Radio), and store/fdroid/metadata/ in the repository has ready F-Droid
metadata files (subdir per module) if they are useful.
```

**After that:** every new tagged Release is picked up automatically, so publishing later versions
is just `git tag` + push.

---

## B. F-Droid (main repository) — free, slower, F-Droid signs the build

I have already written the five recipe files. They are in
[`store/fdroid/metadata/`](fdroid/metadata/), one per application id, each pointing at its own
module with `subdir`.

1. Make a free account at <https://gitlab.com>.
2. Go to <https://gitlab.com/fdroid/fdroiddata> and fork it (the Fork button).
3. In your fork open the `metadata/` folder, "Add file → Upload file", and upload all five
   `.yml` files from `store/fdroid/metadata/` in one go.
4. Commit, then press **Create merge request** back into `fdroid/fdroiddata`.
5. Paste the text below as the description and submit. A reviewer replies; if they ask for a
   change, we change the recipe file and push it again.

```text
Adds five apps from one repository (one Gradle project, one module per app).

Repository: https://github.com/universalrp2003/InternetRadio
Licence: GPL-3.0-or-later
Author: Ramesh prathap .R <universalrp2003@gmail.com>
Tag for the versions in these recipes: v2026.10.06
Commit: cf77610 (the tag points at it)

- com.universalrp.cleansweep    subdir cleaner    2.6 (11)
- com.universalrp.tamilnadufm   subdir radio      1.2 (2)
- com.universalrp.internetradio subdir app        1.0 (2)
- com.universalrp.appforge      subdir builder    1.0 (1)
- com.universalrp.pulseeq       subdir equalizer  1.0 (1)

UpdateCheckMode is RepoManifest and AutoUpdateMode is Version because the five
apps version independently inside one repository. No proprietary dependencies,
no Google Play Services, no ads or analytics. CleanSweep uses
MANAGE_EXTERNAL_STORAGE deliberately: it is a file cleaner, nothing is deleted
without the user selecting it and confirming. Its AI features are opt-in with a
key the user pastes, and the app shows what was sent. No Accessibility service
is used or declared. Listings for en-US and ta-IN are already in fastlane format
in each module, and screenshots are on the way.
```

**If you would rather not make a GitLab account at all:** skip the fork and open an issue at
<https://gitlab.com/fdroid/rfp/-/issues/new> with the text in
[`fdroid/RFP_ISSUE.md`](fdroid/RFP_ISSUE.md). A volunteer packs it for you. GitLab does not
require public visibility for issue replies; a plain issue is readable to anyone with the link,
but it only contains what is in the text above — no keys, no personal data.

---

## What I still need from you

1. **The screenshots again** — they are not on my disk. A phone screenshot is attached to a chat
   message as an image; in the next message, drag the nine in (or send them a few at a time) and
   I will crop out the private parts and put them in the right folders for every store at once.
2. **The 512×512 icon** (the launcher icon is a vector; the stores want a PNG). I can make it.
   The 1024×500 feature graphic for F-Droid too.

## What you do not need to worry about

- **Money:** nothing here costs anything, at any step.
- **Your keystore:** keeping it in the repository is correct for IzzyOnDroid and GitHub Releases.
  F-Droid's main repository ignores it and signs with its own key, which is why the family
  reinstalls once if you go that way.
- **Updates afterwards:** a new git tag makes a new GitHub Release for IzzyOnDroid to pick up;
  for F-Droid I update the recipe's `commit`, version and code — tell me the new version and I
  will push it.
