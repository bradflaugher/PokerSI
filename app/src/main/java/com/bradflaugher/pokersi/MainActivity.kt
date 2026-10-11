package com.bradflaugher.pokersi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bradflaugher.pokersi.ui.PokerSiApp
import com.bradflaugher.pokersi.ui.PokerViewModel
import com.bradflaugher.pokersi.ui.pixel.CardArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: PokerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_SKIP_TIPS, false)) vm.skipTips()
        // Draw the 52 faces and the back off the main thread, so the first deal doesn't stutter.
        lifecycleScope.launch(Dispatchers.Default) { CardArt.prewarm() }
        setContent { PokerSiApp(vm) }
    }

    companion object {
        /** `am start --ez com.bradflaugher.pokersi.SKIP_TIPS true`: open without the first-run tips. */
        const val EXTRA_SKIP_TIPS = "com.bradflaugher.pokersi.SKIP_TIPS"
    }
}
