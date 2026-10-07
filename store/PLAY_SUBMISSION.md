# Google Play submission pack

Everything below is copy-paste ready. The only steps that need *you* are the ones that need a
Google account. Play will show these apps under the developer name on your Play account
(Ramesh prathap .R, `universalrp2003@gmail.com`) — change it in the Console if you want a
different public name.

---

## 0. What is already prepared

* **App bundles** — CI now builds `cleaner/build/outputs/bundle/release/*.aab` together with the
  APKs, renamed with their versions (`CleanSweep-v2.6.aab`, `Ramesh-Radio-v1.2.aab`,
  `Internet-Radio-v1.0.aab`, `AppForge-v1.0.aab`, `PulseEQ-v1.0.aab`) and uploaded as the
  **`store-bundles`** artifact. Play has not accepted APKs for new apps since 2021 — a `.aab` is
  the only thing it takes.
* **Listing text** for every app lives in the repository, ready to paste:
  `app/fastlane/metadata/android/en-US/…`, `radio/…`, `cleaner/…`, `builder/…`, `equalizer/…`
  (`title.txt`, `short_description.txt`, `full_description.txt`), with Tamil for CleanSweep and
  Ramesh Radio under `ta-IN/`.
* **Privacy policy** — [PRIVACY.md](PRIVACY.md). Play needs a public URL; use
  **https://github.com/universalrp2003/InternetRadio/blob/arena/01a10b15-internetradio/PRIVACY.md**
  now, or the identical `main` URL once this branch is merged into `main`.
* **The placeholder app id is gone**: `:app` is now `com.universalrp.internetradio`.
* **No proprietary SDKs, no ads, no analytics** — nothing to declare in the "Ads" section.

## 1. The apps that qualify, and the one that does not

| App | Package id | Play? |
|---|---|---|
| Ramesh Radio | `com.universalrp.tamilnadufm` | **Yes** |
| Internet Radio | `com.universalrp.internetradio` | **Yes** |
| AppForge | `com.universalrp.appforge` | **Yes** |
| PulseEQ | `com.universalrp.pulseeq` | **Yes** |
| CleanSweep | `com.universalrp.cleansweep` | **No — see below** |

**Why CleanSweep cannot go to Play as it is:** it declares `MANAGE_EXTERNAL_STORAGE`
("All files access"). Play permits that permission only for file managers, backup/restore apps,
anti-virus apps, document management apps, on-device file search, disk/file encryption and
device-to-device migration. A junk cleaner is not on that list, and the Permissions Declaration
Form is rejected for it. Load-shedding the feature would mean using the system file picker
(Storage Access Framework) instead, which cannot see the junk the app exists to find. Keep
CleanSweep on the GitHub Release / F-Droid / IzzyOnDroid channels.

## 2. One-time account setup (you)

1. Create a Google Play developer account at <https://play.google.com/console> — **$25 once**,
   plus an identity check (ID and address). This can take a day or two.
2. In **Play Console → Settings → Developer account → Account details**, check the developer
   name and contact email shown publicly. Set the contact email to
   `universalrp2003@gmail.com` if it is not already.
3. Nothing else is needed before uploading.

## 3. Per-app upload (about 15 minutes the first time, 5 after that)

For each of the four apps:

1. **Create app** → app name, default language **English (United States)**, type **App**,
   **Free**.
2. **Store listing** → paste from the repo:
   * App name: `cat radio/fastlane/metadata/android/en-US/title.txt` (≤30 characters)
   * Short description: `short_description.txt` (≤80)
   * Full description: `full_description.txt` (≤4000)
   * Graphics: 512×512 icon, 1024×500 feature graphic, and 2–8 phone screenshots
     (send me the screenshots and I will place them in the repo for every store at once).
   * Contact email: `universalrp2003@gmail.com`
   * Privacy policy URL: the PRIVACY.md link above.
3. **App content** (the questionnaire section):
   * **Privacy policy**: the URL above.
   * **Ads**: “No, my app does not contain ads”.
   * **App access**: “All functionality is available without special access”.
   * **Content rating**: fill the questionnaire — for all four: no violence, no sexuality, no
     profanity, no gambling, no drugs, no user-generated content, no user interaction, no
     location sharing, no purchases. PulseEQ/Ramesh Radio: “Music/audio”.
   * **Target audience**: 13+ (or 18+). Not designed for children.
   * **News app**: answer **No**, even for Ramesh Radio — it *streams* radio stations, it does
     not publish news content itself.
   * **Data safety** — the answers, per app:
     * *Does your app collect or share any of the required user data types?* → **No.**
     * *Is all of the user data collected by your app encrypted in transit?* → not applicable
       (nothing is collected).
     * *Do you provide a way for users to request that their data is deleted?* → not
       applicable; there is no server-side data.
     * If the form insists on the AI feature: the request is user-initiated and carries only
       the text the user chose to send, using the user's own API key, straight to the provider.
       The developer receives nothing. Declare it honestly as
       **“App functionality — user-initiated transmission to a third-party service the user
       configured”**, and note the provider in the description if you want to be extra clear.
   * **Government apps / financial features / health**: No to all.
4. **Production → Create new release** → upload the `.aab` from the `store-bundles` artifact
   (download it from the GitHub Actions run of the tag you are publishing).
   * **Release notes**: copy `changelogs/<versionCode>.txt` from the app's fastlane folder.
   * **Play App Signing**: leave it ON (the default). Play re-signs the app with its own key —
     that is normal and is why a Play install cannot replace a GitHub-installed copy, and vice
     versa. Choose one channel per app per phone and stay on it.
5. **Countries/regions**: all, or India + wherever your family lives.
6. **Send for review.** First review usually takes a few days; later updates are faster.

## 4. After the first review

* Every new version: build a new tag (`git tag v2026.11.01 && git push origin v2026.11.01`),
  download the fresh `.aab`, and upload it in **Production → Create new release**.
* Keep the version code going up — Play refuses a bundle with a version code it has already
  seen. CI takes it from each module's `versionCode`, so bump it there.

## 5. What is still missing before you press upload

* **Screenshots** (2 minimum per app, 8 maximum). Send them and I will put them in the repo,
  and you can upload the same files to Play.
* **512×512 icon** and **1024×500 feature graphic**. The launcher icon in the repo is a vector
  asset, which Play's uploader cannot take; send me a screenshot of the app icon, or say the
  word and I will generate a matching 512 icon and feature graphic for each app.
* **The developer account itself** ($25 + identity check) — only you can do that, and only from
  your own Google account.
