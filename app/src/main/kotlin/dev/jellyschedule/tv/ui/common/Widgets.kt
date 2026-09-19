package dev.jellyschedule.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellyschedule.tv.appGraph
import dev.jellyschedule.tv.di.AppGraph
import dev.jellyschedule.tv.domain.AiringBadge
import dev.jellyschedule.tv.ui.theme.JellyColors

/** Creates a ViewModel wired to the app graph, scoped to the current navigation entry. */
@Composable
inline fun <reified VM : ViewModel> graphViewModel(crossinline create: (AppGraph) -> VM): VM {
    val graph = LocalContext.current.appGraph
    return viewModel { create(graph) }
}

/** Requests focus once when the composable enters the composition. Every screen has one obvious first focus. */
@Composable
fun rememberInitialFocus(vararg keys: Any?): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(*keys) { runCatching { requester.requestFocus() } }
    return requester
}

@Composable
fun PosterImage(url: String?, modifier: Modifier = Modifier, title: String = "", corner: Int = 10) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(JellyColors.SurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = title.take(24),
                style = MaterialTheme.typography.titleSmall,
                color = JellyColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** A dimmed (and on Android 12+ blurred) backdrop behind a screen's content. */
@Composable
fun BackdropImage(url: String?, modifier: Modifier = Modifier, blurred: Boolean = true, dim: Float = 0.62f) {
    Box(modifier = modifier.background(JellyColors.Background)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().then(if (blurred) Modifier.blur(18.dp) else Modifier),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(JellyColors.Background.copy(alpha = 0.96f), JellyColors.Background.copy(alpha = dim))),
            ),
        )
    }
}

@Composable
fun BadgeChip(badge: AiringBadge, modifier: Modifier = Modifier) {
    val (bg, fg) = when (badge) {
        AiringBadge.OnNow -> JellyColors.Live to Color.White
        AiringBadge.Movie -> JellyColors.Movie to Color.White
        AiringBadge.ReRun -> JellyColors.ReRun to Color.White
        AiringBadge.OneOff -> JellyColors.Primary to Color.White
        AiringBadge.SeriesFinale, AiringBadge.SeasonFinale -> JellyColors.Warn to Color.Black
        AiringBadge.Premiere -> JellyColors.Ok to Color.Black
        AiringBadge.Recorded -> JellyColors.Rec to Color.White
        AiringBadge.Watched -> JellyColors.Ok.copy(alpha = 0.25f) to JellyColors.Ok
        AiringBadge.Missed -> JellyColors.Warn.copy(alpha = 0.25f) to JellyColors.Warn
        AiringBadge.InProgress, AiringBadge.RunsLate -> JellyColors.SurfaceVariant to JellyColors.Text
    }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(badge.label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BadgeRow(badges: List<AiringBadge>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) { badges.forEach { BadgeChip(it) } }
}

/** The red "LIVE" / "ON NOW" pill. */
@Composable
fun LivePill(text: String, modifier: Modifier = Modifier, color: Color = JellyColors.Live) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val m = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            colors = ButtonDefaults.colors(
                containerColor = JellyColors.Primary,
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = JellyColors.PrimaryDark,
            ),
        ) { content() }
    } else {
        OutlinedButton(onClick = onClick, modifier = m, enabled = enabled) { content() }
    }
}

@Composable
fun LoadingScreen(message: String = "Loading…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(JellyColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spinner()
            Text(message, style = MaterialTheme.typography.bodyLarge, color = JellyColors.Muted)
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Int = 48) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier.size(size.dp),
        color = JellyColors.Primary,
        trackColor = JellyColors.SurfaceVariant,
        strokeWidth = 5.dp,
    )
}

data class ButtonSpec(val text: String, val onClick: () -> Unit, val icon: ImageVector? = null)

@Composable
fun ErrorScreen(title: String, message: String, primary: ButtonSpec, secondary: ButtonSpec? = null, tertiary: ButtonSpec? = null) {
    val focus = rememberInitialFocus(title)
    Box(Modifier.fillMaxSize().background(JellyColors.Background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 720.dp).padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = JellyColors.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(primary.text, primary.onClick, icon = primary.icon, primary = true, focusRequester = focus)
                if (secondary != null) TvButton(secondary.text, secondary.onClick, icon = secondary.icon)
                if (tertiary != null) TvButton(tertiary.text, tertiary.onClick, icon = tertiary.icon)
            }
        }
    }
}

/** A full-width notice, used for the household-user mismatch and schedule warnings. */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, color: Color = JellyColors.Warn) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = JellyColors.Text)
    }
}

/** A thin progress bar. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, buffered: Float = 0f, color: Color = JellyColors.Primary) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        if (buffered > 0f) Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).height(6.dp).background(Color.White.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = JellyColors.Muted, modifier = modifier)
}

@Composable
fun Gap(width: Int = 0, height: Int = 0) {
    Spacer(Modifier.width(width.dp).height(height.dp))
}
