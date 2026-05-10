package com.cameraapp.app.ui.camera

import android.Manifest
import android.content.ContentValues
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
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
import java.text.SimpleDateFormat
import java.util.Locale

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
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var currentVideoFile by remember { mutableStateOf<File?>(null) }

    // Always use 4:3 from the sensor — matches most phone cameras natively, no stretch
    val previewSize = remember(uiState.currentCameraId) {
        cameraManager.getPreviewSize(uiState.currentCameraId, 4f / 3f)
    }
    val targetRatio = if (isLandscape) {
        previewSize.width.toFloat() / previewSize.height
    } else {
        previewSize.height.toFloat() / previewSize.width
    }

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

    // When switching between photo/video, re-apply buffer size and restart preview
    LaunchedEffect(uiState.currentMode.isVideo) {
        val st = surfaceTexture ?: return@LaunchedEffect
        st.setDefaultBufferSize(previewSize.width, previewSize.height)
        val surface = Surface(st)
        previewSurface = surface
        cameraManager.startPreview(surface)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Viewport Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 150.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                st.setDefaultBufferSize(previewSize.width, previewSize.height)
                                surfaceTexture = st
                                previewSurface = Surface(st)
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                previewSurface = null
                                surfaceTexture = null
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
                            delay(250)
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
                            scope.launch {
                                currentVideoFile?.let { videoFile ->
                                    if (videoFile.exists() && videoFile.length() > 0) {
                                        val values = ContentValues().apply {
                                            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
                                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraApp")
                                            }
                                        }
                                        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                                        uri?.let { mediaUri ->
                                            context.contentResolver.openOutputStream(mediaUri)?.use { out ->
                                                videoFile.inputStream().use { input -> input.copyTo(out) }
                                            }
                                        }
                                        videoFile.delete()
                                        Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show()
                                    }
                                    currentVideoFile = null
                                }
                                cameraManager.startPreview(surf)
                            }
                        } else {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                            val videoFile = File(context.cacheDir, "VID_$timestamp.mp4")
                            currentVideoFile = videoFile
                            scope.launch {
                                if (cameraManager.startRecording(surf, videoFile)) {
                                    viewModel.setRecording(true)
                                }
                            }
                        }
                    } else {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                        val tempFile = File(context.cacheDir, "IMG_$timestamp.jpg")
                        scope.launch {
                            val success = cameraManager.takePhoto(tempFile)
                            if (success) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg")
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraApp")
                                    }
                                }
                                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                uri?.let { mediaUri ->
                                    context.contentResolver.openOutputStream(mediaUri)?.use { out ->
                                        tempFile.inputStream().use { input -> input.copyTo(out) }
                                    }
                                }
                                tempFile.delete()
                                Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onGalleryClick = { /* TODO */ },
                onRotateClick = { viewModel.toggleCamera() }
            )
        }
    }
}
