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
        // SwiftUI views that draw an engraved score.
        .library(name: "GABCSwiftUI", targets: ["GABCSwiftUI"])
    ],
    targets: [
        .target(name: "GABCParser"),
        .target(
            name: "GABCLayout",
            dependencies: ["GABCParser"],
            resources: [
                .copy("Resources/EBGaramond-VariableFont_wght.ttf"),
                .copy("Resources/EBGaramond-OFL.txt")
            ]
        ),
        .target(
            name: "GABCSwiftUI",
            dependencies: ["GABCLayout"]
        ),
        .testTarget(
            name: "GABCParserTests",
            dependencies: ["GABCParser", "GABCLayout"],
            resources: [.copy("Fixtures")]
        ),
        .testTarget(
            name: "GABCLayoutTests",
            dependencies: ["GABCLayout"]
        ),
        .testTarget(
            name: "GABCSwiftUITests",
            dependencies: ["GABCSwiftUI"]
        )
    ]
)
