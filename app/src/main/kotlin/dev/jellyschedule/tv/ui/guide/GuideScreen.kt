package dev.jellyschedule.tv.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.GuideDay
import dev.jellyschedule.tv.domain.GuideWeeks
import dev.jellyschedule.tv.domain.TimeText
import dev.jellyschedule.tv.domain.badges
import dev.jellyschedule.tv.domain.displaySubtitle
import dev.jellyschedule.tv.domain.displayTitle
import dev.jellyschedule.tv.domain.formatMinutes
import dev.jellyschedule.tv.domain.posterItemId
import dev.jellyschedule.tv.ui.common.BadgeRow
import dev.jellyschedule.tv.ui.common.ButtonSpec
import dev.jellyschedule.tv.ui.common.ErrorScreen
import dev.jellyschedule.tv.ui.common.FocusableCard
import dev.jellyschedule.tv.ui.common.InfoBanner
import dev.jellyschedule.tv.ui.common.LoadingScreen
import dev.jellyschedule.tv.ui.common.PosterImage
import dev.jellyschedule.tv.ui.common.TvButton
import dev.jellyschedule.tv.ui.common.graphViewModel
import dev.jellyschedule.tv.ui.common.rememberInitialFocus
import dev.jellyschedule.tv.ui.nav.PlaybackMode
import dev.jellyschedule.tv.ui.theme.JellyColors
import java.util.Locale

@Composable
fun GuideScreen(onOpenDetails: (Airing) -> Unit, onPlay: (Airing, PlaybackMode) -> Unit, onBack: () -> Unit) {
    val vm = graphViewModel { GuideViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Coming back from the details screen (after record / watched) refreshes the week.
    LaunchedEffect(lifecycleOwner) {
        var first = true
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!first) vm.load(silent = true)
            first = false
        }
    }

    when {
        state.loading && state.guide == null -> LoadingScreen("Loading the guide…")
        state.error != null && state.guide == null -> ErrorScreen(
            title = "Can't load the guide",
            message = state.error!!,
            primary = ButtonSpec("Try again", onClick = { vm.load() }),
            secondary = ButtonSpec("Back", onBack),
        )
        else -> GuideContent(state, vm, onOpenDetails)
    }
}

@Composable
private fun GuideContent(state: GuideUiState, vm: GuideViewModel, onOpenDetails: (Airing) -> Unit) {
    val guide = state.guide ?: return
    val day = state.selectedDay
    val dayFocus = rememberInitialFocus(state.weekIndex, guide.from)

    Column(Modifier.fillMaxSize().background(JellyColors.Background).padding(horizontal = 48.dp, vertical = 28.dp)) {
        // Header: title, week switch, stats.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Guide", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.width(8.dp))
            TvButton("This week", onClick = { vm.selectWeek(0) }, primary = state.weekIndex == 0)
            TvButton("Next week", onClick = { vm.selectWeek(1) }, primary = state.weekIndex == 1)
            Spacer(Modifier.weight(1f))
            val s = guide.stats
            Text(
                buildString {
                    append("${s.programmes} programmes · ${formatMinutes(s.scheduledMinutes)} · ${s.episodes} episodes · ${s.movies} movie${if (s.movies == 1) "" else "s"}")
                    if (s.watched > 0) append(" · ${s.watched} watched")
                    if (s.missed > 0) append(" · ${s.missed} missed")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = JellyColors.Muted,
            )
        }
        if (state.householdMismatch) {
            Spacer(Modifier.height(10.dp))
            InfoBanner("This channel follows the household user's watch history, not yours.")
        }
        if (guide.isEmpty) {
            Spacer(Modifier.height(10.dp))
            InfoBanner("Your channel is empty. Set viewing times and a lineup in the web app at ${state.serverUrl}/JellySchedule/app.", color = JellyColors.Primary)
        } else if (!guide.warning.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            InfoBanner(guide.warning)
        }
        Spacer(Modifier.height(16.dp))

        // Day tabs: focusing a day selects it, so left / right browses the week.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            guide.days.forEach { d ->
                val selected = d.date == state.selectedDate
                val modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) vm.selectDay(d.date) }
                    .then(if (selected) Modifier.focusRequester(dayFocus) else Modifier)
                DayTab(d, selected = selected, modifier = modifier, onClick = { vm.selectDay(d.date) })
            }
        }
        Spacer(Modifier.height(14.dp))

        if (day != null) DayList(day, vm, onOpenDetails)
    }
}

@Composable
private fun DayTab(day: GuideDay, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val status = GuideWeeks.status(day)
    val muted = day.isPast && !day.isToday
    FocusableCard(onClick = onClick, modifier = modifier, selected = selected, corner = 10) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    TimeText.dayShort(day.date.dayOfWeek, Locale.getDefault()).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (day.isToday) JellyColors.Primary else if (muted) JellyColors.Muted else androidx.tv.material3.LocalContentColor.current,
                )
                if (day.isToday) Text("TODAY", style = MaterialTheme.typography.labelSmall, color = JellyColors.Primary, fontWeight = FontWeight.Bold)
            }
            Text("${day.date.dayOfMonth} ${day.date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())}", style = MaterialTheme.typography.titleSmall)
            Text(
                when (status) {
                    is GuideWeeks.DayStatus.Away -> status.label
                    is GuideWeeks.DayStatus.OffAir -> "Off air"
                    is GuideWeeks.DayStatus.Scheduled -> "${status.programmes} programme${if (status.programmes == 1) "" else "s"}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (muted) JellyColors.Muted.copy(alpha = 0.7f) else JellyColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DayList(day: GuideDay, vm: GuideViewModel, onOpenDetails: (Airing) -> Unit) {
    val status = GuideWeeks.status(day)
    when (status) {
        is GuideWeeks.DayStatus.Away -> EmptyDay("${status.label} — nothing scheduled.")
        is GuideWeeks.DayStatus.OffAir -> EmptyDay(
            if (status.hasWindows) "Off air — nothing left to schedule in this window." else "Off air — no viewing window on this day.",
        )
        is GuideWeeks.DayStatus.Scheduled -> {
            val listState = rememberLazyListState()
            val windows = day.windows.joinToString("  ·  ") { w -> (w.label?.let { "$it " } ?: "") + "${TimeText.wallTime(w.start)}–${TimeText.wallTime(w.end)}" }
            if (windows.isNotBlank()) {
                Text(windows, style = MaterialTheme.typography.bodySmall, color = JellyColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(day.airings, key = { it.id }) { airing ->
                    AiringRow(airing, vm, onClick = { onOpenDetails(airing) })
                }
            }
        }
    }
}

@Composable
private fun EmptyDay(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = JellyColors.Muted)
    }
}

@Composable
fun AiringRow(airing: Airing, vm: GuideViewModel, onClick: () -> Unit) {
    val past = airing.end.toInstant().isBefore(vm.serverNow()) && !airing.isOnNow
    FocusableCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), containerColor = if (airing.isOnNow) JellyColors.Live.copy(alpha = 0.18f) else JellyColors.Surface.copy(alpha = if (past) 0.4f else 0.7f)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(TimeText.wallTime(airing.start), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(72.dp))
            PosterImage(vm.imageUrl(airing.posterItemId, maxHeight = 200), Modifier.size(56.dp, 84.dp), title = airing.displayTitle, corner = 6)
            Column(Modifier.weight(1f)) {
                Text(airing.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(airing.displaySubtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                BadgeRow(airing.badges(compact = true))
            }
            Text("${formatMinutes(airing.runtimeMinutes)}", style = MaterialTheme.typography.bodySmall)
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
