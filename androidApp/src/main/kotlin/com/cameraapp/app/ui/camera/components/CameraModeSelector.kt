package com.cameraapp.app.ui.camera.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cameraapp.app.ui.camera.CameraMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ANIM_SPEC = tween<Float>(180)

// Pre-built gradient — allocated once, not per frame
private val EDGE_FADE_BRUSH = Brush.horizontalGradient(
    0f to Color.Black,
    0.12f to Color.Transparent,
    0.88f to Color.Transparent,
    1f to Color.Black
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraModeSelector(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val modes = remember { CameraMode.values() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val screenHalfDp = LocalConfiguration.current.screenWidthDp / 2

    val scrollLock = remember { object { @Volatile var locked = false } }

    val snapProvider = remember(listState) {
        object : SnapLayoutInfoProvider {
            override fun Density.calculateSnapStepSize(): Float = 0f
            override fun Density.calculateApproachOffset(initialVelocity: Float): Float = 0f
            override fun Density.calculateSnappingOffset(currentVelocity: Float): Float {
                val info = listState.layoutInfo
                val items = info.visibleItemsInfo
                if (items.isEmpty()) return 0f
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                val closest = items.minBy { abs(it.offset + it.size / 2 - center) }
                return (closest.offset + closest.size / 2 - center).toFloat()
            }
        }
    }

    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return@derivedStateOf modes.indexOf(currentMode)
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            items.minBy { abs(it.offset + it.size / 2 - center) }.index
        }
    }

    // Single scroll helper — visible items get one smooth motion, off-screen items get two-step
    suspend fun scrollToCenter(index: Int, animate: Boolean) {
        val item = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
        if (item != null) {
            val info = listState.layoutInfo
            val dist = (item.offset + item.size / 2 - (info.viewportStartOffset + info.viewportEndOffset) / 2).toFloat()
            if (animate) listState.animateScrollBy(dist) else listState.scrollBy(dist)
        } else {
            if (animate) listState.animateScrollToItem(index) else listState.scrollToItem(index)
            listState.layoutInfo.visibleItemsInfo.find { it.index == index }?.let { settled ->
                val info = listState.layoutInfo
                val correction = (settled.offset + settled.size / 2 - (info.viewportStartOffset + info.viewportEndOffset) / 2).toFloat()
                if (animate) listState.animateScrollBy(correction) else listState.scrollBy(correction)
            }
        }
    }

    // Initial centering
    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.size }.first { it > 0 }
        scrollLock.locked = true
        scrollToCenter(modes.indexOf(currentMode), animate = false)
        scrollLock.locked = false
    }

    // User scroll → state sync
    LaunchedEffect(centerIndex) {
        if (!scrollLock.locked) {
            val newMode = modes[centerIndex]
            if (newMode != currentMode) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onModeSelected(newMode)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(snapProvider),
            contentPadding = PaddingValues(horizontal = screenHalfDp.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().drawWithContent {
                drawContent()
                drawRect(brush = EDGE_FADE_BRUSH)
            }
        ) {
            itemsIndexed(modes) { index, mode ->
                val selected = centerIndex == index
                val scale by animateFloatAsState(if (selected) 1f else 0.85f, ANIM_SPEC, label = "s")
                val alpha by animateFloatAsState(if (selected) 1f else 0.35f, ANIM_SPEC, label = "a")

                Text(
                    text = mode.title,
                    color = if (selected) Color.Yellow else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = if (selected) 0.5.sp else 0.sp,
                    softWrap = false,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (mode != currentMode && !scrollLock.locked) {
                                scope.launch {
                                    scrollLock.locked = true
                                    onModeSelected(mode)
                                    scrollToCenter(index, animate = true)
                                    scrollLock.locked = false
                                }
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(4.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-2).dp)
                .background(Color.Yellow.copy(alpha = 0.8f), CircleShape)
        )
    }
}
