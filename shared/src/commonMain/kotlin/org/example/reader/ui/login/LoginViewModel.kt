package org.example.reader.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.UserInfo
import org.example.reader.core.net.ApiResult
import org.example.reader.core.session.AuthState
import org.example.reader.core.session.ServerConfig
import org.example.reader.core.settings.SettingsKeys

data class LoginUiState(
    val serverAddress: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val addressError: String? = null,
)

/**
 * 登录页界面状态。
 *
 * 服务地址要填到 `/reader3` 为止（子路径部署时还要带部署前缀），
 * 因为会话 Cookie 的 Path 与它同前缀——填错会导致登录 200 但后续全 401。
 */
class LoginViewModel(private val deps: AppDependencies) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(
            serverAddress = deps.settings.getString(SettingsKeys.API_BASE)
                ?: ServerConfig.DEFAULT_DEBUG_API_BASE,
            username = deps.settings.getString(SettingsKeys.USERNAME).orEmpty(),
        ),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onServerAddressChange(value: String) {
        _state.update { it.copy(serverAddress = value, addressError = null, error = null) }
    }

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return

        val normalizedBase = ServerConfig.normalize(current.serverAddress)
        if (normalizedBase == null) {
            _state.update { it.copy(addressError = "请填写完整地址，例如 http://10.0.2.2:8080/reader3") }
            return
        }
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "请填写用户名和密码") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null, addressError = null) }

        // 地址可能已变更：先落盘再登录，确保后续请求打到新服务器。
        deps.settings.putString(SettingsKeys.API_BASE, normalizedBase)
        deps.session.updateServerConfig(ServerConfig(normalizedBase))

        viewModelScope.launch {
            when (val result = deps.api.login(current.username.trim(), current.password)) {
                is ApiResult.Success -> onLoginSuccess(result.value, normalizedBase)
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    private fun onLoginSuccess(user: UserInfo, apiBase: String) {
        val username = user.username.ifBlank { _state.value.username.trim() }
        deps.settings.putString(SettingsKeys.USERNAME, username)
        deps.settings.putString(SettingsKeys.API_BASE, apiBase)
        deps.session.updateAuthState(AuthState.LoggedIn(username = username, uid = user.uid))
        _state.update { it.copy(isLoading = false, password = "", error = null) }
    }
}
