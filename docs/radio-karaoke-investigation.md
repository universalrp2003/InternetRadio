# Ramesh Radio — karaoke investigation (not implemented)

Requested: selectable online and offline AI vocal separation of local songs,
with an instrumental output that can be played for singing or listening.
Neither option is included in the 1.6 APK. No audio is uploaded by this work.
A centre-cancellation switch is not an adequate replacement for this request.

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

## Before shipping online
Choose a documented service or separately deployed self-hosted worker. Define
HTTPS authentication, file limits, job status/cancellation, costs, retention and
delete behaviour. Ask for upload confirmation per file. Store keys in protected
app settings, not source code. Download output only from validated endpoints.
Do not invent a free endpoint or quietly upload a folder/library.

## Acceptance
Both modes return a playable instrumental file for a selected song. A disabled
or missing backend must not be labelled as a working "full voice cut". Tests
must include cancellation, network loss, mono/stereo input, silence, long songs,
unsupported formats and insufficient disk/memory.
