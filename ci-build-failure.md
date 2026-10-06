# CI build failure excerpt

## Kotlin / resource errors
```
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/AiScreens.kt:862:1 This annotation is not repeatable.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/AiScreens.kt:877:13 Functions which invoke @Composable functions must be marked with the @Composable annotation
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/AiScreens.kt:879:5 @Composable invocations can only happen from the context of a @Composable function
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1791280913435.json

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

BUILD FAILED in 1m 15s
107 actionable tasks: 72 executed, 35 from cache
```
