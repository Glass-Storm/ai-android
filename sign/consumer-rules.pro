# Shipped consumer R8/ProGuard rules for :sign.
#
# TWO native runtimes back this module, each with its own JNI surface:
#   1. LiteRT (com.google.ai.edge.litert:litert) — the SINGLE shipped sign-classifier runtime. Its
#      libtensorflowlite_jni.so binds `native` methods on org.tensorflow.lite.* by exact name. The AAR's
#      proguard.txt only keeps @UsedByReflection members, NOT the JNI surface.
#   2. MediaPipe Tasks (com.google.mediapipe:tasks-vision/-core) — the hand/holistic landmark EXTRACTOR
#      (not a classifier runtime). libmediapipe_tasks_vision_jni.so binds `native` methods on
#      com.google.mediapipe.framework.* / com.google.mediapipe.tasks.core.* by exact name. The AARs ship
#      NO consumer rules at all.
-keepclasseswithmembers,includedescriptorclasses class org.tensorflow.lite.** {
    native <methods>;
}
-keepclasseswithmembers,includedescriptorclasses class com.google.mediapipe.** {
    native <methods>;
}
