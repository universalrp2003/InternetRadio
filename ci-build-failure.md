# CI build failure excerpt

## Kotlin / resource errors
```
```

## Gradle summary
```
```

## APK verification output
```
android.permission.MODIFY_AUDIO_SETTINGS
android.permission.POST_NOTIFICATIONS
android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
android.permission.REQUEST_INSTALL_PACKAGES
android.permission.WAKE_LOCK
android:permission=
OK: the background equalizer service is declared
android:name="com.universalrp.pulseeq.service.EqService"
OK: PeakingBiquad is in the equalizer APK
OK: EqEngine is in the equalizer APK
OK: SessionEffectReceiver is in the equalizer APK
OK: Bands is in the equalizer APK
=== Ramesh Radio checks ===
OK: INTERNET permission present (streaming)
OK: foreground media playback permission declared
OK: local audio permission declared
OK: the Media3 playback service is declared
OK: no Accessibility service
Permissions in the merged manifest:
android.permission.ACCESS_NETWORK_STATE
android.permission.BLUETOOTH_PRIVILEGED
android.permission.DUMP
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
android.permission.FOREGROUND_SERVICE_SPECIAL_USE
android.permission.INTERNET
android.permission.MODIFY_AUDIO_SETTINGS
android.permission.POST_NOTIFICATIONS
android.permission.READ_EXTERNAL_STORAGE
android.permission.READ_MEDIA_AUDIO
android.permission.REQUEST_INSTALL_PACKAGES
android.permission.WAKE_LOCK
android:permission=
OK: the home-screen widget receiver is declared in the manifest
OK: the தமிழ் menu label is inside the radio APK
OK: the widget info is packaged in the APK
OK: PlaybackService is in the Ramesh Radio APK
OK: AudioFx is in the Ramesh Radio APK
OK: EqPresets is in the Ramesh Radio APK
OK: StationRepository is in the Ramesh Radio APK
OK: MainViewModel is in the Ramesh Radio APK
OK: DirectoryClient is in the Ramesh Radio APK
OK: RadioWidgetProvider is in the Ramesh Radio APK
OK: the station seed list is packaged (293805 bytes)
seed list OK: 507 stations (228 Tamil, 175 news, 481 verified)
OK: seed list parsed and contains Tamil + news stations
=== RP VOCAL REMOVER STUDIO checks (debug and signed release) ===
OK: Vocal Remover declares android.permission.INTERNET
OK: Vocal Remover declares android.permission.FOREGROUND_SERVICE
OK: Vocal Remover declares android.permission.FOREGROUND_SERVICE_DATA_SYNC
OK: Vocal Remover declares android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING
OK: Vocal Remover declares android.permission.WAKE_LOCK
OK: Vocal Remover declares android.permission.REQUEST_INSTALL_PACKAGES
OK: ProcessingQueue is present in vocal-remover-debug.dex
OK: ProcessingPolicy is present in vocal-remover-debug.dex
OK: ChunkedSeparationProcessor is present in vocal-remover-debug.dex
OK: WavPlaybackController is present in vocal-remover-debug.dex
OK: ModelRepository is present in vocal-remover-debug.dex
OK: DeviceMonitor is present in vocal-remover-debug.dex
OK: SeparationService is present in vocal-remover-debug.dex
OK: AppUpdates is present in vocal-remover-debug.dex
OK: AppLanguage is present in vocal-remover-debug.dex
OK: PcmWavToMp3Encoder is present in vocal-remover-debug.dex
OK: ProcessingQueue is present in vocal-remover-release.dex
OK: ProcessingPolicy is present in vocal-remover-release.dex
OK: ChunkedSeparationProcessor is present in vocal-remover-release.dex
OK: WavPlaybackController is present in vocal-remover-release.dex
OK: ModelRepository is present in vocal-remover-release.dex
OK: DeviceMonitor is present in vocal-remover-release.dex
OK: SeparationService is present in vocal-remover-release.dex
OK: AppUpdates is present in vocal-remover-release.dex
OK: AppLanguage is present in vocal-remover-release.dex
OK: PcmWavToMp3Encoder is present in vocal-remover-release.dex
OK: Vocal Remover ships standalone signed bytes, local-processing runtime, and verified updater
=== Store listing inventory (warnings) ===
cleaner: locales=[en-US ta-IN ] screenshots=16 icon=2 featureGraphic=2
equalizer: locales=[en-US ] screenshots=0 icon=1 featureGraphic=1
WARN: equalizer has fewer than 2 phone screenshots - the store page will show no gallery
radio: locales=[en-US ta-IN ] screenshots=8 icon=2 featureGraphic=2
ALL CHECKS PASSED
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

## Release publication step
```
::error::Dual-repository release is blocked. Configure the InternetRadio Actions secret RP_ANDROID_APPS_RELEASE_TOKEN with contents:write access to RP-Android-Apps; no release drafts have been created. Do not paste the secret into chat.
```
