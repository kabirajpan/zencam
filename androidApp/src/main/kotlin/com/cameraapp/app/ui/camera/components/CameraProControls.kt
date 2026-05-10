package com.cameraapp.app.ui.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraProControls(
    isoValue: Float,
    onIsoChange: (Float) -> Unit,
    focusValue: Float,
    onFocusChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(20.dp)
    ) {
        ManualControlRow(
            label = "ISO",
            value = isoValue,
            range = 100f..3200f,
            onValueChange = onIsoChange
        )
        Spacer(modifier = Modifier.height(12.dp))
        ManualControlRow(
            label = "FOCUS",
            value = focusValue,
            range = 0f..10f,
            onValueChange = onFocusChange
        )
    }
}

@Composable
private fun ManualControlRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color.White,
            modifier = Modifier.width(50.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Yellow
            )
        )
        val displayValue = if (label == "FOCUS") "%.1f".format(value) else value.toInt().toString()
        Text(
            text = displayValue,
            color = Color.Yellow,
            modifier = Modifier.width(40.dp),
            fontSize = 10.sp,
            textAlign = TextAlign.End
        )
    }
}
