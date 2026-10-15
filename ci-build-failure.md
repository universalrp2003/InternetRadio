# CI build failure excerpt

## Kotlin / resource errors
```
```

## Gradle summary
```
```

## APK verification output
```
=== APK files ===
total 19476
drwxr-xr-x 2 runner runner     4096 Oct 15 03:45 .
drwxr-xr-x 4 runner runner     4096 Oct 15 03:46 ..
-rw-r--r-- 1 runner runner 19929869 Oct 15 03:45 cleaner-debug.apk
-rw-r--r-- 1 runner runner      414 Oct 15 03:45 output-metadata.json
total 16512
drwxr-xr-x 2 runner runner     4096 Oct 15 03:43 .
drwxr-xr-x 4 runner runner     4096 Oct 15 03:45 ..
-rw-r--r-- 1 runner runner 16892103 Oct 15 03:43 equalizer-debug.apk
-rw-r--r-- 1 runner runner      411 Oct 15 03:43 output-metadata.json
total 20176
drwxr-xr-x 2 runner runner     4096 Oct 15 03:44 .
drwxr-xr-x 4 runner runner     4096 Oct 15 03:45 ..
-rw-r--r-- 1 runner runner      413 Oct 15 03:44 output-metadata.json
-rw-r--r-- 1 runner runner 20644229 Oct 15 03:44 radio-debug.apk
total 79820
drwxr-xr-x 2 runner runner     4096 Oct 15 03:47 .
drwxr-xr-x 4 runner runner     4096 Oct 15 03:47 ..
-rw-r--r-- 1 runner runner      423 Oct 15 03:47 output-metadata.json
-rw-r--r-- 1 runner runner 81721909 Oct 15 03:47 vocalremover-debug.apk
total 16264
drwxr-xr-x 2 runner runner     4096 Oct 15 03:44 .
drwxr-xr-x 3 runner runner     4096 Oct 15 03:44 ..
-rw-r--r-- 1 runner runner      435 Oct 15 03:44 output-metadata.json
-rw-r--r-- 1 runner runner 16640403 Oct 15 03:44 secretmessage-debug.apk
OK: standalone Vocal Remover debug + release APKs: vocalremover/build/outputs/apk/debug/vocalremover-debug.apk / vocalremover/build/outputs/apk/release/vocalremover-release.apk
=== Google Play bundles (.aab) for the three store-packaged apps ===
OK: CleanSweep-v2.46.aab inside the build
OK: PulseEQ-v1.5.aab inside the build
OK: Ramesh-Radio-v1.11.aab inside the build
=== signed release APKs ===
OK: cleaner/build/outputs/apk/release/cleaner-release.apk
OK: radio/build/outputs/apk/release/radio-release.apk
OK: equalizer/build/outputs/apk/release/equalizer-release.apk
OK: vocalremover/build/outputs/apk/release/vocalremover-release.apk
=== App icons inside the built APKs ===
FAIL: Secret Message local preview unexpectedly requests an Android permission
```

## Store artwork step
```
wrote cleaner/fastlane/metadata/android/en-US/images/icon.png 7005 bytes
wrote cleaner/fastlane/metadata/android/ta-IN/images/icon.png 7005 bytes
wrote radio/fastlane/metadata/android/en-US/images/icon.png 4595 bytes
wrote radio/fastlane/metadata/android/ta-IN/images/icon.png 4595 bytes
wrote equalizer/fastlane/metadata/android/en-US/images/icon.png 4334 bytes
fonts: title='DejaVu-Sans-Bold' body='DejaVu-Sans'
wrote cleaner/fastlane/metadata/android/en-US/images/featureGraphic.png
wrote cleaner/fastlane/metadata/android/ta-IN/images/featureGraphic.png
wrote radio/fastlane/metadata/android/en-US/images/featureGraphic.png
wrote radio/fastlane/metadata/android/ta-IN/images/featureGraphic.png
wrote equalizer/fastlane/metadata/android/en-US/images/featureGraphic.png

cleaner/fastlane/metadata/android/en-US/images/featureGraphic.png        1024x500
cleaner/fastlane/metadata/android/ta-IN/images/featureGraphic.png        1024x500
equalizer/fastlane/metadata/android/en-US/images/featureGraphic.png      1024x500
radio/fastlane/metadata/android/en-US/images/featureGraphic.png          1024x500
radio/fastlane/metadata/android/ta-IN/images/featureGraphic.png          1024x500
```
