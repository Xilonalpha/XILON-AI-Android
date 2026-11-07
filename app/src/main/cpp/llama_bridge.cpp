#include <jni.h>
#include <llama.h>
#include <algorithm>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex guard;
llama_model* model = nullptr;
llama_context* context = nullptr;
bool backend_ready = false;
long long last_prompt_ms = -1;
long long last_decode_ms = -1;
int last_generated_tokens = 0;
using Clock = std::chrono::steady_clock;
long long millis(Clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - start).count();
}

void unload() {
    if (context) { llama_free(context); context = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }
}
std::string utf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_load(JNIEnv* env, jobject, jstring path, jint context_size) {
    std::lock_guard<std::mutex> lock(guard);
    unload();
    last_prompt_ms = last_decode_ms = -1;
    last_generated_tokens = 0;
    const auto filename = utf8(env, path);
    if (filename.empty()) return JNI_FALSE;
    if (!backend_ready) { llama_backend_init(); backend_ready = true; }
    auto params = llama_model_default_params();
    params.n_gpu_layers = 0;
    model = llama_model_load_from_file(filename.c_str(), params);
    if (!model) return JNI_FALSE;
    auto cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(std::max(512, std::min(4096, static_cast<int>(context_size))));
    cp.n_batch = 512;
    cp.n_threads = 4;
    context = llama_init_from_model(model, cp);
    if (!context) { unload(); return JNI_FALSE; }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_unload(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(guard);
    unload();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_generate(JNIEnv* env, jobject, jstring prompt, jint max_tokens) {
    std::lock_guard<std::mutex> lock(guard);
    last_prompt_ms = last_decode_ms = -1;
    last_generated_tokens = 0;
    if (!model || !context) return env->NewStringUTF("Model not loaded");
    const auto input = utf8(env, prompt);
    if (input.empty()) return env->NewStringUTF("");
    const llama_vocab* vocab = llama_model_get_vocab(model);
    const int required = -llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()),
                                          nullptr, 0, true, false);
    if (required <= 0 || required > 4096) return env->NewStringUTF("Prompt too long");
    std::vector<llama_token> tokens(required);
    const int count = llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()),
                                     tokens.data(), required, true, false);
    const int limit = std::max(1, std::min(512, static_cast<int>(max_tokens)));
    if (count <= 0 || count + limit > static_cast<int>(llama_n_ctx(context)))
        return env->NewStringUTF("Context limit exceeded");
    llama_memory_clear(llama_get_memory(context), true);
    const auto prompt_start = Clock::now();
    const int chunk = 512;
    for (int i = 0; i < count; i += chunk) {
        const int n = std::min(chunk, count - i);
        if (llama_decode(context, llama_batch_get_one(tokens.data() + i, n)) != 0)
            return env->NewStringUTF("Prompt evaluation failed");
    }
    last_prompt_ms = millis(prompt_start);
    const auto decode_start = Clock::now();
    llama_sampler* sampler = llama_sampler_init_greedy();
    if (!sampler) return env->NewStringUTF("Sampler initialization failed");
    std::string output;
    for (int i = 0; i < limit; ++i) {
        const llama_token token = llama_sampler_sample(sampler, context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        ++last_generated_tokens;
        char piece[512];
        int n = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (n > 0) output.append(piece, n);
        llama_token next = token;
        if (llama_decode(context, llama_batch_get_one(&next, 1)) != 0) break;
    }
    llama_sampler_free(sampler);
    last_decode_ms = millis(decode_start);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_countTokens(JNIEnv* env, jobject, jstring text) {
    std::lock_guard<std::mutex> lock(guard);
    if (!model) return -1;
    const auto input = utf8(env, text);
    if (input.empty()) return 0;
    const llama_vocab* vocab = llama_model_get_vocab(model);
    const int required = llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()),
                                        nullptr, 0, false, false);
    return required >= 0 ? required : -required;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_lastTimings(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(guard);
    const jlong values[] = {last_prompt_ms, last_decode_ms, last_generated_tokens};
    jlongArray result = env->NewLongArray(3);
    if (result) env->SetLongArrayRegion(result, 0, 3, values);
    return result;
}
