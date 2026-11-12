package com.rakotobe.comeback

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.rakotobe.comeback.ui.AppRoot
import com.rakotobe.comeback.ui.ComebackTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = ComebackApp.repo(this)
        setContent {
            ComebackTheme {
                AppRoot(repo)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                ComebackApp.repo(this@MainActivity).lockDays()
            } catch (t: Throwable) {
                CrashLog.record(this@MainActivity, t)
            }
        }
    }
}
