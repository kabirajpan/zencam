package com.cameraapp.app.ui.camera.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraModeSelector(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val modes = CameraMode.values()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val scrollLock = remember { object { var locked = false } }

    // Center-snapping for Compose 1.5.4
    val snapLayoutInfoProvider = remember(listState, density) {
        object : SnapLayoutInfoProvider {
            override fun Density.calculateSnapStepSize(): Float = 0f
            override fun Density.calculateApproachOffset(initialVelocity: Float): Float = 0f
            override fun Density.calculateSnappingOffset(currentVelocity: Float): Float {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return 0f

                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closestItem = visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    abs(itemCenter - viewportCenter)
                } ?: return 0f

                return (closestItem.offset + closestItem.size / 2 - viewportCenter).toFloat()
            }
        }
    }
    val flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider)

    // Detect which item is closest to center
    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) modes.indexOf(currentMode)
            else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    abs(itemCenter - viewportCenter)
                }?.index ?: 0
            }
        }
    }

    // Helper: scroll to center a specific item — single smooth motion, no bounce
    suspend fun scrollToCenterItem(targetIndex: Int, animate: Boolean) {
        // If the item is already visible, use a single animateScrollBy for buttery smooth motion
        val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
        if (itemInfo != null) {
            val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            val distance = (itemCenter - viewportCenter).toFloat()
            if (animate) {
                listState.animateScrollBy(distance)
            } else {
                listState.scrollBy(distance)
            }
        } else {
            // Item not visible — scroll to it first, then fine-tune
            if (animate) {
                listState.animateScrollToItem(targetIndex)
            } else {
                listState.scrollToItem(targetIndex)
            }
            val info = listState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
            if (info != null) {
                val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                val itemCenter = info.offset + info.size / 2
                val correction = (itemCenter - viewportCenter).toFloat()
                if (animate) {
                    listState.animateScrollBy(correction)
                } else {
                    listState.scrollBy(correction)
                }
            }
        }
    }

    // Initial centering — runs once after first layout
    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.size }
            .first { it > 0 }
        val targetIndex = modes.indexOf(currentMode)
        scrollLock.locked = true
        scrollToCenterItem(targetIndex, animate = false)
        scrollLock.locked = false
    }

    // User scroll detection
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
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = (configuration.screenWidthDp / 2).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Subtle edge fade
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black,
                            0.12f to Color.Transparent,
                            0.88f to Color.Transparent,
                            1f to Color.Black
                        )
                    )
                }
        ) {
            itemsIndexed(modes) { index, mode ->
                val isSelected = centerIndex == index

                // Smooth tween for scale — no bounce
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.85f,
                    animationSpec = tween(durationMillis = 180),
                    label = "scale"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.35f,
                    animationSpec = tween(durationMillis = 180),
                    label = "alpha"
                )

                Text(
                    text = mode.title,
                    color = if (isSelected) Color.Yellow else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = if (isSelected) 0.5.sp else 0.sp,
                    softWrap = false,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            if (mode != currentMode && !scrollLock.locked) {
                                scope.launch {
                                    scrollLock.locked = true
                                    onModeSelected(mode)
                                    scrollToCenterItem(index, animate = true)
                                    scrollLock.locked = false
                                }
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 16.dp)
                )
            }
        }

        // Minimal center dot
        Box(
            modifier = Modifier
                .size(4.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-2).dp)
                .background(
                    color = Color.Yellow.copy(alpha = 0.8f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
    }
}
