# CI build failure excerpt

## Kotlin / resource errors
```
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/ChargeNotifier.kt:237:24 Unresolved reference 'batteryTimeMinutes'.
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/ChargeNotifier.kt:239:30 Unresolved reference 'div' for operator '/'.
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/ChargeNotifier.kt:240:30 Unresolved reference 'rem' for operator '%'.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:792:22 Type 'MutableState<Int>' has no method 'setValue(Nothing?, KMutableProperty0<*>, ERROR CLASS: Ambiguity: getValue, [kotlin/getValue, kotlin/getValue, kotlin/getValue, kotlin/collections/getValue, kotlin/collections/getValue, kotlin/collections/getValue])', so it cannot serve as a delegate for var (read-write property).
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:792:22 Property delegate must have a 'getValue(Nothing?, KMutableProperty0<*>)' method. None of the following functions is applicable:
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/ChargeNotifier.kt:237:24 Unresolved reference 'batteryTimeMinutes'.
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/ChargeNotifier.kt:239:30 Unresolved reference 'div' for operator '/'.
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/ChargeNotifier.kt:240:30 Unresolved reference 'rem' for operator '%'.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:792:22 Type 'MutableState<Int>' has no method 'setValue(Nothing?, KMutableProperty0<*>, ERROR CLASS: Ambiguity: getValue, [kotlin/getValue, kotlin/getValue, kotlin/getValue, kotlin/collections/getValue, kotlin/collections/getValue, kotlin/collections/getValue])', so it cannot serve as a delegate for var (read-write property).
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:792:22 Property delegate must have a 'getValue(Nothing?, KMutableProperty0<*>)' method. None of the following functions is applicable:
```

## Gradle summary
```
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
