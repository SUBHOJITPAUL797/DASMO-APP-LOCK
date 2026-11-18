package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun Modifier.tactileScale(
    pressed: Boolean,
    pressedScale: Float = 0.965f
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tactile_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun Tactile3DCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val topBorderColor by animateColorAsState(
        targetValue = if (highlighted) NeonViolet.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.16f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_top_border"
    )
    val bottomBorderColor by animateColorAsState(
        targetValue = if (highlighted) DeepViolet.copy(alpha = 0.8f) else ObsidianBorder.copy(alpha = 0.65f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_bottom_border"
    )

    val bgGradient = if (highlighted) {
        Brush.verticalGradient(
            listOf(Color(0xFF281748), Color(0xFF170F2B), Color(0xFF100A1F))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFF1D1533), Color(0xFF130E22), Color(0xFF0D0918))
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier
            .tactileScale(pressed = isPressed)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = NeonViolet.copy(alpha = 0.25f)),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .shadow(
                elevation = if (highlighted) 14.dp else 8.dp,
                shape = shape,
                ambientColor = ElectricPurple.copy(alpha = 0.35f),
                spotColor = ElectricPurple.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(bgGradient, shape)
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(topBorderColor, bottomBorderColor))
                ),
                shape = shape
            )
            .then(clickableModifier),
        content = content
    )
}

@Composable
fun Icon3DBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    accentStart: Color = ElectricPurple,
    accentEnd: Color = Color(0xFF4C1D95),
    iconTint: Color = PureWhite
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = accentStart.copy(alpha = 0.5f),
                spotColor = accentStart.copy(alpha = 0.65f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(accentStart, accentEnd, Color(0xFF1E0B3B)),
                    start = Offset(0f, 0f),
                    end = Offset(120f, 120f)
                )
            )
            .drawWithContent {
                drawContent()
                // Soft top-left specular bevel highlight for 3D depth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(this.size.width * 0.28f, this.size.height * 0.22f),
                        radius = this.size.width * 0.55f
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.38f), Color.Black.copy(alpha = 0.45f))
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
fun Section3DHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentStart: Color = ElectricPurple,
    accentEnd: Color = DeepViolet
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon3DBadge(
            icon = icon,
            contentDescription = null,
            size = 40.dp,
            accentStart = accentStart,
            accentEnd = accentEnd
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentStart, CircleShape)
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
fun TactileSettingSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    accentStart: Color = ElectricPurple,
    accentEnd: Color = DeepViolet,
    badgeText: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Tactile3DCard(
        highlighted = checked,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onCheckedChange(!checked)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon3DBadge(
                icon = icon,
                contentDescription = null,
                size = 42.dp,
                accentStart = if (checked) accentStart else Color(0xFF3E2C66),
                accentEnd = if (checked) accentEnd else Color(0xFF1F1635)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PureWhite
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (checked) accentStart.copy(alpha = 0.22f) else ObsidianSurfaceVariant,
                            shape = CircleShape,
                            border = BorderStroke(
                                1.dp,
                                if (checked) accentStart.copy(alpha = 0.6f) else ObsidianBorder
                            )
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (checked) LavenderGlow else TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = { next ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(next)
                },
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
