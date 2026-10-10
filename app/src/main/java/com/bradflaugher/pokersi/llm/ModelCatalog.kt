package com.bradflaugher.pokersi.llm

/** A downloadable model. [sizeBytes] is the exact file size on Hugging Face. */
data class CatalogModel(
    val id: String,
    val name: String,
    val maker: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    /** One fun line for the model picker. */
    val description: String,
    val recommended: Boolean = false,
)

/**
 * Small instruct GGUFs (Q4_K_M) that run on a phone and follow a JSON
 * format well enough to play. Sizes were checked with HTTP HEAD requests
 * (Hugging Face's x-linked-size); the URLs are the ones LM Playground uses
 * where it has the model.
 */
object ModelCatalog {
    private const val HF = "https://huggingface.co"

    val models: List<CatalogModel> = listOf(
        CatalogModel(
            "qwen3-0.6b", "Qwen 3 0.6B", "Alibaba", "Qwen3-0.6B-Q4_K_M.gguf",
            "$HF/lmstudio-community/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf", 484_219_808,
            "Pocket-sized and quick. Plays like it just learned the rules.",
        ),
        CatalogModel(
            "llama3.2-1b", "Llama 3.2 1B", "Meta", "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "$HF/lmstudio-community/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf", 807_690_688,
            "Steady and talkative. A safe pair of hands for any phone.",
        ),
        CatalogModel(
            "gemma3-1b", "Gemma 3 1B", "Google", "gemma-3-1b-it-Q4_K_M.gguf",
            "$HF/lmstudio-community/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf", 806_058_240,
            "Chatty and charming. Never met a pun it didn't like.",
        ),
        CatalogModel(
            "lfm2.5-1.2b", "LFM2.5 1.2B Instruct", "Liquid AI", "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "$HF/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf", 730_895_168,
            "The best starter: built for phones, quick, sensible and short-spoken.",
            recommended = true,
        ),
        CatalogModel(
            "granite4-h-1b", "Granite 4.0 H 1B", "IBM", "granite-4.0-h-1b-Q4_K_M.gguf",
            "$HF/ibm-granite/granite-4.0-h-1b-GGUF/resolve/main/granite-4.0-h-1b-Q4_K_M.gguf", 901_162_208,
            "All business. Brings a spreadsheet to game night.",
        ),
        CatalogModel(
            "qwen3-1.7b", "Qwen 3 1.7B", "Alibaba", "Qwen3-1.7B-Q4_K_M.gguf",
            "$HF/lmstudio-community/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", 1_282_439_328,
            "A sharper player that still fits on most phones.",
        ),
        CatalogModel(
            "llama3.2-3b", "Llama 3.2 3B", "Meta", "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            "$HF/lmstudio-community/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf", 2_019_377_440,
            "Reads the table like a regular. Needs a roomy phone.",
        ),
        CatalogModel(
            "smollm3-3b", "SmolLM3 3B", "Hugging Face", "HuggingFaceTB_SmolLM3-3B-Q4_K_M.gguf",
            "$HF/bartowski/HuggingFaceTB_SmolLM3-3B-GGUF/resolve/main/HuggingFaceTB_SmolLM3-3B-Q4_K_M.gguf", 1_915_305_792,
            "Small name, big ideas. Fully open, all the way down.",
        ),
        CatalogModel(
            "phi4-mini", "Phi-4 mini", "Microsoft", "Phi-4-mini-instruct-Q4_K_M.gguf",
            "$HF/lmstudio-community/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf", 2_491_874_400,
            "The math whiz. Counts outs in its sleep.",
        ),
        CatalogModel(
            "qwen3-4b", "Qwen 3 4B", "Alibaba", "Qwen3-4B-Q4_K_M.gguf",
            "$HF/lmstudio-community/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf", 2_497_280_704,
            "The table shark. Slow on older phones, scary on new ones.",
        ),
    )

    fun byId(id: String): CatalogModel? = models.firstOrNull { it.id == id }

    fun byFileName(fileName: String): CatalogModel? = models.firstOrNull { it.fileName == fileName }

    val recommended: CatalogModel get() = models.first { it.recommended }
}
