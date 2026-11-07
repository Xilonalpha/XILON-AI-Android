#include <jni.h>
#include <cstdint>
#include <fstream>
#include <string>

extern "C" JNIEXPORT jint JNICALL
Java_com_xilonalpha_xilonai_NativeGgufProbe_probeVersion(
        JNIEnv* env, jobject /*thiz*/, jstring path) {
    if (path == nullptr) return -1;
    const char* raw = env->GetStringUTFChars(path, nullptr);
    if (raw == nullptr) return -1;
    const std::string filename(raw);
    env->ReleaseStringUTFChars(path, raw);

    std::ifstream file(filename, std::ios::binary);
    if (!file) return -1;
    unsigned char header[8] = {};
    file.read(reinterpret_cast<char*>(header), sizeof(header));
    if (file.gcount() != static_cast<std::streamsize>(sizeof(header))) return -1;
    if (header[0] != 'G' || header[1] != 'G' ||
        header[2] != 'U' || header[3] != 'F') return -1;
    const std::uint32_t version =
        static_cast<std::uint32_t>(header[4]) |
        (static_cast<std::uint32_t>(header[5]) << 8) |
        (static_cast<std::uint32_t>(header[6]) << 16) |
        (static_cast<std::uint32_t>(header[7]) << 24);
    return (version == 2 || version == 3) ? static_cast<jint>(version) : -1;
}
