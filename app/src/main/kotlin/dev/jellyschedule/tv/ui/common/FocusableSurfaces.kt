package dev.jellyschedule.tv.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import dev.jellyschedule.tv.ui.theme.JellyColors

/** A focusable, clickable row/card with the app's focus treatment (light surface, slight scale). */
@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    corner: Int = 12,
    containerColor: Color = if (selected) JellyColors.Primary.copy(alpha = 0.28f) else JellyColors.Surface.copy(alpha = 0.7f),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(corner.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = JellyColors.Text,
            focusedContainerColor = Color.White,
            focusedContentColor = JellyColors.Background,
            pressedContainerColor = Color.White,
            pressedContentColor = JellyColors.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        content = content,
    )
}
