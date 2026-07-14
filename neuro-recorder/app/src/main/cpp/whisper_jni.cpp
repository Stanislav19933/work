#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_forgptstas_neurorecorder_WhisperEngine_transcribeNative(
        JNIEnv *env,
        jobject,
        jstring modelPath,
        jfloatArray samples,
        jint threads,
        jstring language) {
    const char *model = env->GetStringUTFChars(modelPath, nullptr);
    const char *lang = env->GetStringUTFChars(language, nullptr);

    whisper_context_params contextParams = whisper_context_default_params();
    contextParams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(model, contextParams);

    env->ReleaseStringUTFChars(modelPath, model);
    if (ctx == nullptr) {
        env->ReleaseStringUTFChars(language, lang);
        return env->NewStringUTF("");
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.n_threads = threads > 0 ? threads : std::max(1u, std::thread::hardware_concurrency() / 2);
    params.language = lang;

    const int result = whisper_full(ctx, params, pcm.data(), static_cast<int>(pcm.size()));
    env->ReleaseStringUTFChars(language, lang);

    if (result != 0) {
        whisper_free(ctx);
        return env->NewStringUTF("");
    }

    std::string output;
    const int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text != nullptr) {
            output += text;
        }
    }

    whisper_free(ctx);
    return env->NewStringUTF(output.c_str());
}
