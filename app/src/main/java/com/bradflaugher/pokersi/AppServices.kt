package com.bradflaugher.pokersi

import android.content.Context
import com.bradflaugher.pokersi.game.HouseOnlyBrains
import com.bradflaugher.pokersi.game.OpponentBrains

/** App-wide singletons the UI pulls in. */
object AppServices {
    /**
     * Who plays the opponents. LLM WIRING POINT: return the `llm/`
     * implementation of [OpponentBrains] here (it should report
     * "HOUSE BOT (no model)" in its label and hand every seat to the fallback
     * while no model is loaded).
     */
    @Suppress("UNUSED_PARAMETER")
    fun brains(context: Context): OpponentBrains = HouseOnlyBrains
}
