package com.cameraapp.app.ui.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraBottomBar(
    isRecording: Boolean,
    isVideoMode: Boolean,
    onShutterClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRotateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Gallery
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .size(50.dp)
                .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
        ) {
            Text("🖼️", fontSize = 20.sp)
        }

        // Shutter Button
        Box(
            modifier = Modifier
                .size(80.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(if (isVideoMode && isRecording) Color.Red else Color.White)
                .clickable { onShutterClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isVideoMode && !isRecording) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }

        // Rotate Camera
        IconButton(
            onClick = onRotateClick,
            modifier = Modifier
                .size(50.dp)
                .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
        ) {
            Text("🔄", fontSize = 20.sp)
        }
    }
}
