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
total 19720
drwxr-xr-x 2 runner runner     4096 Oct  5 09:52 .
drwxr-xr-x 3 runner runner     4096 Oct  5 09:52 ..
-rw-r--r-- 1 runner runner 20177078 Oct  5 09:52 app-debug.apk
-rw-r--r-- 1 runner runner      407 Oct  5 09:52 output-metadata.json
total 17412
drwxr-xr-x 2 runner runner     4096 Oct  5 09:53 .
drwxr-xr-x 3 runner runner     4096 Oct  5 09:53 ..
-rw-r--r-- 1 runner runner 17814843 Oct  5 09:53 cleaner-debug.apk
-rw-r--r-- 1 runner runner      412 Oct  5 09:53 output-metadata.json
total 16380
drwxr-xr-x 2 runner runner     4096 Oct  5 09:53 .
drwxr-xr-x 3 runner runner     4096 Oct  5 09:53 ..
-rw-r--r-- 1 runner runner 16757715 Oct  5 09:53 builder-debug.apk
-rw-r--r-- 1 runner runner      410 Oct  5 09:53 output-metadata.json
total 16380
drwxr-xr-x 2 runner runner     4096 Oct  5 09:53 .
drwxr-xr-x 3 runner runner     4096 Oct  5 09:53 ..
-rw-r--r-- 1 runner runner 16759162 Oct  5 09:53 equalizer-debug.apk
-rw-r--r-- 1 runner runner      411 Oct  5 09:53 output-metadata.json
=== Package info ===
package: name='com.universalrp.cleansweep' versionCode='4' versionName='1.3' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
package: name='com.universalrp.appforge' versionCode='1' versionName='1.0' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
package: name='com.universalrp.pulseeq' versionCode='1' versionName='1.0' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
=== CleanSweep v1.3 checks ===
OK: no Accessibility service in CleanSweep's manifest
OK: CacheCleanerService is gone
OK: the on-device assistant is in the APK
=== AppForge checks ===
OK: AppForge APK contains its code
OK: the Android project generator is present
=== PulseEQ checks ===
PulseEQ permissions:  
OK: PulseEQ has no INTERNET permission (fully offline)
FAIL: EqService is missing from the equalizer manifest
```
