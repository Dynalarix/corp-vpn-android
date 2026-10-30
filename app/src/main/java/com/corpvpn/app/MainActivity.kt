package com.corpvpn.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corpvpn.app.data.ConnectionState
import com.corpvpn.app.ui.AppsScreen
import com.corpvpn.app.ui.CheckListScreen
import com.corpvpn.app.ui.CorpVpnTheme
import com.corpvpn.app.ui.MainViewModel
import com.corpvpn.app.ui.ServersScreen

private enum class Screen { SERVERS, CHECK_LIST, APPS }

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) importFrom(intent)
        setContent {
            CorpVpnTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.SERVERS) }
                val back = { screen = Screen.SERVERS }
                when (screen) {
                    Screen.SERVERS -> ServersScreen(
                        viewModel,
                        onOpenCheckList = { screen = Screen.CHECK_LIST },
                        onOpenApps = { screen = Screen.APPS },
                    )
                    Screen.CHECK_LIST -> CheckListScreen(viewModel, onBack = back)
                    Screen.APPS -> {
                        val connection by viewModel.connection.collectAsStateWithLifecycle()
                        AppsScreen(connected = connection is ConnectionState.Connected, onBack = back)
                    }
                }
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
