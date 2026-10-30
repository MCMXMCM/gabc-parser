import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// GABC to a semantic score: clefs, neumes, lyrics, divisions. The JDK only.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

kotlin {
    explicitApi()
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    // Some of the parser's tests engrave what they parse, as the Swift package's do.
    testImplementation(testFixtures(project(":gabc-layout")))
}

// The Swift package's fixtures and lyric font, shared by both test suites.
tasks.test {
    systemProperty("gabc.fixtures", rootDir.resolve("../Tests/GABCParserTests/Fixtures").absolutePath)
    systemProperty("gabc.lyricFont", rootDir.resolve("../Sources/GABCLyricFont/Resources/EBGaramond-VariableFont_wght.ttf").absolutePath)
    systemProperty("gabc.lyricItalicFont", rootDir.resolve("../Sources/GABCLyricFont/Resources/EBGaramond-Italic-VariableFont_wght.ttf").absolutePath)
    systemProperty("java.awt.headless", "true")
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}
