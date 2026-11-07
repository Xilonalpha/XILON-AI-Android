#include <jni.h>
#include <cstdint>
#include <fstream>
#include <string>

namespace {
bool read_u32(std::ifstream& file, std::uint32_t& out) {
    unsigned char b[4] = {};
    if (!file.read(reinterpret_cast<char*>(b), 4)) return false;
    out = std::uint32_t(b[0]) | (std::uint32_t(b[1]) << 8) |
          (std::uint32_t(b[2]) << 16) | (std::uint32_t(b[3]) << 24);
    return true;
}
bool read_u64(std::ifstream& file, std::uint64_t& out) {
    unsigned char b[8] = {};
    if (!file.read(reinterpret_cast<char*>(b), 8)) return false;
    out = 0;
    for (int i = 0; i < 8; ++i) out |= std::uint64_t(b[i]) << (i * 8);
    return true;
}
bool inspect(const std::string& filename, std::uint32_t& version,
             std::uint64_t& tensors, std::uint64_t& metadata) {
    std::ifstream file(filename, std::ios::binary);
    if (!file) return false;
    char magic[4] = {};
    if (!file.read(magic, 4)) return false;
    if (std::string(magic, 4) != "GGUF") return false;
    if (!read_u32(file, version) || (version != 2 && version != 3)) return false;
    if (!read_u64(file, tensors) || !read_u64(file, metadata)) return false;
    // Plausibility check only; does not validate the entire GGUF structure.
    if (tensors > 10000000ULL || metadata > 10000000ULL) return false;
    return true;
}
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_xilonalpha_xilonai_NativeGgufProbe_probeHeader(
        JNIEnv* env, jobject /*thiz*/, jstring path) {
    if (path == nullptr) return nullptr;
    const char* raw = env->GetStringUTFChars(path, nullptr);
    if (raw == nullptr) return nullptr;
    const std::string filename(raw);
    env->ReleaseStringUTFChars(path, raw);
    std::uint32_t version = 0;
    std::uint64_t tensors = 0, metadata = 0;
    if (!inspect(filename, version, tensors, metadata)) return nullptr;
    const jlong values[3] = {
        static_cast<jlong>(version),
        static_cast<jlong>(tensors),
        static_cast<jlong>(metadata)
    };
    jlongArray result = env->NewLongArray(3);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 3, values);
    return result;
}
