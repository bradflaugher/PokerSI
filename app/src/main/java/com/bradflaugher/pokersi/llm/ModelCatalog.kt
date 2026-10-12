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
 * Small instruct GGUFs that run on a phone and follow a JSON format well
 * enough to play: the newest of each family LM Playground carries, Q4_K_M
 * (Gemma 4 is Google's own quantization-aware Q4_0). Sizes were checked
 * with HTTP HEAD requests (Hugging Face's x-linked-size); the URLs are the
 * repos LM Playground uses.
 */
object ModelCatalog {
    private const val HF = "https://huggingface.co"

    val models: List<CatalogModel> = listOf(
        CatalogModel(
            "qwen3.5-0.8b", "Qwen 3.5 0.8B", "Alibaba", "Qwen_Qwen3.5-0.8B-Q4_K_M.gguf",
            "$HF/bartowski/Qwen_Qwen3.5-0.8B-GGUF/resolve/main/Qwen_Qwen3.5-0.8B-Q4_K_M.gguf", 579_615_840,
            "Pocket-sized and quick. Plays like it just learned the rules.",
        ),
        CatalogModel(
            "minicpm5-1b", "MiniCPM5 1B", "OpenBMB", "MiniCPM5-1B-Q4_K_M.gguf",
            "$HF/openbmb/MiniCPM5-1B-GGUF/resolve/main/MiniCPM5-1B-Q4_K_M.gguf", 688_065_920,
            "Tiny and eager. Shows up early and counts its chips twice.",
        ),
        CatalogModel(
            "llama3.2-1b", "Llama 3.2 1B", "Meta", "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "$HF/lmstudio-community/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf", 807_690_688,
            "Steady and talkative. A safe pair of hands for any phone.",
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
            "qwen3.5-2b", "Qwen 3.5 2B", "Alibaba", "Qwen_Qwen3.5-2B-Q4_K_M.gguf",
            "$HF/bartowski/Qwen_Qwen3.5-2B-GGUF/resolve/main/Qwen_Qwen3.5-2B-Q4_K_M.gguf", 1_396_198_496,
            "A sharper player that still fits on most phones.",
        ),
        CatalogModel(
            "smollm3-3b", "SmolLM3 3B", "Hugging Face", "HuggingFaceTB_SmolLM3-3B-Q4_K_M.gguf",
            "$HF/bartowski/HuggingFaceTB_SmolLM3-3B-GGUF/resolve/main/HuggingFaceTB_SmolLM3-3B-Q4_K_M.gguf", 1_915_305_792,
            "Small name, big ideas. Fully open, all the way down.",
        ),
        CatalogModel(
            "llama3.2-3b", "Llama 3.2 3B", "Meta", "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            "$HF/lmstudio-community/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf", 2_019_377_440,
            "Reads the table like a regular. Needs a roomy phone.",
        ),
        CatalogModel(
            "phi4-mini", "Phi-4 mini", "Microsoft", "Phi-4-mini-instruct-Q4_K_M.gguf",
            "$HF/lmstudio-community/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf", 2_491_874_400,
            "The math whiz. Counts outs in its sleep.",
        ),
        CatalogModel(
            "qwen3.5-4b", "Qwen 3.5 4B", "Alibaba", "Qwen_Qwen3.5-4B-Q4_K_M.gguf",
            "$HF/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/main/Qwen_Qwen3.5-4B-Q4_K_M.gguf", 3_013_027_808,
            "The table shark. Slow on older phones, scary on new ones.",
        ),
        CatalogModel(
            "gemma4-e2b", "Gemma 4 E2B", "Google", "gemma-4-E2B_q4_0-it.gguf",
            "$HF/google/gemma-4-E2B-it-qat-q4_0-gguf/resolve/main/gemma-4-E2B_q4_0-it.gguf", 3_349_516_256,
            "Google's phone-sized ace. Charming, sharp, and needs 8 GB of RAM.",
        ),
        CatalogModel(
            "gemma4-e4b", "Gemma 4 E4B", "Google", "gemma-4-E4B_q4_0-it.gguf",
            "$HF/google/gemma-4-E4B-it-qat-q4_0-gguf/resolve/main/gemma-4-E4B_q4_0-it.gguf", 5_154_941_280,
            "The high roller. Plays a mean game on a 12 GB flagship.",
        ),
    )

    fun byId(id: String): CatalogModel? = models.firstOrNull { it.id == id }

    fun byFileName(fileName: String): CatalogModel? = models.firstOrNull { it.fileName == fileName }

    val recommended: CatalogModel get() = models.first { it.recommended }
}
