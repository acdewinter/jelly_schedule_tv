package dev.jellyschedule.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dev.jellyschedule.tv.ui.nav.AppRoot
import dev.jellyschedule.tv.ui.theme.JellyColors
import dev.jellyschedule.tv.ui.theme.JellyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JellyTheme {
                Box(Modifier.fillMaxSize().background(JellyColors.Background)) {
                    AppRoot()
                }
            }
        }
    }
}
