import CoreText
import Foundation
import GABCLayout

public extension GregorianLyricFont {
    /// Registers the bundled EB Garamond, roman and italic, with the process.
    /// Only the first call does anything. Call it before a score is laid out
    /// or drawn, where the app does not register the face itself.
    ///
    /// A preparatory syllable is measured and drawn in the italic, which the
    /// roman's family finds once it is registered.
    ///
    /// - Returns: Whether the font can be found by its PostScript name:
    ///   registered now, or before (by the app, say).
    @discardableResult
    static func registerBundledFont() -> Bool {
        isBundledFontRegistered
    }
}

/// The variable fonts the library carries, each along its weight axis from
/// 400 to 800: the roman, then the italic.
private let bundledFaces = [
    "EBGaramond-VariableFont_wght",
    "EBGaramond-Italic-VariableFont_wght"
]

private let isBundledFontRegistered: Bool = {
    for name in bundledFaces {
        guard let url = Bundle.module.url(forResource: name, withExtension: "ttf") else { continue }
        var error: Unmanaged<CFError>?
        if !CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error) {
            // Already registered (from the app's own copy, perhaps) is as
            // good as registered now.
            _ = error?.takeRetainedValue()
        }
    }
    let font = CTFontCreateWithName(GregorianLyricFont.defaultName as CFString, 12, nil)
    return CTFontCopyPostScriptName(font) as String == GregorianLyricFont.defaultName
}()
