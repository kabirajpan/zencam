package com.cameraapp.app.ui.camera

import android.hardware.camera2.CaptureRequest
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CameraUiState(
    val currentMode: CameraMode = CameraMode.PHOTO,
    val isRecording: Boolean = false,
    val flashMode: Int = CaptureRequest.CONTROL_AE_MODE_ON,
    val currentCameraId: String = "0",
    val isoValue: Float = 400f,
    val focusValue: Float = 0f,
    val isManualMode: Boolean = false
)

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun setMode(mode: CameraMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode, isManualMode = mode.isPro)
    }

    fun setRecording(recording: Boolean) {
        _uiState.value = _uiState.value.copy(isRecording = recording)
    }

    fun toggleFlash() {
        val nextFlash = when (_uiState.value.flashMode) {
            CaptureRequest.CONTROL_AE_MODE_ON -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> CaptureRequest.CONTROL_AE_MODE_OFF
            else -> CaptureRequest.CONTROL_AE_MODE_ON
        }
        _uiState.value = _uiState.value.copy(flashMode = nextFlash)
    }

    fun toggleCamera() {
        val nextId = if (_uiState.value.currentCameraId == "0") "1" else "0"
        _uiState.value = _uiState.value.copy(currentCameraId = nextId)
    }

    fun setIso(value: Float) {
        _uiState.value = _uiState.value.copy(isoValue = value)
    }

    fun setFocus(value: Float) {
        _uiState.value = _uiState.value.copy(focusValue = value)
    }
}
