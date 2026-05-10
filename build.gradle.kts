plugins {
    // this is necessary to avoid the plugins being loaded multiple times
    // in each subproject's classloader
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.multiplatform") version "1.9.22" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.compose") version "1.5.11" apply false
}
