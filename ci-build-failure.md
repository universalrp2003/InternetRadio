# CI build failure excerpt

## Kotlin / resource errors
```
e: vocalremover/src/test/java/com/universalrp/vocalremover/audio/PcmWavToMp3EncoderTest.kt:63:17 Unresolved reference 'dropLast'.
e: vocalremover/src/test/java/com/universalrp/vocalremover/audio/PcmWavToMp3EncoderTest.kt:63:35 Cannot infer type for value parameter 'index'. Specify it explicitly.
```

## Gradle summary
```
[Incubating] Problems report is available at: file:///home/runner/work/InternetRadio/InternetRadio/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':vocalremover:compileDebugUnitTestKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.14.3/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 2m 29s
539 actionable tasks: 287 executed, 252 from cache
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
