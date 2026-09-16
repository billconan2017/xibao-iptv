# Audio decoder source and rebuild

Media3 FFmpeg bridge: AndroidX Media 1.11.0, commit 2bc207851df311340767e913931ca7b28cab1794 (Apache 2.0).
FFmpeg: n6.1.4, commit 34277e12e80031c7f89494ba543684bc1dd0be8f (LGPL 2.1 or later).
Both license texts are bundled in the APK assets/licenses folder.

On Linux install Android NDK 28.2.13676358, CMake, Ninja and make, then run:

    ANDROID_NDK_HOME=/path/to/ndk bash scripts/build-audio-native.sh

Alternatively run the Build audio decoders workflow and extract its audio-native-libs artifact into android/decoder-ffmpeg/src/main/jniLibs before building the APK. The script fetches the exact FFmpeg source revision and builds only audio decoders; video remains hardware decoded. The generated JNI library supports 16 KB pages and includes ARM32, ARM64, x86 and x86_64.

The upstream build script is modified only to enable position-independent code. No GPL or nonfree components are enabled. Users can rebuild the library and the complete APK from this repository, replace the decoder, and sign/install their own build. Native outputs are not committed to Git.
