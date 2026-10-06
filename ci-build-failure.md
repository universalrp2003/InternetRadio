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
total 19732
drwxr-xr-x 2 runner runner     4096 Oct  6 15:51 .
drwxr-xr-x 4 runner runner     4096 Oct  6 15:54 ..
-rw-r--r-- 1 runner runner 20191060 Oct  6 15:51 app-debug.apk
-rw-r--r-- 1 runner runner      411 Oct  6 15:51 output-metadata.json
total 18524
drwxr-xr-x 2 runner runner     4096 Oct  6 15:53 .
drwxr-xr-x 4 runner runner     4096 Oct  6 15:56 ..
-rw-r--r-- 1 runner runner 18954244 Oct  6 15:53 cleaner-debug.apk
-rw-r--r-- 1 runner runner      413 Oct  6 15:53 output-metadata.json
total 16380
drwxr-xr-x 2 runner runner     4096 Oct  6 15:51 .
drwxr-xr-x 4 runner runner     4096 Oct  6 15:55 ..
-rw-r--r-- 1 runner runner 16757715 Oct  6 15:51 builder-debug.apk
-rw-r--r-- 1 runner runner      410 Oct  6 15:51 output-metadata.json
total 16380
drwxr-xr-x 2 runner runner     4096 Oct  6 15:51 .
drwxr-xr-x 4 runner runner     4096 Oct  6 15:55 ..
-rw-r--r-- 1 runner runner 16759162 Oct  6 15:51 equalizer-debug.apk
-rw-r--r-- 1 runner runner      411 Oct  6 15:51 output-metadata.json
total 20012
drwxr-xr-x 2 runner runner     4096 Oct  6 15:54 .
drwxr-xr-x 4 runner runner     4096 Oct  6 15:56 ..
-rw-r--r-- 1 runner runner      411 Oct  6 15:54 output-metadata.json
-rw-r--r-- 1 runner runner 20478430 Oct  6 15:54 radio-debug.apk
=== Google Play bundles (.aab) ===
OK: Internet-Radio-v1.0.aab inside the build
OK: CleanSweep-v2.6.aab inside the build
OK: AppForge-v1.0.aab inside the build
OK: PulseEQ-v1.0.aab inside the build
OK: Ramesh-Radio-v1.2.aab inside the build
=== release APKs (what the stores take) ===
OK: app/build/outputs/apk/release/app-release.apk
OK: cleaner/build/outputs/apk/release/cleaner-release.apk
OK: builder/build/outputs/apk/release/builder-release.apk
OK: equalizer/build/outputs/apk/release/equalizer-release.apk
OK: radio/build/outputs/apk/release/radio-release.apk
=== App icons inside the built APKs ===
FAIL: cleaner/build/outputs/apk/release/cleaner-release.apk carries no res/.../ic_launcher - a store would reject the listing
```

## Store artwork step
```
wrote cleaner/fastlane/metadata/android/en-US/images/icon.png 7005 bytes
wrote cleaner/fastlane/metadata/android/ta-IN/images/icon.png 7005 bytes
wrote radio/fastlane/metadata/android/en-US/images/icon.png 4595 bytes
wrote radio/fastlane/metadata/android/ta-IN/images/icon.png 4595 bytes
wrote app/fastlane/metadata/android/en-US/images/icon.png 8744 bytes
wrote app/src/main/res/mipmap-mdpi/ic_launcher.png 1015 bytes
wrote app/src/main/res/mipmap-hdpi/ic_launcher.png 1318 bytes
wrote app/src/main/res/mipmap-xhdpi/ic_launcher.png 1988 bytes
wrote app/src/main/res/mipmap-xxhdpi/ic_launcher.png 2718 bytes
wrote app/src/main/res/mipmap-xxxhdpi/ic_launcher.png 3957 bytes
wrote builder/fastlane/metadata/android/en-US/images/icon.png 3729 bytes
wrote equalizer/fastlane/metadata/android/en-US/images/icon.png 4334 bytes
fonts: title='DejaVu-Sans-Bold' body='DejaVu-Sans'
wrote cleaner/fastlane/metadata/android/en-US/images/featureGraphic.png
wrote cleaner/fastlane/metadata/android/ta-IN/images/featureGraphic.png
wrote radio/fastlane/metadata/android/en-US/images/featureGraphic.png
wrote radio/fastlane/metadata/android/ta-IN/images/featureGraphic.png
wrote app/fastlane/metadata/android/en-US/images/featureGraphic.png
wrote builder/fastlane/metadata/android/en-US/images/featureGraphic.png
wrote equalizer/fastlane/metadata/android/en-US/images/featureGraphic.png

app/fastlane/metadata/android/en-US/images/featureGraphic.png            1024x500
builder/fastlane/metadata/android/en-US/images/featureGraphic.png        1024x500
cleaner/fastlane/metadata/android/en-US/images/featureGraphic.png        1024x500
cleaner/fastlane/metadata/android/ta-IN/images/featureGraphic.png        1024x500
equalizer/fastlane/metadata/android/en-US/images/featureGraphic.png      1024x500
radio/fastlane/metadata/android/en-US/images/featureGraphic.png          1024x500
radio/fastlane/metadata/android/ta-IN/images/featureGraphic.png          1024x500
```
