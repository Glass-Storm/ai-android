# Shipped consumer R8/ProGuard rules for :translate.
#
# ONNX Runtime (com.microsoft.onnxruntime:onnxruntime-android) ships NO consumer rules. Its JNI is
# resolved by name (libonnxruntime4j_jni.so binds `native` methods on ai.onnxruntime.* by exact class +
# method name). Keep them explicitly so the translation engine never silently depends on the AGP default.
-keepclasseswithmembers,includedescriptorclasses class ai.onnxruntime.** {
    native <methods>;
}
