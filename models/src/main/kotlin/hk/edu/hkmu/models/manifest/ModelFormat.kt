package hk.edu.hkmu.models.manifest

/** Serialized model artifact format recognized by the manifest schema. */
enum class ModelFormat(val id: String, val extension: String) {
    Gguf("gguf", ".gguf"),
    Onnx("onnx", ".onnx"),
    Tflite("tflite", ".tflite"),
    Task("task", ".task");

    companion object {
        /** Resolves a manifest `format` token, or `null` when it is not a recognized format. */
        fun fromId(id: String): ModelFormat? = entries.firstOrNull { it.id == id.trim().lowercase() }
    }
}
