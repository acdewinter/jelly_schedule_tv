package dev.jellyschedule.tv.ui.settings

import androidx.compose.runtime.Composable
import dev.jellyschedule.tv.ui.common.ButtonSpec
import dev.jellyschedule.tv.ui.common.ErrorScreen

@Composable
fun SettingsScreen(onBack: () -> Unit, onChangeServer: () -> Unit) {
    ErrorScreen("Settings", "Settings arrive in a later milestone.", ButtonSpec("Back", onBack), ButtonSpec("Change server", onChangeServer))
}
