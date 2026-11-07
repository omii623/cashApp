package com.example.cashapp.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.backup.Backup
import com.example.cashapp.data.backup.BackupFormatException
import com.example.cashapp.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupMessage { JSON_EXPORTED, CSV_EXPORTED, RESTORED, EXPORT_FAILED }

data class BackupUiState(
    val isWorking: Boolean = false,
    /** Beolvasott, jóváhagyásra váró mentés összesítője. */
    val pendingRestore: BackupManager.Summary? = null,
    val message: BackupMessage? = null,
    /** Visszaállítási hiba oka; null = nincs hiba. [BackupError.Unknown]: nem formátumhiba (pl. I/O). */
    val error: BackupError? = null,
)

sealed interface BackupError {
    data class Format(val reason: BackupFormatException.Reason) : BackupError
    data object Unknown : BackupError
}

class BackupViewModel(private val backupManager: BackupManager) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _state.asStateFlow()

    private var pendingBackup: Backup? = null

    fun exportJson(uri: Uri) = run(BackupMessage.JSON_EXPORTED) { backupManager.exportJson(uri) }

    fun exportCsv(uri: Uri) = run(BackupMessage.CSV_EXPORTED) { backupManager.exportCsv(uri) }

    /** 1. lépés: a fájl beolvasása és ellenőrzése, majd megerősítés kérése. */
    fun pickRestoreFile(uri: Uri) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            runCatching { backupManager.readBackup(uri) }
                .onSuccess { b ->
                    pendingBackup = b
                    _state.update { it.copy(isWorking = false, pendingRestore = backupManager.summarize(b)) }
                }
                .onFailure { e -> _state.update { it.copy(isWorking = false, error = errorText(e)) } }
        }
    }

    /** 2. lépés: megerősítés után a jelenlegi adatok cseréje. */
    fun confirmRestore() {
        val backup = pendingBackup ?: return
        pendingBackup = null
        _state.update { it.copy(pendingRestore = null, isWorking = true) }
        viewModelScope.launch {
            runCatching { backupManager.restore(backup) }
                .onSuccess { _state.update { it.copy(isWorking = false, message = BackupMessage.RESTORED) } }
                .onFailure { e -> _state.update { it.copy(isWorking = false, error = errorText(e)) } }
        }
    }

    fun cancelRestore() {
        pendingBackup = null
        _state.update { it.copy(pendingRestore = null) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun errorShown() = _state.update { it.copy(error = null) }

    private fun run(success: BackupMessage, block: suspend () -> Unit) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val ok = runCatching { block() }.isSuccess
            _state.update { it.copy(isWorking = false, message = if (ok) success else BackupMessage.EXPORT_FAILED) }
        }
    }

    /** A hibát a felhasználó nyelvén a UI írja ki. */
    private fun errorText(e: Throwable): BackupError =
        (e as? BackupFormatException)?.let { BackupError.Format(it.reason) } ?: BackupError.Unknown
}
