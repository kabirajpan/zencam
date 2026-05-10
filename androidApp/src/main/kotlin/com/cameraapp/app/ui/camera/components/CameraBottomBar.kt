package com.cameraapp.app.ui.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
            .height(100.dp) // Slimmer bottom bar
            .padding(horizontal = 50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Minimal Gallery
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape) // Subtle glass background
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Gallery",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp) // Minimal icon
            )
        }

        // Shutter Button (Keeping it prominent but clean)
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape) // Thinner border
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (isVideoMode && isRecording) Color.Red else Color.White)
                .clickable { onShutterClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isVideoMode && !isRecording) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }

        // Minimal Rotate Camera
        IconButton(
            onClick = onRotateClick,
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp) // Minimal icon
            )
        }
    }
}
