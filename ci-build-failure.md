# CI build failure excerpt

## Kotlin / resource errors
```
e: cleaner/src/main/java/com/universalrp/cleansweep/work/HealthWatchWorker.kt:184:28 Unresolved reference 'scan'.
e: cleaner/src/main/java/com/universalrp/cleansweep/work/HealthWatchWorker.kt:207:30 Unresolved reference 'totalBytes'.
e: cleaner/src/main/java/com/universalrp/cleansweep/work/HealthWatchWorker.kt:209:34 Unresolved reference 'totalBytes'.
e: cleaner/src/main/java/com/universalrp/cleansweep/work/HealthWatchWorker.kt:210:20 Unresolved reference 'totalBytes'.
e: cleaner/src/main/java/com/universalrp/cleansweep/work/HealthWatchWorker.kt:211:64 Unresolved reference 'totalBytes'.
e: cleaner/src/main/java/com/universalrp/cleansweep/work/HealthWatchWorker.kt:212:75 Unresolved reference 'totalBytes'.
e: cleaner/src/main/java/com/universalrp/cleansweep/work/HealthWatchWorker.kt:232:40 Unresolved reference 'totalCount'.
e: cleaner/src/main/java/com/universalrp/cleansweep/work/HealthWatchWorker.kt:232:58 Unresolved reference 'totalBytes'.
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1791279370984.json

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

BUILD FAILED in 1m 13s
107 actionable tasks: 72 executed, 35 from cache
```
