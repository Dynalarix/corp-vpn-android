package com.corpvpn.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.corpvpn.app.data.KeyRepository
import com.corpvpn.core.KeyDecoder
import com.corpvpn.core.KeyImportException
import com.corpvpn.core.Server
import com.corpvpn.core.VpnKey
import com.corpvpn.core.groupByServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KeyRepository(application)
    private val keys = MutableStateFlow<List<VpnKey>>(emptyList())

    val servers: StateFlow<List<Server>> = keys
        .map { it.groupByServer() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the snackbar. */
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { keys.value = repository.load() }
    }

    fun import(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val decoded = try {
                KeyDecoder.decode(input)
            } catch (e: KeyImportException) {
                _messages.send(e.message ?: "Couldn't import the key.")
                return@launch
            }
            val existing = keys.value.map { it.id }.toSet()
            val added = decoded.filter { it.id !in existing }
            if (added.isEmpty()) {
                _messages.send("This key is already added.")
                return@launch
            }
            val updated = keys.value + added
            repository.save(updated)
            keys.value = updated
            _messages.send("Added ${added.first().name} (${added.joinToString { it.protocol.displayName }}).")
        }
    }

    fun removeServer(server: Server) {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = server.keys.map { it.id }.toSet()
            val updated = keys.value.filter { it.id !in ids }
            repository.save(updated)
            keys.value = updated
        }
    }
}
