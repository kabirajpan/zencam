package com.cameraapp.app.ui.camera.components

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.layout.*
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
            .height(64.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "4:3",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        IconButton(onClick = onFlashToggle) {
            val icon = when (flashMode) {
                CaptureRequest.CONTROL_AE_MODE_ON -> "⚡A"
                CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "⚡ON"
                else -> "⚡OFF"
            }
            Text(
                text = icon,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
