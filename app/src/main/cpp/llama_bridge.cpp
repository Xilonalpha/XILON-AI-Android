#include <jni.h>
#include <android/log.h>
#include <llama.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <mutex>
#include <thread>
#include <string>
#include <vector>

namespace {
std::mutex guard;
std::atomic<bool> stop_requested{false};
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
// Convert ordinary UTF-8 to UTF-16 for JNI, including supplementary characters.
jstring unicode(JNIEnv* env, const std::string& value) {
    std::vector<jchar> chars;
    chars.reserve(value.size());
    for (size_t i = 0; i < value.size();) {
        const unsigned char lead = static_cast<unsigned char>(value[i]);
        uint32_t cp = 0;
        size_t length = 1;
        if (lead < 0x80) cp = lead;
        else if ((lead & 0xE0) == 0xC0) { cp = lead & 0x1F; length = 2; }
        else if ((lead & 0xF0) == 0xE0) { cp = lead & 0x0F; length = 3; }
        else if ((lead & 0xF8) == 0xF0) { cp = lead & 0x07; length = 4; }
        else cp = 0xFFFD;
        if (length > 1) {
            if (i + length > value.size()) { cp = 0xFFFD; length = 1; }
            else {
                bool valid = true;
                for (size_t j = 1; j < length; ++j) {
                    const auto next = static_cast<unsigned char>(value[i + j]);
                    if ((next & 0xC0) != 0x80) { valid = false; break; }
                    cp = (cp << 6) | (next & 0x3F);
                }
                if (!valid || (length == 2 && cp < 0x80) ||
                    (length == 3 && cp < 0x800) || (length == 4 && cp < 0x10000) ||
                    cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                    cp = 0xFFFD;
                    length = 1;
                }
            }
        }
        i += length;
        if (cp <= 0xFFFF) chars.push_back(static_cast<jchar>(cp));
        else {
            cp -= 0x10000;
            chars.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            chars.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
    }
    return env->NewString(chars.data(), static_cast<jsize>(chars.size()));
}
jstring fail(JNIEnv* env, const char* message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    if (exception) {
        env->ThrowNew(exception, message);
        env->DeleteLocalRef(exception);
    }
    return nullptr;
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
    cp.n_ctx = static_cast<uint32_t>(std::clamp(static_cast<int>(context_size), 512, 4096));
    // Avoid oversubscribing low-core phones while capping inference workers
    // on big.LITTLE devices. Batch capacity stays bounded for mobile memory.
    const unsigned int cores = std::thread::hardware_concurrency();
    const int workers = cores == 0 ? 2 : std::clamp(static_cast<int>(cores), 1, 4);
    cp.n_batch = 256;
    cp.n_threads = workers;
    cp.n_threads_batch = workers;
    __android_log_print(ANDROID_LOG_INFO, "XilonInference", "load_context=%u batch=%u cpu_threads=%d batch_threads=%d", cp.n_ctx, cp.n_batch, cp.n_threads, cp.n_threads_batch);
    context = llama_init_from_model(model, cp);
    if (!context) { unload(); return JNI_FALSE; }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_unload(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(guard);
    unload();
}

static jstring generate_impl(JNIEnv* env, jstring prompt, jint max_tokens, jobject callback) {
    std::lock_guard<std::mutex> lock(guard);
    last_prompt_ms = last_decode_ms = -1;
    last_generated_tokens = 0;
    if (!model || !context) return fail(env, "Model not loaded");
    const auto input = utf8(env, prompt);
    if (input.empty()) return env->NewStringUTF("");
    const llama_vocab* vocab = llama_model_get_vocab(model);
    const int probe = llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()),
                                     nullptr, 0, true, false);
    const int required = probe < 0 ? -probe : probe;
    if (required <= 0 || required > 32768) return fail(env, "Prompt too long");
    std::vector<llama_token> tokens(required);
    const int count = llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()),
                                     tokens.data(), required, true, false);
    const int limit = std::max(1, std::min(3072, static_cast<int>(max_tokens)));
    if (count <= 0) return fail(env, "Tokenization failed");
    const int available = static_cast<int>(llama_n_ctx(context));
    // Do not truncate raw tokens: this can break special role markers and chat templates.
    // The Kotlin conversation builder must remove whole turns before generation.
    if (count > available || limit > available - count)
        return fail(env, "Context limit exceeded: shorten conversation");
    const int effective_count = count;
    __android_log_print(ANDROID_LOG_INFO, "XilonInference", "prompt_tokens=%d context=%d max_output=%d", count, available, limit);
    // Reset KV state between independent prompts; never leak a previous chat
    // into a new model-formatted request. The context belongs to this model.
    llama_memory_clear(llama_get_memory(context), true);
    const auto prompt_start = Clock::now();
    const int chunk = 256;
    for (int i = 0; i < effective_count; i += chunk) {
        if (stop_requested.load()) {
            llama_memory_clear(llama_get_memory(context), true);
            return fail(env, "Generation cancelled");
        }
        const int n = std::min(chunk, effective_count - i);
        if (llama_decode(context, llama_batch_get_one(tokens.data() + i, n)) != 0) {
            llama_memory_clear(llama_get_memory(context), true);
            return fail(env, "Prompt evaluation failed");
        }
    }
    last_prompt_ms = millis(prompt_start);
    __android_log_print(ANDROID_LOG_INFO, "XilonInference", "prompt_evaluated_ms=%lld", last_prompt_ms);
    const auto decode_start = Clock::now();
    llama_sampler* sampler = llama_sampler_init_greedy();
    if (!sampler) {
        llama_memory_clear(llama_get_memory(context), true);
        return fail(env, "Sampler initialization failed");
    }
    jclass callbackClass = nullptr;
    jmethodID callbackMethod = nullptr;
    if (callback) {
        callbackClass = env->GetObjectClass(callback);
        if (!callbackClass) {
            llama_sampler_free(sampler);
            llama_memory_clear(llama_get_memory(context), true);
            return fail(env, "Streaming callback unavailable");
        }
        callbackMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
        if (!callbackMethod) {
            env->DeleteLocalRef(callbackClass);
            llama_sampler_free(sampler);
            llama_memory_clear(llama_get_memory(context), true);
            return nullptr;
        }
    }
    std::string output;
    auto last_stream_update = Clock::now() - std::chrono::milliseconds(200);
    bool decode_failed = false;
    bool piece_failed = false;
    for (int i = 0; i < limit; ++i) {
        if (stop_requested.load()) break;
        const llama_token token = llama_sampler_sample(sampler, context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        ++last_generated_tokens;
        char piece[512];
        int n = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (n < 0) {
            std::vector<char> expanded(static_cast<size_t>(-n) + 1);
            n = llama_token_to_piece(vocab, token, expanded.data(),
                                     static_cast<int32_t>(expanded.size()), 0, true);
            if (n > 0) output.append(expanded.data(), static_cast<size_t>(n));
            else piece_failed = true;
        } else if (n > 0) {
            output.append(piece, static_cast<size_t>(n));
        }
        if (piece_failed) break;
        // JNI UTF-16 conversion and callback allocation are expensive on mobile.
        // Stream at most ~5 updates/sec, without changing the token decoding rate.
        if (callback && !output.empty() &&
            Clock::now() - last_stream_update >= std::chrono::milliseconds(200)) {
            last_stream_update = Clock::now();
            jstring partial = unicode(env, output);
            if (!partial) break;
            jobject result = env->CallObjectMethod(callback, callbackMethod, partial);
            env->DeleteLocalRef(partial);
            if (result) env->DeleteLocalRef(result);
            if (env->ExceptionCheck()) break;
        }
        if (i + 1 < limit) {
            llama_token next = token;
            if (llama_decode(context, llama_batch_get_one(&next, 1)) != 0) {
                decode_failed = true;
                break;
            }
        }
    }
    llama_sampler_free(sampler);
    if (callbackClass) env->DeleteLocalRef(callbackClass);
    if (env->ExceptionCheck()) {
        llama_memory_clear(llama_get_memory(context), true);
        return nullptr;
    }
    last_decode_ms = millis(decode_start);
    __android_log_print(ANDROID_LOG_INFO, "XilonInference", "decode_ms=%lld generated_tokens=%d", last_decode_ms, last_generated_tokens);
    if (decode_failed) {
        llama_memory_clear(llama_get_memory(context), true);
        return fail(env, "Token decoding failed");
    }
    if (piece_failed) {
        llama_memory_clear(llama_get_memory(context), true);
        return fail(env, "Token text conversion failed");
    }
    return unicode(env, output);
}

extern "C" JNIEXPORT void JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_prepareGeneration(JNIEnv*, jobject) {
    stop_requested.store(false);
}

extern "C" JNIEXPORT void JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_requestStop(JNIEnv*, jobject) {
    // Intentionally lock-free: generation holds the model mutex.
    stop_requested.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_generate(JNIEnv* env, jobject, jstring prompt, jint max_tokens) {
    return generate_impl(env, prompt, max_tokens, nullptr);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_generateStreaming(JNIEnv* env, jobject, jstring prompt,
                                                           jint max_tokens, jobject callback) {
    if (!callback) return fail(env, "Missing streaming callback");
    return generate_impl(env, prompt, max_tokens, callback);
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

extern "C" JNIEXPORT jstring JNICALL
Java_com_xilonalpha_xilonai_LocalLlama_formatChat(JNIEnv* env, jobject,
                                                   jobjectArray roles, jobjectArray contents) {
    std::lock_guard<std::mutex> lock(guard);
    if (!model || !roles || !contents) return nullptr;
    const jsize count = env->GetArrayLength(roles);
    if (count != env->GetArrayLength(contents) || count < 1 || count > 64) return nullptr;
    const char* tmpl = llama_model_chat_template(model, nullptr);
    if (!tmpl || !*tmpl) return nullptr;
    std::vector<std::string> role_values, content_values;
    role_values.reserve(count);
    content_values.reserve(count);
    for (jsize i = 0; i < count; ++i) {
        auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto content = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        role_values.push_back(utf8(env, role));
        content_values.push_back(utf8(env, content));
        if (role) env->DeleteLocalRef(role);
        if (content) env->DeleteLocalRef(content);
        if (role_values.back() != "system" && role_values.back() != "user" &&
            role_values.back() != "assistant") return nullptr;
    }
    std::vector<llama_chat_message> messages;
    messages.reserve(count);
    for (jsize i = 0; i < count; ++i)
        messages.push_back({role_values[i].c_str(), content_values[i].c_str()});
    const int needed = llama_chat_apply_template(tmpl, messages.data(), count, true, nullptr, 0);
    if (needed <= 0 || needed > 1024 * 1024) return nullptr;
    std::vector<char> buffer(static_cast<size_t>(needed) + 1, 0);
    const int written = llama_chat_apply_template(tmpl, messages.data(), count, true,
                                                  buffer.data(), static_cast<int>(buffer.size()));
    if (written < 0 || written > needed) return nullptr;
    buffer[written] = '\0';
    return unicode(env, std::string(buffer.data(), static_cast<size_t>(written)));
}
