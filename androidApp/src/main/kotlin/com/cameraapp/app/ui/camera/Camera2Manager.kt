package com.cameraapp.app.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

/**
 * Enhanced Camera2Manager supporting Photo, Video, and Pro controls.
 */
class Camera2Manager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    init {
        // Dump all available sizes and encoder caps on init
        try {
            val chars = cameraManager.getCameraCharacteristics("0")
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val recSizes = map?.getOutputSizes(MediaRecorder::class.java)
            val prevSizes = map?.getOutputSizes(SurfaceTexture::class.java)
            val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            
            Log.d("Camera2Manager", "=== SENSOR INFO ===")
            Log.d("Camera2Manager", "Active array: $sensorSize")
            Log.d("Camera2Manager", "Preview sizes (4:3): ${
                prevSizes?.filter { abs(it.width.toFloat() / it.height - 4f/3f) < 0.05f }
                    ?.sortedByDescending { it.width * it.height }
                    ?.joinToString { "${it.width}x${it.height}" }
            }")
            Log.d("Camera2Manager", "Recorder sizes (4:3): ${
                recSizes?.filter { abs(it.width.toFloat() / it.height - 4f/3f) < 0.05f }
                    ?.sortedByDescending { it.width * it.height }
                    ?.joinToString { "${it.width}x${it.height}" }
            }")
            Log.d("Camera2Manager", "Recorder sizes (16:9): ${
                recSizes?.filter { abs(it.width.toFloat() / it.height - 16f/9f) < 0.05f }
                    ?.sortedByDescending { it.width * it.height }
                    ?.joinToString { "${it.width}x${it.height}" }
            }")
            
            // H264 encoder caps
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder && info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) }) {
                    val caps = info.getCapabilitiesForType("video/avc").videoCapabilities
                    Log.d("Camera2Manager", "H264 Encoder '${info.name}': W=${caps.supportedWidths}, H=${caps.supportedHeights}, Bitrate=${caps.bitrateRange}")
                }
                if (info.isEncoder && info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) }) {
                    val caps = info.getCapabilitiesForType("video/hevc").videoCapabilities
                    Log.d("Camera2Manager", "HEVC Encoder '${info.name}': W=${caps.supportedWidths}, H=${caps.supportedHeights}, Bitrate=${caps.bitrateRange}")
                }
            }
        } catch (e: Exception) {
            Log.e("Camera2Manager", "Failed to dump sensor info", e)
        }
    }
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    private var mediaRecorder: MediaRecorder? = null
    private var imageReader: ImageReader? = null
    private var isRecording = false

    // Active Surfaces
    private var activePreviewSurface: Surface? = null
    private var activeRecorderSurface: Surface? = null

    // Configuration
    private var currentCameraId: String = "0"
    private var flashMode: Int = CaptureRequest.CONTROL_AE_MODE_ON

    /**
     * Returns the best preview size for the given aspect ratio.
     */
    fun getPreviewSize(cameraId: String, ratio: Float): Size {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: return Size(1920, 1080)

        return sizes
            .filter { abs(it.width.toFloat() / it.height.toFloat() - ratio) < 0.05f }
            .maxByOrNull { it.width * it.height }
            ?: sizes.maxByOrNull { it.width * it.height }
            ?: Size(1920, 1080)
    }

    /**
     * Returns the best recording size for MediaRecorder at the given aspect ratio.
     * Respects H264 encoder limits — won't pick a size the hardware can't encode.
     */
    fun getRecordingSize(cameraId: String, ratio: Float): Size {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(MediaRecorder::class.java) ?: return Size(1920, 1080)

        // Query the H264 encoder's max supported resolution
        val maxEncoderPixels = try {
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            val encoderInfo = codecList.codecInfos.firstOrNull {
                it.isEncoder && it.supportedTypes.any { type -> type.equals("video/avc", ignoreCase = true) }
            }
            val caps = encoderInfo?.getCapabilitiesForType("video/avc")?.videoCapabilities
            if (caps != null) {
                val maxW = caps.supportedWidths.upper
                val maxH = caps.supportedHeights.upper
                Log.d("Camera2Manager", "H264 encoder max: ${maxW}x${maxH}")
                maxW * maxH
            } else 1920 * 1080
        } catch (e: Exception) {
            1920 * 1080
        }

        val matching = sizes
            .filter { abs(it.width.toFloat() / it.height.toFloat() - ratio) < 0.05f }
            .filter { it.width * it.height <= maxEncoderPixels }
            .sortedByDescending { it.width * it.height }

        Log.d("Camera2Manager", "Recording sizes for ratio $ratio (encoder-safe): ${
            matching.joinToString { "${it.width}x${it.height}" }
        }")

        return matching.firstOrNull()
            ?: sizes.filter { it.width * it.height <= maxEncoderPixels }
                .maxByOrNull { it.width * it.height }
            ?: Size(1920, 1080)
    }
    
    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("Camera2Background").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("Camera2Manager", "Interrupted while stopping background thread", e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun openCamera(cameraId: String = "0"): Boolean = suspendCoroutine { continuation ->
        if (cameraDevice != null && currentCameraId == cameraId) {
            continuation.resume(true)
            return@suspendCoroutine
        }
        
        startBackgroundThread()
        currentCameraId = cameraId
        
        try {
            cameraManager.openCamera(currentCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    continuation.resume(true)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    continuation.resume(false)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("Camera2Manager", "Failed to open camera", e)
            continuation.resume(false)
        }
    }

    /**
     * Starts the preview session, ensuring ImageReader and Recorder surfaces are prepared.
     */
    suspend fun startPreview(surface: Surface): Boolean = suspendCoroutine { continuation ->
        val device = cameraDevice ?: run {
            continuation.resume(false)
            return@suspendCoroutine
        }
        
        // Close existing session before creating a new one
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null
        
        activePreviewSurface = surface
        activeRecorderSurface = null

        // Initialize ImageReader for high-res photos
        if (imageReader == null) {
            imageReader = ImageReader.newInstance(4080, 3072, ImageFormat.JPEG, 2)
        }

        try {
            val previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            val captureCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    // Guard: if session was already replaced, don't use this stale one
                    if (cameraDevice == null) {
                        session.close()
                        continuation.resume(false)
                        return
                    }
                    captureSession = session
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode)
                        
                        session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                        continuation.resume(true)
                    } catch (e: IllegalStateException) {
                        // Session was closed between creation and this callback
                        Log.w("Camera2Manager", "Session closed before repeating request", e)
                        continuation.resume(false)
                    } catch (e: CameraAccessException) {
                        Log.e("Camera2Manager", "Failed to start repeating request", e)
                        continuation.resume(false)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    continuation.resume(false)
                }
            }

            val surfaces = mutableListOf(surface, imageReader!!.surface)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigs = surfaces.map { OutputConfiguration(it) }
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    context.mainExecutor,
                    captureCallback
                )
                device.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(surfaces, captureCallback, backgroundHandler)
            }

        } catch (e: Exception) {
            Log.e("Camera2Manager", "Failed to create capture session", e)
            continuation.resume(false)
        }
    }

    /**
     * Captures a high-resolution photo.
     */
    suspend fun takePhoto(outputFile: File): Boolean = suspendCoroutine { continuation ->
        val session = captureSession ?: run { continuation.resume(false); return@suspendCoroutine }
        val device = cameraDevice ?: run { continuation.resume(false); return@suspendCoroutine }
        val reader = imageReader ?: run { continuation.resume(false); return@suspendCoroutine }

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)
            
            // Apply current flash/settings
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode)

            reader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                FileOutputStream(outputFile).use { it.write(bytes) }
                image.close()
                Log.d("Camera2Manager", "Photo Saved: ${outputFile.absolutePath}")
                continuation.resume(true)
            }, backgroundHandler)

            session.capture(captureBuilder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e("Camera2Manager", "Photo capture failed", e)
            continuation.resume(false)
        }
    }

    suspend fun startRecording(
        previewSurface: Surface,
        outputFile: File,
        videoWidth: Int = 1920,
        videoHeight: Int = 1080,
        useHevc: Boolean = false
    ): Boolean = suspendCoroutine { continuation ->
        val device = cameraDevice ?: run {
            continuation.resume(false)
            return@suspendCoroutine
        }

        try {
            captureSession?.close()
            setupMediaRecorder(outputFile, videoWidth, videoHeight, useHevc)
            val recorderSurface = mediaRecorder!!.surface
            
            activePreviewSurface = previewSurface
            activeRecorderSurface = recorderSurface

            val recordingRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            recordingRequestBuilder.addTarget(previewSurface)
            recordingRequestBuilder.addTarget(recorderSurface)

            // Lock crop to full sensor — prevents zoom when recording starts
            val characteristics = cameraManager.getCameraCharacteristics(currentCameraId)
            val sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (sensorRect != null) {
                recordingRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, sensorRect)
            }

            val captureCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        recordingRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        session.setRepeatingRequest(recordingRequestBuilder.build(), null, backgroundHandler)
                        
                        mediaRecorder?.start()
                        isRecording = true
                        continuation.resume(true)
                    } catch (e: Exception) {
                        Log.e("Camera2Manager", "Failed to start recording session", e)
                        continuation.resume(false)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    continuation.resume(false)
                }
            }

            // Only preview + recorder — no imageReader (avoids forcing the camera to recrop)
            val surfaces = listOf(previewSurface, recorderSurface)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigs = surfaces.map { OutputConfiguration(it) }
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    context.mainExecutor,
                    captureCallback
                )
                device.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(surfaces, captureCallback, backgroundHandler)
            }

        } catch (e: Exception) {
            Log.e("Camera2Manager", "Failed to setup recording", e)
            continuation.resume(false)
        }
    }

    private fun setupMediaRecorder(outputFile: File, width: Int = 1920, height: Int = 1080, useHevc: Boolean = false) {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(outputFile.absolutePath)
            val pixels = width * height
            val bitrate = (pixels * 10L * 30 / 8).coerceIn(10_000_000, 100_000_000).toInt()
            val codec = if (useHevc) "HEVC" else "H264"
            Log.d("Camera2Manager", "Recording ${width}x${height} @ ${bitrate/1_000_000}Mbps ($codec)")
            setVideoEncodingBitRate(bitrate)
            setVideoFrameRate(30)
            setVideoSize(width, height)
            setVideoEncoder(
                if (useHevc) MediaRecorder.VideoEncoder.HEVC
                else MediaRecorder.VideoEncoder.H264
            )
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44100)
            prepare()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                reset()
            }
            isRecording = false
            activeRecorderSurface = null
        } catch (e: Exception) {
            Log.e("Camera2Manager", "Failed to stop media recorder", e)
        }
    }

    fun setFlashMode(mode: Int) {
        flashMode = mode
        updateManualSettings() // Re-apply to repeating request
    }

    fun updateManualSettings(
        iso: Int? = null,
        shutterSpeedNano: Long? = null,
        focusDistance: Float? = null
    ) {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val previewSurf = activePreviewSurface ?: return

        try {
            val builder = if (isRecording) {
                device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            } else {
                device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            }

            builder.addTarget(previewSurf)
            activeRecorderSurface?.let { builder.addTarget(it) }

            // Apply Flash
            builder.set(CaptureRequest.CONTROL_AE_MODE, flashMode)

            // Apply Manual ISO and Shutter
            if (iso != null || shutterSpeedNano != null) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                if (iso != null) builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                if (shutterSpeedNano != null) builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeedNano)
            }

            // Apply Manual Focus
            if (focusDistance != null) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e("Camera2Manager", "Failed to update manual settings", e)
        }
    }

    fun closeCamera() {
        stopRecording()
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        stopBackgroundThread()
    }
}
