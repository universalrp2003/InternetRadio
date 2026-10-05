# AppForge — offline app builder for Android

Build an app **on your phone**, with no account, no internet and no PC.

**v1.0 highlights:** block-based screen designer • live preview that runs the real app •
one-file HTML export • full Android Studio project export (Kotlin + Gradle + CI workflow) •
4 starter templates • everything stored locally.

## What it does

| Step | What happens |
|---|---|
| 1. Design | Add screens and fill them with blocks: heading, paragraph, tip, bullet list, link, button, image, text field, save button, checklist, divider |
| 2. Preview | The preview is not a mock-up — it is the actual generated app running in a WebView, with working navigation, saved text fields and persistent checklists |
| 3. Export | **HTML app:** one self-contained file that works in any browser, fully offline<br>**Android project:** a zipped Android Studio project whose `assets/index.html` is your app and whose `.github/workflows/build-apk.yml` builds the APK for you on GitHub Actions |
| 4. Look inside | Inspect and copy the generated Kotlin, manifest, Gradle files and README right in the app |

Templates included: blank, notes, link hub, task list. Every word and colour is editable afterwards.

## Where files go

- Projects: `Android/data/com.universalrp.appforge/files/projects/*.json`
- Exports: `Android/data/com.universalrp.appforge/files/exports/…`

Press **Share** to send an export to Files, Drive, WhatsApp or anywhere else — no storage
permission is needed for any of this.

## Getting an APK (the honest part)

No phone can run the Android compiler, so AppForge does not pretend to build APKs on-device.
It does the next best thing:

1. Export the **Android project (.zip)** and share it to your phone/Drive/PC.
2. Unzip it into a new GitHub repository.
3. Open *Actions → Build APK → Run workflow*.
4. Download the `app-apk` artifact and install it (allow "install unknown apps").

Or open the folder in Android Studio and press *Run*. The generated project is a normal,
standard Gradle Android app — no AppForge dependency required.

## Privacy

- No account, no analytics, no ads.
- Projects and exports live in AppForge's own app folder until *you* share them.
- INTERNET is declared only so the preview can display images you link to.

## Building this module

```bash
gradle wrapper            # once, if the wrapper is missing
./gradlew :builder:assembleDebug
# APK at builder/build/outputs/apk/debug/builder-debug.apk
```

Or push to GitHub and download the `appforge-apk` artifact from *Actions → Build APK*.
Every CI build is signed with the same fixed keystore, so new APKs install as updates.

## Limitations (by design)

- The exported app is an offline HTML app in a WebView shell — perfect for notes, link hubs,
  checklists, catalogues and simple tools; it is not a native app with hardware APIs.
- Images and links you point at need internet *in the exported app* if they are remote URLs;
  the generated HTML file itself works offline.
