# CI build failure excerpt

## Kotlin / resource errors
```
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:110:53 Unresolved reference 'absolutePath'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:222:27 Unresolved reference 'absolutePath'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:222:62 Unresolved reference 'absolutePath'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:223:50 Unresolved reference 'absolutePath'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:224:52 Unresolved reference 'absolutePath'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:226:42 Unresolved reference 'absolutePath'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:227:56 Unresolved reference 'absolutePath'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:227:81 Unresolved reference 'lastModified'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:228:28 Unresolved reference 'parent'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:228:36 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:228:36 Cannot infer type for type parameter 'R'. Specify it explicitly.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:228:40 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:228:60 Unresolved reference 'absolutePath'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/ScanEngine.kt:230:33 Unresolved reference 'parent'.
e: cleaner/src/main/java/com/universalrp/cleansweep/work/ChargerWatchWorker.kt:65:30 None of the following candidates is applicable:
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1791285608092.json

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

BUILD FAILED in 46s
106 actionable tasks: 71 executed, 35 from cache
```
