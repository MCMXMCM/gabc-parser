// Compose views that draw an engraved score: the counterpart of GABCSwiftUI.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "io.github.mcmxmcm.gabc.compose"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // The drawing builds Compose paths, which the JVM's stand-in for Android leaves empty.
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":gabc-layout"))
    api(project(":gabc-lyric-font"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":gabc-layout")))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// The JVM tests measure lyrics as gabc-layout's tests do.
tasks.withType<Test>().configureEach {
    systemProperty("gabc.lyricFont", rootDir.resolve("../Sources/GABCLyricFont/Resources/EBGaramond-VariableFont_wght.ttf").absolutePath)
    systemProperty("gabc.lyricItalicFont", rootDir.resolve("../Sources/GABCLyricFont/Resources/EBGaramond-Italic-VariableFont_wght.ttf").absolutePath)
    systemProperty("java.awt.headless", "true")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}
