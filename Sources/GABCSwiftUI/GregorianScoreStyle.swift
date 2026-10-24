import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// The colors a score is drawn in.
public struct GregorianScoreStyle: Sendable {
    /// Staff lines, neumes, clefs, divisions and lyrics.
    public var ink: Color
    /// Lyric marks that are rubrics: `*`, `†`, ℣. and ℟.
    public var rubric: Color
    /// The neume and syllable lit by a highlight.
    public var highlight: Color

    public init(ink: Color, rubric: Color, highlight: Color) {
        self.ink = ink
        self.rubric = rubric
        self.highlight = highlight
    }

    /// Warm black ink and red rubrics, as in a printed chant book, each
    /// lightened in a dark appearance.
    public static let standard = GregorianScoreStyle(
        ink: adaptiveColor(light: (0.11, 0.10, 0.08), dark: (0.85, 0.84, 0.82)),
        rubric: adaptiveColor(light: (0.54, 0.16, 0.15), dark: (0.87, 0.27, 0.20)),
        highlight: adaptiveColor(light: (0.48, 0.05, 0.04), dark: (0.78, 0.20, 0.16))
    )

    private typealias Components = (red: Double, green: Double, blue: Double)

    private static func adaptiveColor(light: Components, dark: Components) -> Color {
        #if canImport(UIKit)
        Color(
            uiColor: UIColor { traits in
                let components = traits.userInterfaceStyle == .dark ? dark : light
                return UIColor(
                    red: components.red,
                    green: components.green,
                    blue: components.blue,
                    alpha: 1
                )
            }
        )
        #elseif canImport(AppKit)
        Color(
            nsColor: NSColor(name: nil) { appearance in
                let isDark = appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
                let components = isDark ? dark : light
                return NSColor(
                    srgbRed: components.red,
                    green: components.green,
                    blue: components.blue,
                    alpha: 1
                )
            }
        )
        #else
        Color(red: light.red, green: light.green, blue: light.blue)
        #endif
    }
}

extension EnvironmentValues {
    /// The colors scores below are drawn in.
    @Entry public var gregorianScoreStyle: GregorianScoreStyle = .standard
}

extension View {
    /// Sets the colors scores in this view are drawn in.
    public func gregorianScoreStyle(_ style: GregorianScoreStyle) -> some View {
        environment(\.gregorianScoreStyle, style)
    }
}
