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
drwxr-xr-x 2 runner runner     4096 Oct 10 14:18 .
drwxr-xr-x 4 runner runner     4096 Oct 10 14:18 ..
-rw-r--r-- 1 runner runner 20191080 Oct 10 14:18 app-debug.apk
-rw-r--r-- 1 runner runner      411 Oct 10 14:18 output-metadata.json
total 18880
drwxr-xr-x 2 runner runner     4096 Oct 10 14:20 .
drwxr-xr-x 4 runner runner     4096 Oct 10 14:20 ..
-rw-r--r-- 1 runner runner 19318003 Oct 10 14:20 cleaner-debug.apk
-rw-r--r-- 1 runner runner      414 Oct 10 14:20 output-metadata.json
total 16380
drwxr-xr-x 2 runner runner     4096 Oct 10 14:18 .
drwxr-xr-x 4 runner runner     4096 Oct 10 14:18 ..
-rw-r--r-- 1 runner runner 16757715 Oct 10 14:18 builder-debug.apk
-rw-r--r-- 1 runner runner      410 Oct 10 14:18 output-metadata.json
total 16412
drwxr-xr-x 2 runner runner     4096 Oct 10 14:18 .
drwxr-xr-x 4 runner runner     4096 Oct 10 14:18 ..
-rw-r--r-- 1 runner runner 16792008 Oct 10 14:18 equalizer-debug.apk
-rw-r--r-- 1 runner runner      411 Oct 10 14:18 output-metadata.json
total 20076
drwxr-xr-x 2 runner runner     4096 Oct 10 14:19 .
drwxr-xr-x 4 runner runner     4096 Oct 10 14:20 ..
-rw-r--r-- 1 runner runner      411 Oct 10 14:19 output-metadata.json
-rw-r--r-- 1 runner runner 20543698 Oct 10 14:19 radio-debug.apk
=== Google Play bundles (.aab) ===
OK: Internet-Radio-v1.1.aab inside the build
OK: CleanSweep-v2.34.aab inside the build
OK: AppForge-v1.1.aab inside the build
OK: PulseEQ-v1.2.aab inside the build
OK: Ramesh-Radio-v1.8.aab inside the build
=== release APKs (what the stores take) ===
OK: app/build/outputs/apk/release/app-release.apk
OK: cleaner/build/outputs/apk/release/cleaner-release.apk
OK: builder/build/outputs/apk/release/builder-release.apk
OK: equalizer/build/outputs/apk/release/equalizer-release.apk
OK: radio/build/outputs/apk/release/radio-release.apk
=== App icons inside the built APKs ===
OK: cleaner/build/outputs/apk/release/cleaner-release.apk icon=res/BW.xml (4 density entries)
OK: radio/build/outputs/apk/release/radio-release.apk icon=res/BW.xml (4 density entries)
OK: app/build/outputs/apk/release/app-release.apk icon=res/BW.xml (6 density entries)
OK: builder/build/outputs/apk/release/builder-release.apk icon=res/BW.xml (4 density entries)
OK: equalizer/build/outputs/apk/release/equalizer-release.apk icon=res/BW.xml (4 density entries)
OK: no module borrows a platform icon any more
=== Package info ===
package: name='com.universalrp.cleansweep' versionCode='39' versionName='2.34' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
package: name='com.universalrp.appforge' versionCode='2' versionName='1.1' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
package: name='com.universalrp.pulseeq' versionCode='3' versionName='1.2' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
package: name='com.universalrp.tamilnadufm' versionCode='8' versionName='1.8' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
cleaner label: application-label:'Live Guard'
radio label: application-label:'Ramesh Radio'
OK: the built APK is labelled Ramesh Radio
OK: no release APK carries the debuggable flag
Certificate stored in file </tmp/tmp.LNamQY77Oe/project-certificate.der>
V2 Signer: certificate DN: CN=CleanSweep CI, OU=Dev, O=universalrp2003, C=IN
V2 Signer: certificate SHA-256 digest: 9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55
V2 Signer: certificate SHA-1 digest: 7e80a550418d4c8a4d6cf1b2c1a8c9f0abc2da16
V2 Signer: certificate MD5 digest: 42195014bb3ee70823bc255eaf65baa8
OK: app/build/outputs/apk/release/app-release.apk signature verified with the existing project key
V2 Signer: certificate DN: CN=CleanSweep CI, OU=Dev, O=universalrp2003, C=IN
V2 Signer: certificate SHA-256 digest: 9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55
V2 Signer: certificate SHA-1 digest: 7e80a550418d4c8a4d6cf1b2c1a8c9f0abc2da16
V2 Signer: certificate MD5 digest: 42195014bb3ee70823bc255eaf65baa8
OK: cleaner/build/outputs/apk/release/cleaner-release.apk signature verified with the existing project key
V2 Signer: certificate DN: CN=CleanSweep CI, OU=Dev, O=universalrp2003, C=IN
V2 Signer: certificate SHA-256 digest: 9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55
V2 Signer: certificate SHA-1 digest: 7e80a550418d4c8a4d6cf1b2c1a8c9f0abc2da16
V2 Signer: certificate MD5 digest: 42195014bb3ee70823bc255eaf65baa8
OK: builder/build/outputs/apk/release/builder-release.apk signature verified with the existing project key
V2 Signer: certificate DN: CN=CleanSweep CI, OU=Dev, O=universalrp2003, C=IN
V2 Signer: certificate SHA-256 digest: 9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55
V2 Signer: certificate SHA-1 digest: 7e80a550418d4c8a4d6cf1b2c1a8c9f0abc2da16
V2 Signer: certificate MD5 digest: 42195014bb3ee70823bc255eaf65baa8
OK: equalizer/build/outputs/apk/release/equalizer-release.apk signature verified with the existing project key
V2 Signer: certificate DN: CN=CleanSweep CI, OU=Dev, O=universalrp2003, C=IN
V2 Signer: certificate SHA-256 digest: 9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55
V2 Signer: certificate SHA-1 digest: 7e80a550418d4c8a4d6cf1b2c1a8c9f0abc2da16
V2 Signer: certificate MD5 digest: 42195014bb3ee70823bc255eaf65baa8
OK: radio/build/outputs/apk/release/radio-release.apk signature verified with the existing project key
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
