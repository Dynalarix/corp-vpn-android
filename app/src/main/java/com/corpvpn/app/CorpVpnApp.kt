package com.corpvpn.app

import android.app.Application
import com.corpvpn.app.data.AppRulesRepository
import com.corpvpn.app.data.TunnelManager

class CorpVpnApp : Application() {
    val appRules: AppRulesRepository by lazy { AppRulesRepository(this) }

    /** One tunnel for the whole app, so it outlives screens and view models. */
    val tunnels: TunnelManager by lazy { TunnelManager(this, appRules) }
}
