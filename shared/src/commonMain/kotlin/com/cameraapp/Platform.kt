package com.cameraapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
