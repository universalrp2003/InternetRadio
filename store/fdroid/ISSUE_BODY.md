<!-- FILED: this exact text is fdroid/rfp#4512, opened 7 Oct 2026.
     https://gitlab.com/fdroid/rfp/-/work_items/4512  Do not edit it to mean something else;
     if a maintainer asks a question, answer in the thread, and record the answer here. -->

Hello, and thank you for F-Droid. I am the developer of these five apps and I would like to
request packaging for them. They share one Gradle project, one module each.

Repository: https://github.com/universalrp2003/InternetRadio
Licence: GPL-3.0-or-later (LICENSE in the repository root)
Author: Ramesh prathap .R <universalrp2003@gmail.com>
Release tag: v2026.10.06 (all APKs are attached to the GitHub Release)
None of the five is listed in F-Droid or in this tracker yet.

- com.universalrp.cleansweep     module cleaner    2.6 (11)  junk cleaner, phone health, security, network
- com.universalrp.tamilnadufm    module radio      1.2 (2)   Tamil FM and world news radio
- com.universalrp.internetradio  module app        1.0 (2)   internet radio streaming
- com.universalrp.appforge       module builder    1.0 (1)   build a small app on the phone
- com.universalrp.pulseeq        module equalizer  1.0 (1)   20-band equalizer, no INTERNET permission

Build notes: `subdir` selects the module of this one Gradle project, and the recipes are already
written in `store/fdroid/metadata/` in the repository, ready to copy into fdroiddata. I use
UpdateCheckMode: RepoManifest and AutoUpdateMode: Version because the five apps version
independently inside one repository.

No proprietary dependencies: no Google Play Services, no Firebase, no ads, no analytics, no
crash reporting. CleanSweep asks for MANAGE_EXTERNAL_STORAGE deliberately, because it is a file
cleaner, and nothing is ever deleted without the user selecting it and confirming. It declares
no Accessibility service and never taps inside other apps.

Descriptions (English, plus Tamil for CleanSweep and Ramesh Radio), 512x512 icons, 1024x500
feature graphics and changelogs are in `<module>/fastlane/metadata/android/`. Phone screenshots
are on the way.

AI disclosure, stated up front: the Kotlin code was written by an AI coding agent (Arena.ai
Agent Mode) working from my feature requirements. I chose every feature, tested each build on my
phone, reported defects and directed the fixes until the behaviour was right. In the apps
themselves, CleanSweep has an optional assistant that the user may point at a provider with
their own API key (Gemini, NVIDIA, OpenRouter, Groq, OpenAI or a custom endpoint, plus a keyless
free option); no key ships with the app and nothing is sent unless the user turns it on and asks.
The other four apps use no AI at all. If this is a problem for F-Droid, please say so and I will
not argue — I would rather state it now than have it discovered later.
