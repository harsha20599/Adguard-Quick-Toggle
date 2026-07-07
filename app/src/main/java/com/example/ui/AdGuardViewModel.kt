package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdGuardCredentialsStore
import com.example.data.AdGuardRepository
import com.example.data.AdGuardStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdGuardUiState {
    object SetupRequired : AdGuardUiState
    object Loading : AdGuardUiState
    data class Success(val status: AdGuardStatus) : AdGuardUiState
    data class Error(val message: String) : AdGuardUiState
}

sealed interface AdGuardTestState {
    object Idle : AdGuardTestState
    object Testing : AdGuardTestState
    object Success : AdGuardTestState
    data class Error(val message: String) : AdGuardTestState
}

class AdGuardViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialsStore = AdGuardCredentialsStore(application)
    private val repository = AdGuardRepository(credentialsStore)

    private val _uiState = MutableStateFlow<AdGuardUiState>(AdGuardUiState.Loading)
    val uiState: StateFlow<AdGuardUiState> = _uiState.asStateFlow()

    private val _testState = MutableStateFlow<AdGuardTestState>(AdGuardTestState.Idle)
    val testState: StateFlow<AdGuardTestState> = _testState.asStateFlow()

    private val _isToggling = MutableStateFlow(false)
    val isToggling: StateFlow<Boolean> = _isToggling.asStateFlow()

    // Settings exposed directly
    private val _trustSelfSigned = MutableStateFlow(credentialsStore.isTrustSelfSigned())
    val trustSelfSigned: StateFlow<Boolean> = _trustSelfSigned.asStateFlow()

    private val _logsEnabled = MutableStateFlow(credentialsStore.isLogsEnabled())
    val logsEnabled: StateFlow<Boolean> = _logsEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(credentialsStore.isNotificationsEnabled())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _serverUrl = MutableStateFlow(credentialsStore.getServerUrl() ?: "")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow(credentialsStore.getUsername() ?: "")
    val username: StateFlow<String> = _username.asStateFlow()

    init {
        checkSetupAndFetch()
    }

    fun checkSetupAndFetch() {
        if (!credentialsStore.hasCredentials()) {
            _uiState.value = AdGuardUiState.SetupRequired
        } else {
            _serverUrl.value = credentialsStore.getServerUrl() ?: ""
            _username.value = credentialsStore.getUsername() ?: ""
            refreshStatus()
        }
    }

    fun refreshStatus() {
        if (!credentialsStore.hasCredentials()) {
            _uiState.value = AdGuardUiState.SetupRequired
            return
        }
        viewModelScope.launch {
            _uiState.value = AdGuardUiState.Loading
            repository.fetchStatus()
                .onSuccess { status ->
                    _uiState.value = AdGuardUiState.Success(status)
                }
                .onFailure { error ->
                    _uiState.value = AdGuardUiState.Error(error.message ?: "Unknown error occurred")
                }
        }
    }

    fun testCredentials(url: String, user: String, pass: String, selfSigned: Boolean) {
        if (url.isBlank()) {
            _testState.value = AdGuardTestState.Error("Server URL cannot be empty")
            return
        }
        viewModelScope.launch {
            _testState.value = AdGuardTestState.Testing
            repository.testConnection(url, user, pass, selfSigned)
                .onSuccess {
                    _testState.value = AdGuardTestState.Success
                }
                .onFailure { error ->
                    _testState.value = AdGuardTestState.Error(error.message ?: "Connection failed")
                }
        }
    }

    fun clearTestState() {
        _testState.value = AdGuardTestState.Idle
    }

    fun saveCredentials(url: String, user: String, pass: String) {
        credentialsStore.saveCredentials(url, user, pass)
        _serverUrl.value = credentialsStore.getServerUrl() ?: ""
        _username.value = credentialsStore.getUsername() ?: ""
        
        if (_uiState.value is AdGuardUiState.SetupRequired || _uiState.value is AdGuardUiState.Error) {
            checkSetupAndFetch()
        } else {
            refreshStatus()
        }
    }

    fun forgetCredentials() {
        credentialsStore.clearCredentials()
        _serverUrl.value = ""
        _username.value = ""
        _uiState.value = AdGuardUiState.SetupRequired
    }

    fun toggleProtection() {
        val currentState = _uiState.value
        if (currentState !is AdGuardUiState.Success) return

        val targetEnabled = !currentState.status.protection_enabled
        viewModelScope.launch {
            _isToggling.value = true
            try {
                repository.setProtection(targetEnabled)
                    .onSuccess {
                        // Update UI locally with the new protection state
                        val updatedStatus = currentState.status.copy(protection_enabled = targetEnabled)
                        _uiState.value = AdGuardUiState.Success(updatedStatus)
                    }
                    .onFailure { error ->
                        _uiState.value = AdGuardUiState.Error(error.message ?: "Failed to toggle protection")
                    }
            } finally {
                _isToggling.value = false
            }
        }
    }

    fun setTrustSelfSigned(enabled: Boolean) {
        credentialsStore.setTrustSelfSigned(enabled)
        _trustSelfSigned.value = enabled
        // Re-check state if setup complete
        if (credentialsStore.hasCredentials()) {
            refreshStatus()
        }
    }

    fun setLogsEnabled(enabled: Boolean) {
        credentialsStore.setLogsEnabled(enabled)
        _logsEnabled.value = enabled
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        credentialsStore.setNotificationsEnabled(enabled)
        _notificationsEnabled.value = enabled
    }
}
