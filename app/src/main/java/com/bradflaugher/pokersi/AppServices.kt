package com.bradflaugher.pokersi

import android.content.Context
import com.bradflaugher.pokersi.game.LlmBrains
import com.bradflaugher.pokersi.game.OpponentBrains
import com.bradflaugher.pokersi.llm.LlamaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons the UI pulls in. */
object AppServices {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var brains: OpponentBrains? = null

    /** Who plays the opponents: the loaded model, or the House Bot while none is loaded. */
    fun brains(context: Context): OpponentBrains = brains ?: synchronized(this) {
        brains ?: LlmBrains(LlamaEngine.get(context), scope).also { brains = it }
    }
}
