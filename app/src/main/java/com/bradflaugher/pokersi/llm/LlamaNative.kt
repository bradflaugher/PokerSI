package com.bradflaugher.pokersi.llm

/** Streams generated text; return false to stop early. Called on the generating thread. */
fun interface TokenCallback {
    fun onToken(text: String): Boolean
}

/**
 * The JNI surface of `app/src/main/cpp/pokersi-jni.cpp`. Handles are opaque
 * native pointers; 0 means failure. Calls on one handle must not overlap
 * (use [LlamaEngine], which serializes them), except [cancel].
 */
object LlamaNative {
    /** False when the app was built with `-PskipNative` or the library is missing. */
    val available: Boolean = try {
        System.loadLibrary("pokersi")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    external fun init(nativeLibDir: String)

    /** [path] is a filesystem path or `fd:N` for a descriptor from the Storage Access Framework. */
    external fun loadModel(path: String, nThreads: Int, nCtx: Int): Long

    external fun freeModel(handle: Long)

    external fun describe(handle: Long): String

    external fun cancel(handle: Long)

    external fun generate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        grammar: String?,
        maxTokens: Int,
        temperature: Float,
        seed: Int,
        callback: TokenCallback?,
    ): String
}
