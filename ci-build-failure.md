# CI build failure excerpt

## Kotlin / resource errors
```
```

## Gradle summary
```
```

## APK verification output
```
total 16380
drwxr-xr-x 2 runner runner     4096 Oct  6 09:43 .
drwxr-xr-x 3 runner runner     4096 Oct  6 09:43 ..
-rw-r--r-- 1 runner runner 16759162 Oct  6 09:43 equalizer-debug.apk
-rw-r--r-- 1 runner runner      411 Oct  6 09:43 output-metadata.json
total 20012
drwxr-xr-x 2 runner runner     4096 Oct  6 09:44 .
drwxr-xr-x 3 runner runner     4096 Oct  6 09:44 ..
-rw-r--r-- 1 runner runner      411 Oct  6 09:44 output-metadata.json
-rw-r--r-- 1 runner runner 20478430 Oct  6 09:44 radio-debug.apk
=== Package info ===
package: name='com.universalrp.cleansweep' versionCode='9' versionName='2.4' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
package: name='com.universalrp.appforge' versionCode='1' versionName='1.0' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
package: name='com.universalrp.pulseeq' versionCode='1' versionName='1.0' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
package: name='com.universalrp.tamilnadufm' versionCode='1' versionName='1.1' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
cleaner label: application-label:'CleanSweep'
radio label: application-label:'Ramesh Radio'
OK: the built APK is labelled Ramesh Radio
CleanSweep merged manifest: cleaner/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
PulseEQ merged manifest: equalizer/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
Ramesh Radio merged manifest: radio/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
--- aapt2 permissions for PulseEQ ---
package: com.universalrp.pulseeq
uses-permission: name='android.permission.MODIFY_AUDIO_SETTINGS'
uses-permission: name='android.permission.WAKE_LOCK'
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.FOREGROUND_SERVICE'
uses-permission: name='android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'
uses-permission: name='android.permission.FOREGROUND_SERVICE_SPECIAL_USE'
uses-permission: name='android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS'
permission: com.universalrp.pulseeq.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='com.universalrp.pulseeq.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
=== CleanSweep v1.3 checks ===
OK: no Accessibility service in CleanSweep's manifest
OK: CacheCleanerService is gone
OK: the on-device assistant is in the APK
OK: legacy write permission declared (the Android 8/9/10 delete fix)
OK: Wi-Fi device-scan permissions declared
OK: INTERNET declared (used only for the optional AI analysis)
OK: the charging-status service is declared
OK: overlay permission present for the status-bar watt reading
OK: phone-state permission present for the Mobile & data screen
OK: locale config present (English + Tamil)
WARN: no values-ta folder found in the APK (launcher label stays English)
OK: DeviceHealthReader is in the CleanSweep APK
OK: BatteryReader is in the CleanSweep APK
OK: CpuReader is in the CleanSweep APK
OK: AppInventoryLoader is in the CleanSweep APK
OK: SecurityScanner is in the CleanSweep APK
OK: NetworkScanner is in the CleanSweep APK
OK: AiClient is in the CleanSweep APK
OK: AiReport is in the CleanSweep APK
OK: identifyDevice is in the CleanSweep APK
OK: ChargeMonitorService is in the CleanSweep APK
OK: batteryTimeLabel is in the CleanSweep APK
OK: StatusPill is in the CleanSweep APK
OK: goBack is in the CleanSweep APK
OK: defaultSelected is in the CleanSweep APK
OK: useAnotherFreeAi is in the CleanSweep APK
OK: ping is in the CleanSweep APK
OK: AiText is in the CleanSweep APK
OK: MobileNet is in the CleanSweep APK
OK: SpeedMeter is in the CleanSweep APK
OK: UsageStats is in the CleanSweep APK
OK: MobileScreen is in the CleanSweep APK
OK: ReadingMovePad is in the CleanSweep APK
OK: moveBy is in the CleanSweep APK
OK: applySaved is in the CleanSweep APK
OK: savedFor is in the CleanSweep APK
OK: setDraggable is in the CleanSweep APK
OK: togglePillDrag is in the CleanSweep APK
OK: setSpeedSizeMb is in the CleanSweep APK
OK: speedSizeMb is in the CleanSweep APK
OK: Announcer is in the CleanSweep APK
OK: HealthWatchWorker is in the CleanSweep APK
OK: VoiceScreen is in the CleanSweep APK
OK: speakAnswer is in the CleanSweep APK
OK: runDailyBriefNow is in the CleanSweep APK
OK: setVoiceEvent is in the CleanSweep APK
FAIL: quietHours is missing from the CleanSweep APK
```
