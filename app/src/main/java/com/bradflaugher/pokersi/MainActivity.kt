package com.bradflaugher.pokersi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.bradflaugher.pokersi.ui.PokerSiApp
import com.bradflaugher.pokersi.ui.PokerViewModel

class MainActivity : ComponentActivity() {
    private val vm: PokerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PokerSiApp(vm) }
    }
}
