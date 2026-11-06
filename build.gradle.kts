// Kotlin 2.3 with R8 8.13.19, the pair Android's table lists for plugin 8.13: R8 then reads all Kotlin
// metadata. (Kotlin 2.4 needs R8 9.1.29, which is not published yet, or a move of the whole build to plugin 9.x.)
buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("com.android.tools:r8:8.13.19")
    }
}

plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
