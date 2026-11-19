package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.domain.model.PatternValidator
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.PureWhite
import kotlin.math.hypot

/**
 * Interactive 3x3 Obsidian-Purple tactile pattern lock pad.
 * Automatically connects collinear intermediate dots (e.g., 0 -> 2 includes 1)
 * and responds cleanly to both finger gestures and automated touch tests.
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    previewNodes: List<Int>? = null,
    resetKey: Any? = Unit,
    testTag: String = "pattern_lock_pad",
    onPatternStart: () -> Unit = {},
    onPatternProgress: (List<Int>) -> Unit = {},
    onPatternComplete: (List<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val selectedNodes = remember { mutableStateListOf<Int>() }
    var activeFingerOffset by remember { mutableStateOf<Offset?>(null) }

    val currentOnStart by rememberUpdatedState(onPatternStart)
    val currentOnProgress by rememberUpdatedState(onPatternProgress)
    val currentOnComplete by rememberUpdatedState(onPatternComplete)

    LaunchedEffect(resetKey) {
        selectedNodes.clear()
        activeFingerOffset = null
    }

    fun nodeCenter(index: Int, canvasSize: IntSize): Offset {
        val col = index % PatternValidator.GRID_SIZE
        val row = index / PatternValidator.GRID_SIZE
        val cellW = canvasSize.width / 3f
        val cellH = canvasSize.height / 3f
        return Offset(
            x = cellW * (col + 0.5f),
            y = cellH * (row + 0.5f)
        )
    }

    fun hitTestNode(point: Offset, canvasSize: IntSize): Int? {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return null
        val minCell = minOf(canvasSize.width, canvasSize.height) / 3f
        val hitRadius = minCell * 0.36f
        for (index in 0..8) {
            val center = nodeCenter(index, canvasSize)
            if (hypot((point.x - center.x).toDouble(), (point.y - center.y).toDouble()) <= hitRadius) {
                return index
            }
        }
        return null
    }

    fun recordHit(point: Offset, canvasSize: IntSize) {
        val hit = hitTestNode(point, canvasSize) ?: return
        if (!selectedNodes.contains(hit)) {
            val updated = PatternValidator.appendNodeWithMidpoint(selectedNodes.toList(), hit)
            if (updated.size > selectedNodes.size) {
                selectedNodes.clear()
                selectedNodes.addAll(updated)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnProgress(updated)
            }
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .aspectRatio(1f)
            .semantics { contentDescription = "3 by 3 pattern unlock grid" }
            .testTag(testTag)
            .pointerInput(enabled, resetKey) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selectedNodes.clear()
                    activeFingerOffset = down.position
                    currentOnStart()
                    recordHit(down.position, size)

                    drag(down.id) { change ->
                        change.consume()
                        activeFingerOffset = change.position
                        recordHit(change.position, size)
                    }

                    activeFingerOffset = null
                    if (selectedNodes.isNotEmpty()) {
                        val completed = selectedNodes.toList()
                        currentOnComplete(completed)
                        selectedNodes.clear()
                    }
                }
            }
    ) {
        val displayedNodes = if (selectedNodes.isNotEmpty()) {
            selectedNodes.toList()
        } else {
            previewNodes.orEmpty()
        }
        val activeColor = if (isError) CrimsonAlert else NeonViolet
        val lineGlowColor = if (isError) CrimsonAlert else ElectricPurple

        Canvas(modifier = Modifier.fillMaxSize()) {
            val intSize = IntSize(size.width.toInt(), size.height.toInt())
            val cellMin = minOf(size.width, size.height) / 3f
            val outerRingRadius = cellMin * 0.25f
            val coreRadius = cellMin * 0.08f

            // Draw connecting path lines
            if (displayedNodes.isNotEmpty()) {
                val path = Path()
                displayedNodes.forEachIndexed { idx, node ->
                    val center = nodeCenter(node, intSize)
                    if (idx == 0) path.moveTo(center.x, center.y) else path.lineTo(center.x, center.y)
                }
                val livePointer = activeFingerOffset
                if (livePointer != null && selectedNodes.isNotEmpty()) {
                    path.lineTo(livePointer.x, livePointer.y)
                }

                // Soft outer aura stroke
                drawPath(
                    path = path,
                    color = lineGlowColor.copy(alpha = 0.28f),
                    style = Stroke(
                        width = 16.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                // Crisp inner gradient beam
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        listOf(lineGlowColor, activeColor)
                    ),
                    style = Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw 9 grid nodes
            for (index in 0..8) {
                val center = nodeCenter(index, intSize)
                val isSelected = displayedNodes.contains(index)

                if (isSelected) {
                    // Radial active halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                lineGlowColor.copy(alpha = 0.42f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outerRingRadius * 1.45f
                        ),
                        radius = outerRingRadius * 1.45f,
                        center = center
                    )
                    drawCircle(
                        color = ObsidianElevated,
                        radius = outerRingRadius,
                        center = center
                    )
                    drawCircle(
                        color = activeColor,
                        radius = outerRingRadius,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        color = PureWhite,
                        radius = coreRadius * 1.3f,
                        center = center
                    )
                } else {
                    drawCircle(
                        color = ObsidianElevated.copy(alpha = 0.8f),
                        radius = outerRingRadius * 0.85f,
                        center = center
                    )
                    drawCircle(
                        color = if (isError) CrimsonAlert.copy(alpha = 0.45f) else ObsidianBorder,
                        radius = outerRingRadius * 0.85f,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = if (isError) CrimsonAlert.copy(alpha = 0.7f) else NeonViolet.copy(alpha = 0.65f),
                        radius = coreRadius * 0.85f,
                        center = center
                    )
                }
            }
        }
    }
}
