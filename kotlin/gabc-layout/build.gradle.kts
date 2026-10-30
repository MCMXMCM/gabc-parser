import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// A score engraved into placed glyphs, strokes and lyrics, in points; the
// glyph outlines as display lists. The JDK only: lyrics are measured by the
// caller's GregorianLyricMeasurer.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `java-test-fixtures`
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
    api(project(":gabc-parser"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

// The tests measure lyrics in the EB Garamond the Swift package carries, with
// the JDK's own text layout (a test fixture the parser's tests share), and
// read the Swift package's fixtures.
tasks.test {
    systemProperty("gabc.fixtures", rootDir.resolve("../Tests/GABCParserTests/Fixtures").absolutePath)
    systemProperty("gabc.lyricFont", rootDir.resolve("../Sources/GABCLyricFont/Resources/EBGaramond-VariableFont_wght.ttf").absolutePath)
    systemProperty("gabc.lyricItalicFont", rootDir.resolve("../Sources/GABCLyricFont/Resources/EBGaramond-Italic-VariableFont_wght.ttf").absolutePath)
    systemProperty("java.awt.headless", "true")
}

// The measuring fixture is for the tests alone.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}
