# CI build failure excerpt

## Kotlin / resource errors
```
e: cleaner/src/main/java/com/universalrp/cleansweep/data/MobileNet.kt:342:34 Unresolved reference 'pci'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/MobileNet.kt:343:34 Unresolved reference 'tac'.
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/StatusPill.kt:91:38 Unresolved label.
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/StatusPill.kt:91:44 Unresolved reference 'post'.
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/StatusPill.kt:93:26 Unresolved label.
e: cleaner/src/main/java/com/universalrp/cleansweep/notify/StatusPill.kt:93:32 Unresolved reference 'post'.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/AboutScreen.kt:89:25 Unresolved reference 'Box'.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/AboutScreen.kt:98:29 @Composable invocations can only happen from the context of a @Composable function
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/AboutScreen.kt:100:55 @Composable invocations can only happen from the context of a @Composable function
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/Components.kt:328:22 Unresolved reference 'fillMaxWidth'.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/Components.kt:344:13 Unresolved reference 'Spacer'.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/Components.kt:347:17 Unresolved reference 'Spacer'.
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1791272958261.json

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

BUILD FAILED in 1m 14s
145 actionable tasks: 101 executed, 44 from cache
```
