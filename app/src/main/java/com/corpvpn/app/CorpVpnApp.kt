package com.corpvpn.app

import android.app.Application
import com.corpvpn.app.data.TunnelManager

class CorpVpnApp : Application() {
    /** One tunnel for the whole app, so it outlives screens and view models. */
    val tunnels: TunnelManager by lazy { TunnelManager(this) }
}
