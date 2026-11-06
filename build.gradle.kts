// R8 as Android's table asks for Kotlin 2.4 (9.1.29): the one bundled with this Android Gradle plugin is older
// and only partly reads Kotlin 2.4's metadata. Taking just R8 avoids moving the whole build to plugin 9.x.
buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("com.android.tools:r8:9.1.29")
    }
}

plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
