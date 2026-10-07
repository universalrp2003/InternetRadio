# The short version to paste (F-Droid request)

This is the same request as `RFP_ISSUE.md`, shortened so it is easy to select and paste on a
phone. Open <https://gitlab.com/fdroid/rfp/-/issues/new>, paste everything between the two lines
below, and submit.

--- copy from the next line ---

Five apps, one repository — packaging request

Repository: https://github.com/universalrp2003/InternetRadio
Licence: GPL-3.0-or-later (LICENSE in the repository root)
Author: Ramesh prathap .R <universalrp2003@gmail.com>
Release tag: v2026.10.06 (all APKs attached to the GitHub Release)
One Gradle project, one module per app (subdir selects it):
- com.universalrp.cleansweep     cleaner    2.6 (11)  junk cleaner, phone health
- com.universalrp.tamilnadufm    radio      1.2 (2)   Tamil FM + world news radio
- com.universalrp.internetradio  app        1.0 (2)   internet radio
- com.universalrp.appforge       builder    1.0 (1)   build a small app on the phone
- com.universalrp.pulseeq        equalizer  1.0 (1)   20-band equalizer, no INTERNET permission

UpdateCheckMode: RepoManifest and AutoUpdateMode: Version, because the five apps version
independently inside one repository. No proprietary dependencies: no Google Play Services, no
Firebase, no ads, no analytics, no crash reporting. CleanSweep uses MANAGE_EXTERNAL_STORAGE
deliberately (it is a file cleaner; nothing is deleted without the user selecting it and
confirming) and declares no Accessibility service.

Descriptions (English, plus Tamil for CleanSweep and Ramesh Radio), 512x512 icons, 1024x500
feature graphics and changelogs are in <module>/fastlane/metadata/android/. Ready F-Droid
metadata files are in store/fdroid/metadata/ in the repository.

AI disclosure, stated up front: the Kotlin code was written by an AI coding agent (Arena.ai Agent
Mode) working from the developer's feature requirements. The developer chose every feature,
tested each build on a phone, reported defects and directed the fixes. In the apps themselves,
CleanSweep has an optional assistant the user may point at a provider with their own API key
(Gemini, NVIDIA, OpenRouter, Groq, OpenAI or a custom endpoint, plus a keyless free option);
no key ships with the app and nothing is sent unless the user turns it on and asks. The other
four apps use no AI at all. If this is a problem for F-Droid, please say so — we would rather
state it now than have it discovered later.

--- end of paste ---

## If they ask for the metadata files

They are in `store/fdroid/metadata/` on the branch
`arena/01a10b15-internetradio`, one file per application id, or send me the question and I will
answer it in the thread.
