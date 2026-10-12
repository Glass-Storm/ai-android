# Shipped consumer R8/ProGuard rules for :asr.
#
# sherpa-onnx exposes a JNI surface: com.k2fsa.sherpa.onnx.* classes declare `native` methods that
# libsherpa-onnx-jni.so resolves by exact class + method name. The vendored AAR (libs/sherpa-onnx-1.13.8.aar)
# ships an EMPTY proguard.txt, so these names would otherwise be protected only by the AGP default
# (which we must not silently rely on). Keep the JNI-bound class + native-method names explicitly.
-keepclasseswithmembers,includedescriptorclasses class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}
