# CI build failure excerpt

## Kotlin / resource errors
```
e: cleaner/src/main/java/com/universalrp/cleansweep/data/AppInventory.kt:268:35 Unresolved reference 'OPSTR_REQUEST_INSTALL_PACKAGES'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/AppInventory.kt:272:53 Unresolved reference 'OPSTR_REQUEST_INSTALL_PACKAGES'.
e: cleaner/src/main/java/com/universalrp/cleansweep/data/SecurityScanner.kt:94:38 Unresolved reference 'ENABLED_NOTIFICATION_LISTENERS'.
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:198:20 Overload resolution ambiguity between candidates:
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:329:9 Conflicting overloads:
e: cleaner/src/main/java/com/universalrp/cleansweep/ui/HomeScreen.kt:395:9 Conflicting overloads:
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1791255157359.json

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

BUILD FAILED in 1m 2s
143 actionable tasks: 99 executed, 44 from cache
```
