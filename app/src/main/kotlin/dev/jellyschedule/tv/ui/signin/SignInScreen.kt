package dev.jellyschedule.tv.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jellyschedule.tv.ui.common.Spinner
import dev.jellyschedule.tv.ui.common.TvButton
import dev.jellyschedule.tv.ui.common.graphViewModel
import dev.jellyschedule.tv.ui.common.rememberInitialFocus
import dev.jellyschedule.tv.ui.theme.JellyColors

@Composable
fun SignInScreen(onChangeServer: () -> Unit) {
    val vm = graphViewModel { SignInViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = rememberInitialFocus(state.quickConnectWaiting)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = JellyColors.Primary,
        unfocusedBorderColor = JellyColors.Border,
        cursorColor = JellyColors.Primary,
        focusedContainerColor = JellyColors.Surface,
        unfocusedContainerColor = JellyColors.Surface,
    )
    val textStyle = TextStyle(color = JellyColors.Text, fontSize = 22.sp)

    Row(Modifier.fillMaxSize().background(JellyColors.Background).padding(64.dp)) {
        Column(Modifier.weight(1f).padding(end = 48.dp), verticalArrangement = Arrangement.Center) {
            Text("Sign in", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(state.serverName, style = MaterialTheme.typography.titleLarge)
            Text(state.serverUrl, style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted)
            Spacer(Modifier.height(24.dp))
            Text(
                "Any Jellyfin user can sign in. The channel follows the household user's watch history; a banner tells you when that is somebody else.",
                style = MaterialTheme.typography.bodyMedium,
                color = JellyColors.Muted,
            )
            Spacer(Modifier.height(24.dp))
            TvButton("Change server", onClick = onChangeServer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            if (state.quickConnectWaiting) {
                Text("Quick Connect", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Open Jellyfin on your phone or computer, go to Settings › Quick Connect and enter this code:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JellyColors.Muted,
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        state.quickConnectCode ?: "……",
                        style = MaterialTheme.typography.displayMedium,
                        color = JellyColors.Primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spinner(size = 32)
                }
                Spacer(Modifier.height(20.dp))
                TvButton("Cancel", onClick = vm::cancelQuickConnect, focusRequester = focus)
            } else {
                Text("User name", style = MaterialTheme.typography.titleSmall, color = JellyColors.Muted)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.username,
                    onValueChange = vm::setUsername,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    textStyle = textStyle,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(14.dp))
                Text("Password", style = MaterialTheme.typography.titleSmall, color = JellyColors.Muted)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::setPassword,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = textStyle,
                    colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.signIn() }),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton("Sign in", onClick = vm::signIn, primary = true, enabled = !state.busy)
                    TvButton("Quick Connect", onClick = vm::startQuickConnect, icon = Icons.Default.PhoneAndroid, enabled = !state.busy)
                    if (state.busy) Spinner(size = 28)
                }
            }
            if (state.error != null) {
                Spacer(Modifier.height(16.dp))
                Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = JellyColors.Live)
            }
        }
    }
}
