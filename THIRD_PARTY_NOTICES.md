# Third-party notices

This project's source code is licensed under GPL-3.0-or-later.

Direct dependencies include:

| Component | License | Source |
| --- | --- | --- |
| NewPipe Extractor v0.26.5 | GPL-3.0-or-later | https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5 |
| AndroidX / Compose / Media3 / DataStore / CameraX | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin / kotlinx.coroutines | Apache-2.0 | https://github.com/JetBrains/kotlin |
| OkHttp | Apache-2.0 | https://github.com/square/okhttp |
| Gson | Apache-2.0 | https://github.com/google/gson |
| Coil | Apache-2.0 | https://github.com/coil-kt/coil |
| Google ML Kit Face Detection 16.1.7 (bundled model) | ML Kit terms | https://developers.google.com/ml-kit/terms |

NewPipe Extractor's transitive dependencies include nanojson (MIT), jsoup (MIT), Rhino (MPL-2.0), protobuf (BSD-3-Clause), and JSR-305 annotations (Apache-2.0). Other transitive AndroidX dependencies include Guava (Apache-2.0). Original notices carried by dependency artifacts remain in the APK where packaged. See each upstream project for its complete license and copyright notices. The full GPL text is in `LICENSE`.

YouTube is a trademark of Google LLC. This app is not affiliated with Google or Team NewPipe.

The Android test-only face fixture `app/src/androidTest/assets/face-presence.jpg` comes from TensorFlow's public Grace Hopper example image: https://storage.googleapis.com/download.tensorflow.org/example_images/grace_hopper.jpg . It is not packaged in the production APK and is used only to verify face presence detection.
