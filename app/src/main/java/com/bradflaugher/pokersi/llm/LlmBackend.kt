package com.bradflaugher.pokersi.llm

/**
 * What [LlmAgent] needs from a model: [LlamaEngine] on the phone, the
 * desktop harness in tests (`HarnessSessionTest`).
 */
interface LlmBackend {
    /** True while a model is loaded and can [generate]. */
    val isReady: Boolean

    /**
     * Generates a reply to [messages], constrained by [grammar] (GBNF, or
     * null for free text). [onText] gets the whole reply so far after each
     * token and returns false to stop early. Throws
     * [kotlinx.coroutines.TimeoutCancellationException] after [timeoutMs].
     */
    suspend fun generate(
        messages: List<ChatMessage>,
        grammar: String?,
        maxTokens: Int,
        temperature: Float,
        seed: Int,
        timeoutMs: Long = 25_000,
        onText: (String) -> Boolean = { true },
    ): String
}
