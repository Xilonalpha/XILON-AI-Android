package com.xilonalpha.xilonai

/** Filename-based preflight for GGUF components that cannot be used as standalone text LLMs. */
object AuxiliaryGguf {
    fun isAuxiliary(filename: String): Boolean {
        val name = filename.substringAfterLast('/').lowercase(java.util.Locale.ROOT)
        return listOf(
            "imatrix", "importance_matrix", "mmproj", "vision-projector",
            "vision_projector", "clip-projector", "clip_projector",
            "openvisionhead", "adapter_model"
        ).any { name.contains(it) }
    }
}
