package com.cameraapp.app.ui.camera

enum class CameraMode(val title: String, val isPro: Boolean, val isVideo: Boolean) {
    PHOTO("PHOTO", false, false),
    VIDEO("VIDEO", false, true),
    PRO_PHOTO("PRO PHOTO", true, false),
    PRO_VIDEO("PRO VIDEO", true, true)
}
