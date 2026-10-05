# CI build failure excerpt

## Kotlin / resource errors
```
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:139:18 None of the following candidates is applicable:
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:150:12 Unresolved reference 'enabled'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:206:16 'val' cannot be reassigned.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:207:16 Unresolved reference 'limiter'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:207:25 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:207:25 Cannot infer type for type parameter 'R'. Specify it explicitly.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:207:31 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:208:21 Unresolved reference 'isEnabled'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:209:21 Unresolved reference 'attackTime'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:210:21 Unresolved reference 'releaseTime'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:211:21 Unresolved reference 'ratio'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:212:21 Unresolved reference 'threshold'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:213:21 Unresolved reference 'postGain'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:217:45 Unresolved reference 'channelCount'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:220:21 Unresolved reference 'eq'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:220:25 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:220:25 Cannot infer type for type parameter 'R'. Specify it explicitly.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:220:31 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:221:20 Unresolved reference 'isEnabled'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:222:32 Unresolved reference 'bandCount'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:224:35 Unresolved reference 'getBandByBandIndex'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:234:36 Unresolved reference 'getBandByBandIndex'.
e: radio/src/main/java/com/universalrp/tamilnadufm/audio/AudioFx.kt:245:12 Unresolved reference 'setConfig'.
e: radio/src/main/java/com/universalrp/tamilnadufm/ui/AppRoot.kt:277:14 Unresolved reference 'weight'.
```

## Gradle summary
```
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1791204533012.json

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

BUILD FAILED in 49s
143 actionable tasks: 99 executed, 44 from cache
```
