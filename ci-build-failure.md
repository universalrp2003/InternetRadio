# CI build failure excerpt

## Kotlin / resource errors
```
```

## Gradle summary
```
```

## APK verification output
```
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
OK: the Tamil launcher label is inside the APK resources
OK: the Tamil name is in the CleanSweep dex (menu + About)
OK: the Tamil menu (daily brief) is inside the CleanSweep dex
OK: the home-screen widget layout is inside the APK
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
OK: setQuietHours is in the CleanSweep APK
OK: speakNow is in the CleanSweep APK
OK: offlineFallback is in the CleanSweep APK
OK: SystemEventWatcher is in the CleanSweep APK
OK: CleanSweepWidget is in the CleanSweep APK
OK: answerLanguage is in the CleanSweep APK
OK: setAnswerLanguage is in the CleanSweep APK
OK: setUnplugStartHour is in the CleanSweep APK
OK: ChargerWatchWorker is in the CleanSweep APK
OK: ChargeNotifier is in the CleanSweep APK
OK: canSearchWeb is in the CleanSweep APK
OK: setOnlineVoice is in the CleanSweep APK
OK: openScanner is in the CleanSweep APK
OK: CrashLog is in the CleanSweep APK
=== AppForge checks ===
OK: AppForge APK contains its code
OK: the Android project generator is present
=== PulseEQ checks ===
FAIL: PulseEQ must not request INTERNET
16:    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
17:    <uses-permission android:name="android.permission.WAKE_LOCK" />
18:    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
19:    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
20:    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
21:    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
22:    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
23:    <uses-permission android:name="android.permission.INTERNET" />
29:    <uses-permission android:name="com.universalrp.pulseeq.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
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
