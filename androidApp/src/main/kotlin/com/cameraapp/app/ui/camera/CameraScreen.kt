package com.cameraapp.app.ui.camera

import android.Manifest
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cameraapp.app.ui.camera.components.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
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
    
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { CameraMode.values().size })
    var previewSurface by remember { mutableStateOf<Surface?>(null) }
    val targetRatio = if (isLandscape) 4f / 3f else 3f / 4f

    DisposableEffect(Unit) {
        onDispose { cameraManager.closeCamera() }
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setMode(CameraMode.values()[pagerState.currentPage])
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

    // MAIN LAYOUT: Using a Box to layer components correctly without scope leaks
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Camera Preview (Bottom Layer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 180.dp) // Leave space for bars
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
                modifier = Modifier.fillMaxWidth().aspectRatio(targetRatio)
            )

            // Overlays inside the Preview Box
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.isRecording,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            ) {
                RecordingIndicator()
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.isManualMode,
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
                pagerState = pagerState,
                onModeSelected = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
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
