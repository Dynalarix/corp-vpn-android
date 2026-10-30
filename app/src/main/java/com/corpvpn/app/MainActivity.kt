package com.corpvpn.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.corpvpn.app.ui.CorpVpnTheme
import com.corpvpn.app.ui.MainViewModel
import com.corpvpn.app.ui.ServersScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) importFrom(intent)
        setContent {
            CorpVpnTheme {
                ServersScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFrom(intent)
    }

    /** Handles a vpn://... link opened from the browser. */
    private fun importFrom(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(viewModel::import)
    }
}
