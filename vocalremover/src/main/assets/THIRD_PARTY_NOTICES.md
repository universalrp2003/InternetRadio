Vocal Remover Studio — third-party notices

UVR MDX-Net Voc FT model
- Model: UVR-MDX-NET-Voc_FT.onnx
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/source-separation-models
- SHA-256: e411182ce2c53541fefdcc99a8f46f2fe03978eb22038b9497c1d9d95a00fad4
- Attribution: UVR (Anjok07 and aufr33), UVR MDX-Net Voc FT.
- The official UVR README says the UVR GUI code is MIT-licensed and asks third-party apps using UVR's models to credit UVR and its developers. The model-hosting repositories do not publish a separate per-file license manifest for these weights. This prototype provides attribution and does not represent the weights as covered by sherpa-onnx's license. Review/confirm model terms before commercial redistribution.
- UVR license/attribution notice: https://github.com/Anjok07/ultimatevocalremovergui#license

sherpa-onnx Android FFI runtime
- Version 1.13.8, from the official sherpa-onnx Flutter platform packages on pub.dev.
- arm64 package archive SHA-256: 29db5572735afb1bc29f0f95ab160f7fb38b4b3f926f48e9113457fd740264f4
- armeabi-v7a package archive SHA-256: fe81837bd12f67d136a456c6d2b39876caaba09edd77761cb527cb170dac2d2a
- License: Apache License 2.0. Project: https://github.com/k2-fsa/sherpa-onnx
- The standard upstream Android AAR is JNI-focused and lacks the C API library required for source separation; this build uses the FFI native packages instead.
- Bundled ONNX Runtime components are MIT-licensed: https://github.com/microsoft/onnxruntime/blob/main/LICENSE

JNA Android runtime
- Version 5.14.0. License: dual-licensed under LGPL 2.1 or later or Apache License 2.0.
- Project: https://github.com/java-native-access/jna

AndroidX, Kotlin and Jetpack Compose are provided under their upstream Apache License 2.0 notices.

Jump3r Java LAME MP3 encoder
- Project: https://github.com/nwaldispuehl/java-lame
- Version: 3.98.4 Java port; source pinned from commit `0383c5d82e5f3d0acab347498743a74e9e69d256`.
- License: GNU Lesser General Public License version 2.0 or later (per the source-file headers). The upstream repository bundles its LGPL v3 license text, included as `JUMP3R-LGPL-3.0.txt`; encoder sources are included under `net.sourceforge.lame`.
- Used locally to create a separate 320 kb/s CBR MP3. MP3 is lossy; this high bitrate does not make WAV-to-MP3 conversion lossless.

Privacy: selected audio is processed on-device and is never sent by this app. Internet is only used for the user-requested, checksum-pinned separation model download. Runtime and encoder code are packaged with the app; no song audio is uploaded.
