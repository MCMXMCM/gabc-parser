// EB Garamond, the lyrics' face, for an Android app that does not bundle it,
// with a measurer of lyrics in it: the counterpart of GABCLyricFont. The font
// is the Swift package's own copy, taken as an asset.
plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "io.github.mcmxmcm.gabc.lyricfont"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

// The Swift package's own copy of the font and its license, as assets.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(rootDir.resolve("../Sources/GABCLyricFont/Resources").absolutePath)
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":gabc-layout"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
