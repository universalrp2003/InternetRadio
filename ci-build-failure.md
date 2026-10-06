# CI build failure excerpt

## Kotlin / resource errors
```
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/Components.kt:33:38 Conflicting import: imported name 'FontWeight' is ambiguous.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/Components.kt:55:38 Conflicting import: imported name 'FontWeight' is ambiguous.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:20:42 Conflicting import: imported name 'RoundedCornerShape' is ambiguous.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:53:42 Conflicting import: imported name 'RoundedCornerShape' is ambiguous.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/PhoneScreens.kt:633:104 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'Boolean?'.
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1791263630784.json

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':cleaner:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 59s
145 actionable tasks: 101 executed, 44 from cache
```
