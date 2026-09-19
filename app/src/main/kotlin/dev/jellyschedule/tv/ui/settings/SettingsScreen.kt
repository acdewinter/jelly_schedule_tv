package dev.jellyschedule.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.jellyschedule.tv.data.model.LiveMode
import dev.jellyschedule.tv.ui.common.FocusableCard
import dev.jellyschedule.tv.ui.common.SectionTitle
import dev.jellyschedule.tv.ui.common.TvButton
import dev.jellyschedule.tv.ui.common.graphViewModel
import dev.jellyschedule.tv.ui.common.rememberInitialFocus
import dev.jellyschedule.tv.ui.theme.JellyColors

@Composable
fun SettingsScreen(onBack: () -> Unit, onChangeServer: () -> Unit) {
    val vm = graphViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = rememberInitialFocus()
    val household = state.household
    val settings = household?.settings

    Row(Modifier.fillMaxSize().background(JellyColors.Background).padding(horizontal = 48.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        // Left: things you can change here.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))

            SectionTitle("Server and account")
            InfoRow("Server", listOfNotNull(state.session.serverName, state.session.serverUrl).joinToString("  ·  "))
            InfoRow("Signed in as", state.session.userName ?: "—")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Sign out", onClick = vm::signOut)
                TvButton("Change server", onClick = { vm.forgetServer(); onChangeServer() })
            }
            Spacer(Modifier.height(12.dp))

            SectionTitle("Playback")
            ToggleRow(
                title = "Autoplay on launch",
                subtitle = "Start playing what is on as soon as the app opens (the household setting must allow it too).",
                checked = state.preferences.autoplayOnLaunch,
                onToggle = vm::toggleAutoplay,
                modifier = Modifier.focusRequester(focus),
            )
            FocusableCard(onClick = { vm.nextSubtitleLanguage() }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Preferred subtitle language", style = MaterialTheme.typography.titleSmall)
                        Text("Press to change. Subtitles in this language are switched on automatically when the file has them.", style = MaterialTheme.typography.bodySmall, color = JellyColors.Muted)
                    }
                    Text(state.subtitleLanguageLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            ToggleRow(
                title = "Always transcode",
                subtitle = "Ask the server for an HLS stream instead of direct play. Use this if files stutter or show a black screen.",
                checked = state.preferences.alwaysTranscode,
                onToggle = vm::toggleAlwaysTranscode,
            )
            Spacer(Modifier.height(12.dp))

            SectionTitle("This TV")
            val caps = state.capabilities
            if (caps != null) {
                InfoRow("Video decoders", caps.videoCodecs.joinToString(", ").ifBlank { "none detected" })
                InfoRow("Audio decoders", caps.audioCodecs.joinToString(", "))
                InfoRow("HDR", caps.hdrTypes.joinToString(", ").ifBlank { "SDR only" } + if (caps.hevcMain10) " · HEVC 10-bit" else "")
            }
            InfoRow("App version", state.appVersion)
            Spacer(Modifier.height(8.dp))
            TvButton("Back", onClick = onBack)
        }

        // Right: the household settings, read only.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Household schedule", style = MaterialTheme.typography.headlineSmall)
            Text(
                "These are set in the Jelly Schedule web app at ${state.session.serverUrl}/JellySchedule/app.",
                style = MaterialTheme.typography.bodySmall,
                color = JellyColors.Muted,
            )
            Spacer(Modifier.height(8.dp))
            if (household == null || settings == null) {
                Text("Not loaded yet.", style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted)
            } else {
                InfoRow("Household user", household.householdUser?.name ?: "not set")
                InfoRow(
                    "Live mode",
                    if (settings.liveMode == LiveMode.Strict) "Strict: join in progress, like broadcast TV" else "Relaxed: programmes start from the beginning or resume",
                )
                InfoRow("Slots", "${settings.slotMinutes} minutes")
                InfoRow(
                    "Leftover time",
                    when (settings.fillMode) {
                        "Nothing" -> "Leave it off air"
                        "MoreEpisodes" -> "More episodes of first-run shows, then re-runs"
                        else -> "Fill with re-runs"
                    },
                )
                InfoRow("Re-run picker", settings.rerunPicker)
                InfoRow("Specials", if (settings.includeSpecials) "Included" else "Skipped")
                InfoRow("Autoplay on open", if (settings.autoplayOnOpen) "On" else "Off")
                InfoRow("Time zone", household.serverTimeZone.ifBlank { "server zone" })
                InfoRow("Editing", if (household.canEdit) "You can change the schedule" else "Only the household user or an admin can edit")
                Spacer(Modifier.height(8.dp))
                SectionTitle("Viewing windows")
                if (household.windows.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted)
                household.windows.forEach { w ->
                    InfoRow(w.days.joinToString(", ") { it.take(3) }, "${w.start}–${w.end}" + (w.label?.let { "  ·  $it" } ?: ""))
                }
                if (household.movieNight.days.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("Movie night")
                    InfoRow(
                        household.movieNight.days.joinToString(", ") { it.take(3) },
                        (household.movieNight.startTime?.let { "at $it" } ?: "at the ${household.movieNight.position.lowercase()} of the window") + " · ${household.movieNight.order}",
                    )
                }
                Spacer(Modifier.height(8.dp))
                SectionTitle("Lineup")
                if (household.lineup.isEmpty()) Text("Nothing in the lineup yet.", style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted)
                household.lineup.sortedBy { it.order }.forEach { e ->
                    InfoRow(
                        e.name,
                        listOfNotNull(
                            e.kind,
                            if (e.mode == "ReRun") "re-runs" else "in order",
                            e.days.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.take(3) },
                            if (e.episodesPerAiring > 1) "${e.episodesPerAiring} episodes" else null,
                            if (e.paused) "paused" else null,
                        ).joinToString(" · "),
                    )
                }
                if (household.blackouts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("Away")
                    household.blackouts.forEach { b -> InfoRow(b.label, "${b.from} – ${b.to}") }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted, modifier = Modifier.weight(0.42f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FocusableCard(onClick = onToggle, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = JellyColors.Muted)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
