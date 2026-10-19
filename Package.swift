// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "gabc-parser",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        // GABC to a semantic score: clefs, neumes, lyrics, divisions.
        .library(name: "GABCParser", targets: ["GABCParser"]),
        // A score engraved into placed glyphs, strokes and lyrics.
        .library(name: "GABCLayout", targets: ["GABCLayout"]),
        // EB Garamond, the lyrics' face, for a client that does not bundle it.
        .library(name: "GABCLyricFont", targets: ["GABCLyricFont"]),
        // SwiftUI views that draw an engraved score.
        .library(name: "GABCSwiftUI", targets: ["GABCSwiftUI"])
    ],
    targets: [
        .target(name: "GABCParser"),
        .target(
            name: "GABCLayout",
            dependencies: ["GABCParser"]
        ),
        .target(
            name: "GABCLyricFont",
            dependencies: ["GABCLayout"],
            resources: [
                .copy("Resources/EBGaramond-VariableFont_wght.ttf"),
                .copy("Resources/EBGaramond-OFL.txt")
            ]
        ),
        .target(
            name: "GABCSwiftUI",
            dependencies: ["GABCLayout", "GABCLyricFont"]
        ),
        .testTarget(
            name: "GABCParserTests",
            dependencies: ["GABCParser", "GABCLayout", "GABCLyricFont"],
            resources: [.copy("Fixtures")]
        ),
        .testTarget(
            name: "GABCLayoutTests",
            dependencies: ["GABCLayout", "GABCLyricFont"]
        ),
        .testTarget(
            name: "GABCSwiftUITests",
            dependencies: ["GABCSwiftUI"]
        )
    ]
)
