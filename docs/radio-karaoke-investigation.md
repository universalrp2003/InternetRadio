# Ramesh Radio — karaoke investigation (not implemented)

Original investigation request: selectable online and offline AI separation of local songs.
The owner later clarified that selected audio must never be uploaded; only the local/offline
path is in scope. No audio is uploaded by this work. A centre-cancellation switch is not
an adequate replacement for the requested vocal-removal model.

## Candidates inspected
- https://github.com/k2-fsa/sherpa-onnx — source-separation C/C++ API, with UVR
  and Spleeter examples. The inspected Android/Kotlin examples did not expose a
  ready-to-integrate separation screen or Kotlin wrapper. Native ABI integration,
  decoding, model redistribution rights and memory limits require evaluation.
- https://github.com/k2-fsa/sherpa-onnx/blob/master/c-api-examples/source-separation-uvr-c-api.c
  documents an ONNX model and multichannel audio processing API.
- https://github.com/facebookresearch/demucs — repository reports archived;
  MIT code does not by itself establish every model's redistribution rights.
- https://github.com/Anjok07/ultimatevocalremovergui — desktop reference;
  not a drop-in Android dependency.

## Before shipping offline
Pin model and runtime versions, check model license, publish checksum/size,
require an explicit download over the user's chosen connection, process in
bounded chunks, expose progress/cancellation, preserve the original song, check
available storage, handle low memory and thermal interruptions. Benchmark a full
song on the user's class of phone. Compare vocals/instruments with reference
outputs; report residual vocals honestly. No real-time-radio guarantee.

## Upload/online path — out of scope
The owner does not want selected audio uploaded. Do not add an online separation
backend or send audio/PCM to any service. Network access is limited to application
and model delivery.

## Acceptance
A local MP3 produces a separate, playable instrumental WAV while leaving the
source untouched. Tests must include pause/resume/restart, cancellation, model
checksum failure, mono/stereo input, silence, long songs, unsupported formats,
WAV validity, and insufficient disk/memory. Do not describe the prototype as
verified until Android builds and device tests pass.

## Owner-confirmed standalone prototype requirements (2026-10-12)

- Build a separate proof-of-concept first; only merge into `:radio` after the
  on-device output is judged useful. The minimum Android version is **not fixed
  at API 26**; set it to the lowest version the chosen native runtime supports
  and that passes processing/resume tests.
- Input: local MP3 selected with Android's picker. Preserve the source. First
  output: the instrumental stem as WAV. Internet may be used to fetch the model,
  but selected audio/PCM must never be uploaded or included in a request.
- Main screen offers **Slow / Medium / Fast** resource profiles. They control
  bounded CPU parallelism/pacing, not claimed separation quality; thermal and
  memory safety overrides every profile.
- Decode and process in bounded, overlapped chunks. Persist checkpoints and
  intermediate data; Pause takes effect at a safe chunk boundary, Resume starts
  at the last completed checkpoint (including after a process restart). Write a
  temporary output and publish the final WAV only after it is complete.
- Use a visible, user-started foreground processing service and notification.
  Any app-owned partial wake lock and optional keep-screen-on flag exist only
  while processing is active; release them on pause, completion, cancellation,
  failure and service teardown. Do not silently exempt the app from battery
  optimization. Android's exemption is not temporary and cannot be revoked by
  the app at job completion; only offer settings guidance if a tested OEM kill
  makes it necessary, with that persistence explained.
- Report system available RAM and app memory as estimates, battery temperature
  with its sensor label, Android thermal status where available, and CPU
  temperature only when a readable/identifiable sensor exists. Missing data is
  shown as unavailable, never as zero or a fabricated device temperature.
- Slow or pause at safe boundaries under serious thermal pressure, low memory,
  or insufficient storage. An absolute no-crash/always-finish guarantee is not
  possible under force-stop, power loss, OS termination or hardware shutdown;
  checkpoint/recovery is the mitigation, not a guarantee.
- Provide a user-controlled Keep screen awake option while the processing screen
  is visible. This is separate from the partial wake lock needed for background
  CPU work and must be released automatically when the job stops.

## Implementation gate and current status (2026-10-12)

A separate `:vocalremover` prototype now contains a Compose screen, foreground
service, local MP3 decoding, an offline sherpa-onnx UVR C-API binding, 12-second
checkpoint chunks with overlap/crossfade, and WAV export. The UI has Slow / Medium
/ Fast profiles, progress, safe-boundary pause/resume/cancel, and best-effort
health readings. The original stays unchanged and there is no audio-upload path.

The Android module currently targets API 29 to use scoped MediaStore output and
public Android thermal-status APIs. This is a provisional engineering choice,
not a verified lowest supported Android version. The ordinary sherpa-onnx Android
AAR is JNI-focused and does not include `libsherpa-onnx-c-api.so`; the build now
uses the official sherpa-onnx 1.13.8 Android FFI packages for arm64-v8a and
armeabi-v7a instead. Gradle pins both pub.dev archive SHA-256 values, checks for
the C/C++ API and ONNX Runtime libraries, and validates each ELF ABI before
packaging. UVR model and runtime archive hashes and attribution are documented
in `vocalremover/THIRD_PARTY_NOTICES.md`. The model host does not provide a
separate per-file license manifest; do not claim the weights are licensed by the
runtime, and confirm model terms before commercial redistribution.

## Current publication and verification status (2026-10-14)

RP Vocal Remover Studio remains a **standalone app** and is now included in the
release workflow as its own signed, versioned GitHub APK. It is not bundled into
Ramesh Radio and is not part of the Play Store AAB set. Publication is gated on
module tests, signed-APK/package/version/digest checks, and the normal Android
installer confirmation. The old published APK and tag remain unchanged.

Reliability tests now cover durable-chunk restart recovery, recovery from a lost
picker grant when an app-private source copy exists, detection that a missing
private copy requires reopening the source, and WAV export cancellation/temporary-file cleanup. A pure-JVM seam
lets the restart test confirm a committed checkpoint is stitched without loading
the native model. These tests are source additions only: this workspace has no
JDK/Android SDK, so no Gradle build or JVM test has run here. Do not represent
this candidate as built, verified, or released until CI passes and output/runtime
behavior is confirmed. Keep the app standalone; merge into `:radio` only after
the owner judges the output useful.
