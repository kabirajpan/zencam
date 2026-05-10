package com.cameraapp.app.ui.camera.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cameraapp.app.ui.camera.CameraMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraModeSelector(
    pagerState: PagerState,
    onModeSelected: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val halfWidth = (maxWidth - 100.dp) / 2
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSize = PageSize.Fixed(100.dp),
            contentPadding = PaddingValues(horizontal = halfWidth)
        ) { page ->
            val mode = CameraMode.values()[page]
            val isSelected = pagerState.currentPage == page
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.title,
                    color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.5f),
                    fontSize = if (isSelected) 14.sp else 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.clickable { onModeSelected(page) }
                )
            }
        }
    }
}
