# CI build failure excerpt

## Kotlin / resource errors
```
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt:138:25 Unresolved reference 'IconButton'.
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt:138:67 @Composable invocations can only happen from the context of a @Composable function
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt:139:25 Unresolved reference 'IconButton'.
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt:139:74 @Composable invocations can only happen from the context of a @Composable function
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt:140:25 Unresolved reference 'IconButton'.
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt:140:63 @Composable invocations can only happen from the context of a @Composable function
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt:141:25 Unresolved reference 'IconButton'.
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt:141:77 @Composable invocations can only happen from the context of a @Composable function
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_6-1791440490402.json

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':radio:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 55s
81 actionable tasks: 38 executed, 43 from cache
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
