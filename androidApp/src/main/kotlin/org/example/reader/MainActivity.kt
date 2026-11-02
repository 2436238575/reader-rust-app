package org.example.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.example.reader.core.AndroidDependencies

class MainActivity : ComponentActivity() {

    private lateinit var dependencies: AndroidDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 依赖挂在 Activity 上而不是全局单例：进程被杀后重建能重新装配，
        // 且 Cookie/SharedPreferences 都有磁盘持久化，重建不丢登录态。
        dependencies = AndroidDependencies(applicationContext)

        setContent {
            App(dependencies)
        }
    }
}
