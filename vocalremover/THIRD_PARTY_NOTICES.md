# Vocal Remover Studio — third-party notices

This standalone prototype downloads its source-separation model only after the user chooses to process a song. The model is kept in private app storage. The app does not upload selected audio.

## UVR MDX-Net Voc FT model

- Model: `UVR-MDX-NET-Voc_FT.onnx`
- Model source used by this prototype: [sherpa-onnx source-separation-models release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/source-separation-models)
- SHA-256: `e411182ce2c53541fefdcc99a8f46f2fe03978eb22038b9497c1d9d95a00fad4`
- Attribution: **UVR (Anjok07 and aufr33), UVR MDX-Net Voc FT**.
- The [official UVR README license section](https://github.com/Anjok07/ultimatevocalremovergui#license) says the UVR GUI code is MIT-licensed and asks third-party applications that use UVR's models to credit UVR and its developers. The model-hosting repositories do not publish a separate per-file license manifest for this weight. This prototype displays the requested attribution and links to the upstream notice; it does not represent the model weights as covered by sherpa-onnx's license. Obtain suitable legal review/confirmation before commercial redistribution.

## sherpa-onnx Android FFI runtime

- Project: [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
- Runtime version: `1.13.8`; Android ABI packages are fetched from [pub.dev](https://pub.dev/packages/sherpa_onnx) during the Gradle build.
- `sherpa_onnx_android_arm64-1.13.8.tar.gz` SHA-256: `29db5572735afb1bc29f0f95ab160f7fb38b4b3f926f48e9113457fd740264f4`
- `sherpa_onnx_android_armeabi-1.13.8.tar.gz` SHA-256: `fe81837bd12f67d136a456c6d2b39876caaba09edd77761cb527cb170dac2d2a`
- License: Apache License 2.0; see the upstream sherpa-onnx project for the complete license and notices.
- The ordinary upstream Android AAR is not used: it is JNI-focused and does not include the C API shared library required by this app's source-separation binding.
- The runtime includes ONNX Runtime native components. ONNX Runtime is MIT-licensed; see [Microsoft ONNX Runtime notices](https://github.com/microsoft/onnxruntime/blob/main/LICENSE).

## JNA

- Project: [java-native-access/jna](https://github.com/java-native-access/jna)
- Version: `5.14.0` Android AAR.
- License: dual-licensed under LGPL 2.1 or later and the Apache License 2.0; see the upstream distribution for the full texts.

## Jump3r Java LAME MP3 encoder

- Project: [nwaldispuehl/java-lame](https://github.com/nwaldispuehl/java-lame)
- Version: 3.98.4 Java port, source pinned from commit `0383c5d82e5f3d0acab347498743a74e9e69d256`.
- License: GNU Lesser General Public License version 2.0 or later (per the source-file headers). The upstream repository bundles its LGPL v3 license text, included here at `src/main/assets/JUMP3R-LGPL-3.0.txt`; the encoder sources are included in `src/main/java/net/sourceforge/lame`.
- It is used locally to create a separate 320 kb/s constant-bit-rate MP3. MP3 is inherently lossy; high bitrate reduces but does not eliminate re-encoding loss.

## AndroidX, Kotlin and Compose

AndroidX, Kotlin, and Jetpack Compose components are distributed under their respective upstream Apache License 2.0 notices.
