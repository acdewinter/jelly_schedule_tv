package dev.jellyschedule.tv.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jellyschedule.tv.ui.common.Spinner
import dev.jellyschedule.tv.ui.common.TvButton
import dev.jellyschedule.tv.ui.common.graphViewModel
import dev.jellyschedule.tv.ui.common.rememberInitialFocus
import dev.jellyschedule.tv.ui.theme.JellyColors

@Composable
fun ConnectScreen(onConnected: () -> Unit) {
    val vm = graphViewModel { ConnectViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = rememberInitialFocus()

    LaunchedEffect(Unit) {
        vm.events.collect { if (it is ConnectEvent.Connected) onConnected() }
    }

    Row(Modifier.fillMaxSize().background(JellyColors.Background).padding(64.dp)) {
        Column(Modifier.weight(1f).padding(end = 48.dp), verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = JellyColors.Primary, modifier = Modifier.height(64.dp).width(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Jelly Schedule", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your Jellyfin library as a TV channel. Connect to the Jellyfin server that has the Jelly Schedule plugin installed.",
                style = MaterialTheme.typography.bodyLarge,
                color = JellyColors.Muted,
            )
            Spacer(Modifier.height(24.dp))
            Text("Tip: the address is the same one you use in a browser, for example http://192.168.1.10:8096", style = MaterialTheme.typography.bodySmall, color = JellyColors.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Server address", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.address,
                onValueChange = vm::setAddress,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                placeholder = { androidx.compose.material3.Text("http://jellyfin.local:8096", color = JellyColors.Muted) },
                textStyle = androidx.compose.ui.text.TextStyle(color = JellyColors.Text, fontSize = 22.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.connect() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JellyColors.Primary,
                    unfocusedBorderColor = JellyColors.Border,
                    cursorColor = JellyColors.Primary,
                    focusedContainerColor = JellyColors.Surface,
                    unfocusedContainerColor = JellyColors.Surface,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvButton("Connect", onClick = { vm.connect() }, primary = true, enabled = !state.connecting)
                TvButton("Find servers", onClick = vm::discover, icon = Icons.Default.Search, enabled = !state.discovering)
                if (state.connecting || state.discovering) Spinner(size = 28)
            }
            if (state.error != null) {
                Spacer(Modifier.height(16.dp))
                Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = JellyColors.Live)
            }
            if (state.discovered.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Found on your network", style = MaterialTheme.typography.titleSmall, color = JellyColors.Muted)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.discovered.forEach { server ->
                        TvButton("${server.name}  ·  ${server.address}", onClick = { vm.connect(server.address) })
                    }
                }
            } else if (!state.discovering && state.discovered.isEmpty() && state.error == null) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "Sign in with any Jellyfin account. The schedule follows the household user chosen in the plugin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JellyColors.Muted,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(1.dp).background(Color.Transparent))
        }
    }
}
