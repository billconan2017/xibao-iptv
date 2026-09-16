#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="$ROOT/android/decoder-ffmpeg/src/main"
NDK="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK r28 or newer}"
FFMPEG_COMMIT=34277e12e80031c7f89494ba543684bc1dd0be8f
mkdir -p "$MODULE/jni/ffmpeg" "$MODULE/jniLibs"
curl --fail --location --retry 3 "https://codeload.github.com/FFmpeg/FFmpeg/tar.gz/$FFMPEG_COMMIT" | tar xz --strip-components=1 -C "$MODULE/jni/ffmpeg"
# Audio only: no GPL/nonfree components and no network/demuxer code.
bash "$MODULE/jni/build_ffmpeg.sh" "$MODULE" "$NDK" linux-x86_64 24 mp3 aac ac3 eac3 flac opus vorbis
for ABI in armeabi-v7a arm64-v8a x86 x86_64; do
  cmake -S "$MODULE/jni" -B "$ROOT/android/decoder-ffmpeg/build/native/$ABI" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384"
  cmake --build "$ROOT/android/decoder-ffmpeg/build/native/$ABI" --parallel 2
  mkdir -p "$MODULE/jniLibs/$ABI"
  cp "$ROOT/android/decoder-ffmpeg/build/native/$ABI/libffmpegJNI.so" "$MODULE/jniLibs/$ABI/"
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" "$MODULE/jniLibs/$ABI/libffmpegJNI.so"
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -l "$MODULE/jniLibs/$ABI/libffmpegJNI.so" | grep LOAD
 done
