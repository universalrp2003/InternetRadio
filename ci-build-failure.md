# CI build failure excerpt

## Kotlin / resource errors
```
e: equalizer/src/main/java/com/universalrp/pulseeq/EqViewModel.kt:256:41 Return type mismatch: expected 'Boolean', actual 'Any'.
e: equalizer/src/main/java/com/universalrp/pulseeq/EqViewModel.kt:258:9 Syntax error: Unexpected token.
e: equalizer/src/main/java/com/universalrp/pulseeq/EqViewModel.kt:258:10 Unresolved reference 'pm'.
e: equalizer/src/main/java/com/universalrp/pulseeq/EqViewModel.kt:258:12 Syntax error: Expecting an element.
e: equalizer/src/main/java/com/universalrp/pulseeq/service/EqService.kt:159:43 None of the following candidates is applicable:
e: equalizer/src/main/java/com/universalrp/pulseeq/service/EqService.kt:163:19 Unresolved reference 'build'.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/AboutScreen.kt:152:55 No value passed for parameter 'onClick'.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/AboutScreen.kt:152:68 Argument type mismatch: actual type is 'Function0<Unit>', but 'Modifier' was expected.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/AboutScreen.kt:153:66 No value passed for parameter 'onClick'.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/AboutScreen.kt:153:81 Argument type mismatch: actual type is 'Function0<Unit>', but 'Modifier' was expected.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/EqualizerScreen.kt:382:47 No value passed for parameter 'onClick'.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/EqualizerScreen.kt:382:63 Argument type mismatch: actual type is 'Function0<Unit>', but 'Modifier' was expected.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/EqualizerScreen.kt:383:47 No value passed for parameter 'onClick'.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/EqualizerScreen.kt:383:64 Argument type mismatch: actual type is 'Function0<Unit>', but 'Modifier' was expected.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/EqualizerScreen.kt:384:63 No value passed for parameter 'onClick'.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/EqualizerScreen.kt:384:78 Argument type mismatch: actual type is 'Function0<Unit>', but 'Modifier' was expected.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/PlayerScreen.kt:209:56 No value passed for parameter 'onClick'.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/PlayerScreen.kt:209:72 Argument type mismatch: actual type is 'Function0<Unit>', but 'Modifier' was expected.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/SettingsScreen.kt:157:29 No value passed for parameter 'onClick'.
e: equalizer/src/main/java/com/universalrp/pulseeq/ui/SettingsScreen.kt:158:27 Argument type mismatch: actual type is 'Function0<Unit>', but 'Modifier' was expected.
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1791193430630.json

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':equalizer:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 55s
127 actionable tasks: 85 executed, 42 from cache
```
