package com.bobsdevattic.wifimapper.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val progress: Float) : State
        data class ReadyToInstall(val info: UpdateInfo) : State
        data class Error(val message: String) : State
    }

    private val checker = UpdateChecker()
    private val installer = ApkInstaller(app)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var downloaded: java.io.File? = null

    /** Silent check — only surfaces UI when an update is actually available. */
    fun checkInBackground() {
        if (_state.value != State.Idle && _state.value != State.UpToDate) return
        _state.value = State.Checking
        viewModelScope.launch {
            val info = checker.check()
            _state.value = if (info != null) State.Available(info) else State.UpToDate
        }
    }

    fun download() {
        val info = (_state.value as? State.Available)?.info ?: return
        _state.value = State.Downloading(info, 0f)
        viewModelScope.launch {
            val file = installer.download(info) { p ->
                _state.value = State.Downloading(info, p)
            }
            if (file != null) {
                downloaded = file
                _state.value = State.ReadyToInstall(info)
            } else {
                _state.value = State.Error("Download failed. Check your connection and try again.")
            }
        }
    }

    /** @return false if the app first needs "install unknown apps" permission. */
    fun install(): Boolean {
        val file = downloaded ?: return true
        if (!installer.canRequestInstall) return false
        installer.install(file)
        return true
    }

    fun unknownSourcesIntent() = installer.unknownSourcesSettingsIntent()

    fun dismiss() { _state.value = State.Idle }
}
