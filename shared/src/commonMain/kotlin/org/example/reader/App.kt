package org.example.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.reader.core.AppDependencies
import org.example.reader.core.session.AuthState
import org.example.reader.ui.components.FullScreenLoading
import org.example.reader.ui.login.LoginScreen
import org.example.reader.ui.nav.MainShell
import org.example.reader.ui.theme.ReaderTheme

/**
 * 应用根组合。
 *
 * 登录态是唯一的路由分叉点：未登录只有登录页，登录后进主框架。
 * 会话失效（刷新令牌也过期）由传输层回调把状态打回 LoggedOut，
 * 这里会自动切回登录页，不需要页面各自处理。
 */
@Composable
fun App(deps: AppDependencies) {
    ReaderTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val authState by deps.session.authState.collectAsStateWithLifecycle()
            when (authState) {
                AuthState.Unknown -> FullScreenLoading()
                AuthState.LoggedOut -> LoginScreen(deps)
                is AuthState.LoggedIn -> MainShell(deps)
            }
        }
    }
}
