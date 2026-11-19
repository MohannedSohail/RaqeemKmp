#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cstring>
#include "whisper.h"

#define TAG "RaqeemWhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_mohtech_app_ai_whisper_AndroidLocalSpeechTranscriber_initModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path_str) {
    if (model_path_str == nullptr) {
        LOGE("initModel: model_path_str is null");
        return 0L;
    }
    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);
    if (model_path == nullptr) {
        LOGE("initModel: failed to get UTF chars");
        return 0L;
    }

    LOGI("initModel: Loading model from %s", model_path);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(model_path, cparams);
    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (ctx == nullptr) {
        LOGE("initModel: whisper_init_from_file_with_params failed");
        return 0L;
    }
    LOGI("initModel: Successfully loaded context %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_org_mohtech_app_ai_whisper_AndroidLocalSpeechTranscriber_freeModel(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong context_ptr) {
    if (context_ptr == 0L) {
        return;
    }
    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    LOGI("freeModel: Freeing context %p", ctx);
    whisper_free(ctx);
}

JNIEXPORT jstring JNICALL
Java_org_mohtech_app_ai_whisper_AndroidLocalSpeechTranscriber_fullTranscribe(
        JNIEnv *env,
        jobject /* thiz */,
        jlong context_ptr,
        jint num_threads,
        jfloatArray audio_data,
        jstring prompt_str,
        jstring language_str,
        jboolean translate) {
    if (context_ptr == 0L || audio_data == nullptr) {
        LOGE("fullTranscribe: Invalid context or audio data");
        return env->NewStringUTF("");
    }

    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    jsize audio_len = env->GetArrayLength(audio_data);
    if (audio_len <= 0) {
        return env->NewStringUTF("");
    }

    jfloat *samples = env->GetFloatArrayElements(audio_data, nullptr);
    if (samples == nullptr) {
        LOGE("fullTranscribe: Failed to get float array elements");
        return env->NewStringUTF("");
    }

    const char *prompt_chars = nullptr;
    if (prompt_str != nullptr) {
        prompt_chars = env->GetStringUTFChars(prompt_str, nullptr);
    }

    const char *lang_chars = "ar";
    if (language_str != nullptr) {
        lang_chars = env->GetStringUTFChars(language_str, nullptr);
    }

    int n_threads = std::min(std::max(1, static_cast<int>(num_threads)), 4);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = static_cast<bool>(translate);
    params.language = lang_chars;
    params.n_threads = n_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = true;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.temperature_inc = 0.0f;
    params.entropy_thold = 2.4f;
    params.logprob_thold = -1.0f;
    params.no_speech_thold = 0.6f;

    if (prompt_chars != nullptr && std::strlen(prompt_chars) > 0) {
        params.initial_prompt = prompt_chars;
    }

    whisper_reset_timings(ctx);
    int status = whisper_full(ctx, params, samples, audio_len);

    if (prompt_chars != nullptr) {
        env->ReleaseStringUTFChars(prompt_str, prompt_chars);
    }
    if (language_str != nullptr) {
        env->ReleaseStringUTFChars(language_str, lang_chars);
    }
    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);

    if (status != 0) {
        LOGE("fullTranscribe: whisper_full failed with code %d", status);
        return env->NewStringUTF("");
    }

    std::string full_text;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *seg = whisper_full_get_segment_text(ctx, i);
        if (seg != nullptr) {
            full_text += seg;
        }
    }

    return env->NewStringUTF(full_text.c_str());
}

} // extern "C"

