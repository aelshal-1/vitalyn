package com.elshal.vitalyn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.elshal.vitalyn.services.NotificationService
import com.elshal.vitalyn.ui.RootScreen
import com.elshal.vitalyn.ui.VitalynTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph by lazy { AppGraph.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The design is light-only for now.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleOpen(intent)
        setContent {
            CompositionLocalProvider(LocalApp provides graph) {
                VitalynTheme { RootScreen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { graph.refresh() }
    }

    /** Opens the screen a tapped notification points to. */
    private fun handleOpen(intent: Intent?) {
        OpenTarget.fromRaw(intent?.getStringExtra(NotificationService.EXTRA_OPEN))?.let { graph.openTarget = it }
        intent?.removeExtra(NotificationService.EXTRA_OPEN)
    }
}
