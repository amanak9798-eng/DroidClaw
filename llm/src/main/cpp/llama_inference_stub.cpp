#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_droidclaw_llm_LlamaServer_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Llama.cpp inference stub initialized.";
    return env->NewStringUTF(hello.c_str());
}
