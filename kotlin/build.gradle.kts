// The parser and the layout are plain Kotlin, for the JVM; the lyric font and
// the Compose views are Android libraries, with AGP's built-in Kotlin.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
