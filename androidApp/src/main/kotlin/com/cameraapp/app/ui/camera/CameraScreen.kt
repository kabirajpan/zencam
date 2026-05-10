package com.cameraapp.app.ui.camera

import android.Manifest
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cameraapp.app.ui.camera.components.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionsState.allPermissionsGranted) {
            CameraContent(viewModel)
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permissions required.", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CameraContent(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val uiState by viewModel.uiState.collectAsState()
    val cameraManager = remember { Camera2Manager(context) }
    
    var previewSurface by remember { mutableStateOf<Surface?>(null) }
    val targetRatio = if (isLandscape) 4f / 3f else 3f / 4f

    // Transition State
    var isSwitchingMode by remember { mutableStateOf(false) }
    val transitionAlpha by animateFloatAsState(
        targetValue = if (isSwitchingMode) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "ModeTransition"
    )
    val transitionBlur by animateFloatAsState(
        targetValue = if (isSwitchingMode) 12f else 0f,
        animationSpec = tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "BlurTransition"
    )
    val transitionScale by animateFloatAsState(
        targetValue = if (isSwitchingMode) 1.03f else 1f,
        animationSpec = tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "ScaleTransition"
    )

    DisposableEffect(Unit) {
        onDispose { cameraManager.closeCamera() }
    }

    LaunchedEffect(previewSurface, uiState.currentCameraId) {
        val surface = previewSurface ?: return@LaunchedEffect
        if (cameraManager.openCamera(uiState.currentCameraId)) {
            cameraManager.startPreview(surface)
        }
    }

    LaunchedEffect(uiState.currentMode, uiState.flashMode, uiState.isoValue, uiState.focusValue, uiState.isRecording) {
        cameraManager.setFlashMode(uiState.flashMode)
        if (uiState.isManualMode) {
            cameraManager.updateManualSettings(
                iso = uiState.isoValue.toInt(),
                focusDistance = uiState.focusValue
            )
        } else {
            cameraManager.updateManualSettings(iso = null, focusDistance = null)
        }
    }

    LaunchedEffect(uiState.currentMode.isVideo) {
        previewSurface?.let { cameraManager.startPreview(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Viewport Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 150.dp) // Minimal bars
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                previewSurface = Surface(st)
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                previewSurface = null
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(targetRatio)
                    .graphicsLayer {
                        scaleX = transitionScale
                        scaleY = transitionScale
                    }
                    .blur(transitionBlur.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(transitionAlpha * 0.4f)
                    .background(Color.Black)
            )

            AnimatedVisibility(
                visible = uiState.isRecording,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            ) {
                RecordingIndicator()
            }

            AnimatedVisibility(
                visible = uiState.isManualMode && !isSwitchingMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                CameraProControls(
                    isoValue = uiState.isoValue,
                    onIsoChange = { viewModel.setIso(it) },
                    focusValue = uiState.focusValue,
                    onFocusChange = { viewModel.setFocus(it) }
                )
            }
        }

        // 2. Top Bar (Overlay)
        CameraTopBar(
            flashMode = uiState.flashMode,
            onFlashToggle = { viewModel.toggleFlash() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Bottom Controls (Overlay)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CameraModeSelector(
            currentMode = uiState.currentMode,
            onModeSelected = { mode ->
                if (mode != uiState.currentMode) {
                    scope.launch {
                        isSwitchingMode = true
                        viewModel.setMode(mode)
                        delay(250) // Quick, snappy transition
                        isSwitchingMode = false
                    }
                }
            }
        )

            CameraBottomBar(
                isRecording = uiState.isRecording,
                isVideoMode = uiState.currentMode.isVideo,
                onShutterClick = {
                    val surf = previewSurface ?: return@CameraBottomBar
                    if (uiState.currentMode.isVideo) {
                        if (uiState.isRecording) {
                            cameraManager.stopRecording()
                            viewModel.setRecording(false)
                            scope.launch { cameraManager.startPreview(surf) }
                        } else {
                            val file = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
                            scope.launch {
                                if (cameraManager.startRecording(surf, file)) {
                                    viewModel.setRecording(true)
                                }
                            }
                        }
                    } else {
                        val file = File(context.cacheDir, "img_${System.currentTimeMillis()}.jpg")
                        scope.launch { cameraManager.takePhoto(file) }
                    }
                },
                onGalleryClick = { /* TODO */ },
                onRotateClick = { viewModel.toggleCamera() }
            )
        }
    }
}
