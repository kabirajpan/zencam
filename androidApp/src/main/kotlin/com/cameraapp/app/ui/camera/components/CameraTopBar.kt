package com.cameraapp.app.ui.camera.components

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraTopBar(
    flashMode: Int,
    onFlashToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp) // Slimmer top bar
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "4:3",
            color = Color.White.copy(alpha = 0.8f), // Subtle text
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )

        IconButton(
            onClick = onFlashToggle,
            modifier = Modifier.size(32.dp) // Smaller touch target for minimal look
        ) {
            val icon = when (flashMode) {
                CaptureRequest.CONTROL_AE_MODE_ON -> Icons.Default.FlashAuto
                CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> Icons.Default.FlashOn
                else -> Icons.Default.FlashOff
            }
            Icon(
                imageVector = icon,
                contentDescription = "Flash Mode",
                tint = Color.White,
                modifier = Modifier.size(18.dp) // Minimal icon size
            )
        }
    }
}
