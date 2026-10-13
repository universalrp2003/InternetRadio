# Vocal Remover Studio — standalone prototype

This module is intentionally separate from Ramesh Radio while its native audio path and device behavior are being validated. CI builds and uploads only a clearly named **debug prototype APK**; it is excluded from release APKs and Google Play bundles.

## Current flow

1. Optionally tap **Download model** on the model card to fetch the 63.7 MiB UVR MDX-Net Voc FT vocal-focused model and watch download/checksum progress. Otherwise, the first processing job downloads it automatically. The verified model is installed in private app storage; failures show a retryable error. Upgrading from v0.1.4 downloads this larger model once; the retired 9482 weight is removed only after the new file passes its checksum.
2. Pick an MP3 with Android's document picker and choose Slow, Medium, or Fast.
3. Android's local MP3 decoder writes temporary float PCM to app-private storage. The sherpa-onnx source-separation C API processes bounded 12-second windows, with 1-second context and overlap. Completed chunks are fsynced and retained as resumable checkpoints.
4. The non-vocal/accompaniment stem is joined with a crossfade, written as a stereo 16-bit PCM WAV, and saved in `Music/Vocal Remover Studio`.
5. Preview the saved WAV inside the app with Play, Pause, Resume, and Stop controls. Playback stops when the app leaves the foreground, rather than handing playback to a separate player that can outlive this app.

The input song is copied for processing; the selected original is never modified. This app contains no API that uploads selected audio. Network access is for the model download only.

The Voc FT model is a vocal-focused alternative selected after user-file analysis showed the previous 9482 model barely changed one vocal track. It is a quality trial, not a promise of perfect silence: a dense mix can retain vocals, and stronger suppression can distort instruments or add artifacts. Verify results on-device before treating the change as successful.

## Long jobs and device safety

- A foreground service publishes progress and pause/resume/cancel controls; a partial wake lock is held only while the job is active and released while paused, finished, or when the service is destroyed.
- Inference checkpoints are model-version scoped. On a model update, old-version chunks are not mixed with new-model output; stale chunk and private WAV caches for that job are removed before inference resumes.
- A pause normally takes effect when the current inference window finishes (12 seconds of audio, not a wall-clock guarantee; native inference cannot be interrupted mid-call). The native model session is closed before waiting so its large allocations can be reclaimed. Completed chunks are stored atomically and reused after a process/service restart.
- Low-memory safety pauses automatically resume after Android reports at least 1.5 GiB available for 15 continuous seconds and other safety checks are clear; the user can also resume sooner. The service releases its CPU wake lock while paused. User, thermal, storage, and Android service-limit pauses remain manual; an OEM/process shutdown can still require the user to reopen and resume.
- Android's foreground-service time allowance can expire during very long background work. The service then records a resumable pause, drops its wake lock/foreground status, and keeps only the paused notification. Restart timing remains subject to Android's remaining service quota and OEM policy; completed checkpoints stay saved for a later resume.
- Slow uses one native inference thread; Medium uses up to two; Fast uses up to four. Android thermal status can throttle or pause, and low available RAM/storage can pause. The app does **not** use a fixed battery/CPU Celsius pause threshold.
- A user setting keeps the display awake only while the app is visible and a job or explicit model download is running. The visible foreground service and CPU wake lock handle background CPU work; neither can prevent the OS/OEM from terminating a process or the device from shutting down.
- No battery-optimization exemption is requested. Android does not let an app grant and then revoke that exemption temporarily.
- Thermal/RAM/CPU values are best effort. CPU temperature is available only on devices whose OEM exposes a clearly named CPU thermal zone. App CPU load is a process-time estimate and can exceed 100% across cores.

## Native runtime

The upstream `sherpa-onnx-1.13.8.aar` is JNI-focused and does **not** include the `libsherpa-onnx-c-api.so` required by this source-separation binding. The build therefore fetches the upstream sherpa-onnx Android FFI platform packages for arm64-v8a and armeabi-v7a from pub.dev, verifies each immutable archive SHA-256, extracts only its ABI-specific JNI libraries, and checks the ELF ABI before packaging. Model weights and runtime binaries are not committed.

## Build

The Android Gradle module is `:vocalremover`; it currently targets API 29+ for scoped MediaStore output and public Android thermal-status APIs. This is provisional: native library loading, API-level behavior, and device tests are still needed before calling it the lowest supported Android version.

```sh
./gradlew :vocalremover:testDebugUnitTest :vocalremover:assembleDebug
```

## Validation status

GitHub Actions has compiled the standalone module, run its configured unit tests, verified the APKs, and uploaded the debug prototype APK. This validates compilation and packaging, not end-to-end use. Model download on a phone, native ABI loading, separation quality, real-device thermal/memory behavior, pause/restart recovery, and minimum-OS compatibility still require device testing.

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for model/runtime attribution and licensing notes. The UVR README asks third-party applications to credit UVR and its developers; the model host does not provide a separate per-file license manifest, so commercial redistribution needs further legal confirmation.
