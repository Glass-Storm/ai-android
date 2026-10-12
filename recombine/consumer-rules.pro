# Shipped consumer R8/ProGuard rules for :recombine.
#
# The llama.cpp JNI binding is built locally (externalNativeBuild, NOT a Maven artifact). librecombine.so
# anchors `native` methods on hk.edu.hkmu.recombine.NativeLlama by exact class + method name. No upstream
# AAR ships consumer rules for this, so keep them here.
-keepclasseswithmembers,includedescriptorclasses class hk.edu.hkmu.recombine.NativeLlama {
    native <methods>;
}
