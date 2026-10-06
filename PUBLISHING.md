# Publishing these apps to a store — what is ready, what you must do

Short answer:

* **F-Droid** — possible for all five apps, but **F-Droid signs the APK with its own key**.
  Your family would have to uninstall the version installed from GitHub once, then install
  from F-Droid. (Unless we set up *reproducible builds*, see below.)
* **IzzyOnDroid** (an F-Droid-compatible store) — possible, **keeps your own signature**, no
  uninstall, and updates arrive within about a day of each GitHub release. This is the closest
  to "everything you want".
* **Google Play** — possible for **Ramesh Radio, PulseEQ, Internet Radio and AppForge**, but
  **not for CleanSweep as it is designed**: Play only allows "All files access"
  (MANAGE_EXTERNAL_STORAGE) for file managers, backup/restore apps, anti-virus apps, document
  management apps, on-device file search, encryption, and device-to-device migration. A cleaner
  is not on that list, and Play rejects the declaration. It costs $25 once.
* **Your own F-Droid repository on GitHub Pages** — no store, no review, no account, keeps your
  key, and the F-Droid client (or Droid-ify / Neo Store) can install and update from it. This
  is the best fit for "family" — see section 6.

Nothing here replaces your own accounts: I cannot create a GitLab, Codeberg, Google Play or
Amazon account for you, and I should not. What I *can* do — and what is done — is make the repo
ready so that submitting is a matter of copying a file and pressing a button.

---

## 1. What was done in the repo (v2.6)

| Done | Why it matters |
|---|---|
| **Release APKs are built and signed** with the same `ci-keystore/release.p12` as before | Every store rejects an APK with `android:debuggable`, which is exactly what the old `assembleDebug` artifacts were. Both variants use the same key, so a phone that has the debug build updates to the release one without uninstalling. |
| **A version tag publishes a GitHub Release** with `CleanSweep-v2.6.apk`, `Ramesh-Radio-v1.2.apk`, `Internet-Radio-v1.0.apk`, `AppForge-v1.0.apk`, `PulseEQ-v1.0.apk` | Actions artifacts are deleted after 90 days; Releases are permanent. This is also the download that IzzyOnDroid and the Obtainium app look for. |
| **`fastlane/metadata/android/...` listings** for all five apps, in English, plus Tamil for CleanSweep and Ramesh Radio | F-Droid, IzzyOnDroid and Google Play all read this exact layout. Placing it *inside each module* is the supported way to describe several apps that live in one repository. |
| **A check that no release APK is debuggable**, and the release APK's **signing certificate is printed in the CI log** | No surprises at submission time. |
| **No proprietary dependencies** (verified: no Play Services, Firebase, ads, analytics, crash reporting anywhere) | This is the first thing F-Droid and IzzyOnDroid review. |
| **The Gradle wrapper is committed by CI** | F-Droid's build server and anyone building locally want `gradlew` present, because it pins Gradle 8.14.3. |

Still required before any submission:

1. **A licence file.** F-Droid and IzzyOnDroid only accept software under a recognised free
   licence (GPL-3.0, Apache-2.0, MIT, LGPL-3.0 …). The repo has none yet — this is the one
   decision only you can make. (`License:` in the store metadata must match it.)
2. **Version tags** for each release you want published (I add them once you say go).
3. **Screenshots** (2 or more) if you want the store pages to look complete. Send me the
   phone screenshots and I will drop them into the right folders. Without them the pages
   simply show no gallery.
4. **The `:app` module's application id** is still the placeholder `com.example.internetradio`.
   F-Droid wants an id that belongs to you. Renaming it to something like
   `com.universalrp.internetradio` is a one-line change, but it makes it a *different app*:
   anyone who installed the old one must uninstall it first.

---

## 2. How the channels compare

| | Google Play | F-Droid (main repo) | IzzyOnDroid | Your own repo (GitHub Pages) | GitHub Releases + Obtainium |
|---|---|---|---|---|---|
| Who signs the APK | You (or Play) | **F-Droid key** (yours only with reproducible builds) | **You** | **You** | **You** |
| Family must reinstall | No | **Yes, once** | No | No | No |
| Review time | Days–weeks | Weeks (then 24–48 h to appear) | ~1 day | none | none |
| Cost | $25 once | free | free | free | free |
| Auto-updates on the phone | via Play | via F-Droid app | via F-Droid app | via F-Droid app | via Obtainium |
| CleanSweep allowed? | **No** (see above) | yes | yes | yes | yes |
| Needs an account | Google | GitLab (for the MR) | Codeberg (issue) or email | GitHub (you have it) | none |

---

## 3. IzzyOnDroid — the easiest real store (recommended first)

Requirements are exactly what the repo now produces: an APK **signed by you with a release
key**, **not debuggable**, attached to a **GitHub release**, with text descriptions in the
repo. What to do:

1. Tag a release so a GitHub Release exists (see section 5) — for example `v2.6`.
2. Open an issue at **https://codeberg.org/IzzyOnDroid/repo/issues/new/choose** using the
   "app inclusion request" template, one per app. Give: app name, package id, source repo
   `https://github.com/universalrp2003/InternetRadio`, the homepage for the release
   (`https://github.com/universalrp2003/InternetRadio/releases`), and the licence.
3. They scan the APK, check the source and test on a device. Usually about a day.
4. From then on, every GitHub Release you cut is picked up automatically — your signature,
   the family never reinstalls anything.

If an app is later accepted into the *official* F-Droid repo, IzzyOnDroid removes it (to avoid
signature confusion). So: do IzzyOnDroid now, F-Droid only if you actually want it.

---

## 4. F-Droid — the big one, and the honest catch

**The catch:** F-Droid builds your app from source on their own machines and **signs it with a
key they generate for you**. An APK signed by someone else cannot update an app you signed —
so your family uninstalls the GitHub build once and reinstalls from F-Droid. After that,
updates come through the F-Droid client.

There is one way to keep your own signature *and* be in the main F-Droid repo: **reproducible
builds**. If a build on their server produces a byte-identical APK to yours (apart from the
signature), they publish *your* signed APK and mark the app "verified reproducible". This is
real work (the build must not embed timestamps, paths or build-machine data), and it is a
follow-up project, not a first step. Our setup is a decent starting point: fixed Gradle
version, fixed keystore, no minification.

Steps when you are ready:

1. Create a GitLab account (free) — fdroiddata lives on GitLab.
2. Fork **https://gitlab.com/fdroid/fdroiddata** and clone it.
3. Add one metadata file per app: `metadata/<applicationId>.yml`. I will write the five YAML
   files for you (they need the licence choice, the commit hash of the tag, and the exact
   build commands `assembleRelease` plus `subdir:` for each module).
4. Run their linter locally if you want (`pip install fdroidserver`, then
   `fdroid lint <package-id>`), or just open the merge request — their CI lints it for you.
5. Open the Merge Request. Reviewers check the licence, the dependencies and the build; they
   may ask questions. After the merge, the app appears in the F-Droid repo within a day or two.

Useful facts for the submission:

* The build command per app is a **release** build in the module's own directory, e.g. for
  CleanSweep `subdir: cleaner` + `gradle: [assembleRelease]`.
* `UpdateCheckMode: RepoManifest` (reads the version from the repo) is the practical choice for
  several apps in one repository; `Tags` wants one tag per app version, which our single
  repo-wide tag cannot express.
* Tell them the descriptions are already in the repo under
  `<module>/fastlane/metadata/android/`, so the reviewers do not need to write them.

---

## 5. Cutting a release (what the family installs from now on)

The GitHub Release is created automatically by CI when a tag is pushed:

```bash
git tag v2.6               # once, on the commit you want to publish
git push origin v2.6
```

That runs the normal build, then attaches **`CleanSweep-v2.6.apk`** and the other four release
APKs to a Release named `v2.6`. Anyone can then install straight from
`https://github.com/universalrp2003/InternetRadio/releases` — a link that does not expire.

Inside each app the version stays in the file name, exactly as you asked.

---

## 6. The nicest option for family: your own F-Droid repository

This gives F-Droid-style automatic updates **with your own signing key**, no store and no
review:

1. CI runs `fdroid update` after each release tag, signing the index with a key you keep.
2. The generated repository is published to a `gh-pages` branch (free hosting on GitHub).
3. On the phone, open F-Droid (or Droid-ify / Neo Store) → Settings → Repositories → add
   `https://universalrp2003.github.io/InternetRadio/repo`.
4. The five apps appear there, install normally, and **update themselves** when you push a new
   tag — same signature as the APKs already on the phone, so no uninstalling ever.

Say the word and I will build that CI job. It is the only route that gives automatic updates
while keeping your keystore.

The no-work alternative is **Obtainium** (a free app): add the repo URL
`https://github.com/universalrp2003/InternetRadio`, pick the APK you want, and it checks the
GitHub Releases for new versions and installs them for you. Nothing to build, nothing to sign.

---

## 7. Google Play (for the apps that qualify)

Play is the only store that needs real paperwork:

* a Google Play developer account ($25 once) and an identity check;
* a **privacy policy URL** (a page in this repo is enough);
* the **Data safety** form — for these apps: no data collected, no data shared; the optional AI
  sends the text you type to a provider whose key you supplied, and only when you tap Analyse;
* **Android App Bundle (.aab)** — Play does not accept APKs for new apps, so CI must also run
  `bundleRelease` (a two-line change I can make);
* **screenshots** (at least 2), a 512×512 icon and a 1024×500 feature graphic;
* a content rating questionnaire, and target API level compliance (we already target API 36).

And the blocker to be clear about: **CleanSweep will very likely be rejected** because of
`MANAGE_EXTERNAL_STORAGE`. Options if you want it on Play anyway:

1. Ship a **separate "Play edition"** that uses the system file picker (Storage Access
   Framework) instead of all-files access. It could still scan the folders you hand it, but it
   is a noticeably weaker cleaner and a lot of work.
2. Publish the other four apps on Play, keep CleanSweep on F-Droid/IzzyOnDroid/GitHub.

My honest advice: don't fight Google over all-files access. The people who want this app are
exactly the people who install from F-Droid or a link.

---

## 8. Other stores, briefly

* **Amazon Appstore** — free developer account, accepts APKs, and CleanSweep's all-files access
  is not policed the way Play does. Realistically only worth it if you want the Fire-tablet
  audience; the radio app would fit there better than the cleaner.
* **Samsung Galaxy Store / Huawei AppGallery** — free seller accounts, both accept APKs. Worth
  doing for **Ramesh Radio** later (Tamil audience is large on both), not a first step.
* **Aptoide / APKPure / APKMirror** — self-service uploads, no real review, and they do not
  help the family; they mostly mirror what is already public. Not recommended as a primary
  channel, though harmless.

---

## 9. Checklist

- [ ] Choose the licence and add `LICENSE` (I will do it the same hour you decide).
- [ ] Say which apps go public and whether to rename `com.example.internetradio`.
- [ ] Send screenshots for the store pages (optional but recommended).
- [ ] Tag the release (`v2.6`) so the GitHub Release and the permanent link exist.
- [ ] Ask IzzyOnDroid to include the apps (Codeberg issue — one per app).
- [ ] Decide on the self-hosted F-Droid repo for the family (I build the CI job).
- [ ] Only if you want Play: create the account, and we ship the other four apps there.
