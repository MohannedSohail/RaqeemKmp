# 🎙️ Raqeem Architecture Guide: Whisper.cpp & Offline STT Integration

## 📌 Executive Summary
This document specifies the on-device speech-to-text (STT) architecture for **Raqeem (رقيم)**, replacing cloud and proprietary speech APIs (`android.speech.*`, `RecognizerIntent`) with a **100% offline-first, local Whisper inference engine** backed by `whisper.cpp` (Git submodule tag `v1.7.4`, commit `8a9ad784`), compiled via **CMake** with Android NDK `30.0.16248370`, and bundled with GGML quantized models (`ggml-tiny-q5_1.bin`).

---

## 🧠 1. Model Selection: Quantized `tiny` Model (`ggml-tiny-q5_1.bin`)

### Designated Model Specification
* **Source:** GGML whisper quantization format (`ggerganov/whisper.cpp`)
* **Designated Model File:** `ggml-tiny-q5_1.bin`
* **Binary File Size:** 32,152,673 bytes (~30.7 MB, quantized 5-bit `q5_1`)
* **SHA256 Checksum:** `818710568DA3CA15689E31A743197B520007872FF9576237BDA97BD1B469C3D7`
* **Parameter Count:** 39 Million parameters
* **Target Architecture:** Multilingual Encoder-Decoder Transformer supporting Arabic (`ar`)

### Architectural Rationale
1. **Google Play Store AAB Budget:** Bundled directly in `androidApp/src/main/assets/models/ggml-tiny-q5_1.bin`, resulting in a final Release AAB of ~35.3 MB (well under the 200 MB Google Play upload threshold).
2. **Zero Runtime Downloads:** Users do not need an internet connection or post-install downloads; the model is extracted locally on first launch in background `Dispatchers.IO` with atomic temporary file swapping (`.tmp` -> rename).
3. **RAM & CPU Footprint:** Memory consumption remains below ~120 MB RAM during inference. On ARM64 processors, processing 3 seconds of audio finishes within 500–800 ms with `translate = false`.
4. **Context Caching:** The native Whisper context pointer is cached lazily and retained for subsequent voice queries, eliminating repeated model reloads. Explicit release occurs via `AndroidLocalSpeechTranscriber.release()`.

---

## ⚙️ 2. Build Integration: CMake & Git Submodule

### CMake `externalNativeBuild` Pipeline
* **Source submodule:** `androidApp/src/main/cpp/whisper.cpp` pinned to release tag `v1.7.4` (commit `8a9ad784`).
* **JNI source:** `androidApp/src/main/cpp/jni.cpp`
* **CMake build script:** `androidApp/src/main/cpp/CMakeLists.txt`
* **Target ABI:** `arm64-v8a` (NDK `abiFilters += listOf("arm64-v8a")` configured in `androidApp/build.gradle.kts`).
* *Note on `armeabi-v7a`:* Omitted to keep binary size compact (~35MB total AAB) and ensure NEON ARM64 acceleration. Supporting 32-bit `armeabi-v7a` would double native library footprint (~20MB additional overhead) with significantly degraded inference speed.
* **NDK Version:** `30.0.16248370`
* **CMake Version:** `4.1.2`

### JNI Native Bridge Contract (`shared/src/androidMain`)
Exported functions inside `libwhisper.so` match the Kotlin transcriber class signature:
```kotlin
package org.mohtech.app.ai.whisper

class AndroidLocalSpeechTranscriber(
    private val context: Context? = null
) : LocalSpeechTranscriber {

    private external fun initModel(modelPath: String): Long
    private external fun freeModel(contextPtr: Long)
    private external fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        pcm: FloatArray,
        prompt: String,
        language: String,
        translate: Boolean
    ): String

    companion object {
        init {
            System.loadLibrary("whisper")
        }
    }
}
```

### Proguard & R8 Keep Rules (`androidApp/proguard-rules.pro`)
To guarantee native symbol resolution in minified Release builds:
```proguard
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class org.mohtech.app.ai.whisper.AndroidLocalSpeechTranscriber {
    native <methods>;
    *;
}
```

---

## 📦 3. On-Device Model Extraction Pipeline

1. **Extraction Location:**
   `/data/data/org.mohtech.app/files/models/ggml-tiny-q5_1.bin`
2. **Atomic Write:**
   Extracted via `Dispatchers.IO` to a `.tmp` file and atomically renamed upon completion to prevent corrupted partial reads.
3. **Lazy Context Initialization:**
   `initModel(modelPath)` is called lazily upon first audio query, and the pointer is cached in `activeContextPtr` protected by a thread synchronization lock.
