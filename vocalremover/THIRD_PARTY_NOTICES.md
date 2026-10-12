# Vocal Remover Studio — third-party notices

This standalone prototype downloads its source-separation model only after the user chooses to process a song. The model is kept in private app storage. The app does not upload selected audio.

## UVR MDX-Net 9482 model

- Model: `UVR_MDXNET_9482.onnx`
- Model source used by this prototype: [sherpa-onnx source-separation-models release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/source-separation-models)
- SHA-256: `9d78f8566fa8198065214ab628be1de966a500c57786695aa4b13e2b27a7727d`
- Attribution: **UVR (Anjok07 and aufr33), UVR MDX-Net 9482**.
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

## AndroidX, Kotlin and Compose

AndroidX, Kotlin, and Jetpack Compose components are distributed under their respective upstream Apache License 2.0 notices.
