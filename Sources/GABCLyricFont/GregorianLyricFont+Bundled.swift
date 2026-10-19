import CoreText
import Foundation
import GABCLayout

public extension GregorianLyricFont {
    /// Registers the bundled EB Garamond with the process. Only the first
    /// call does anything. Call it before a score is laid out or drawn, where
    /// the app does not register the face itself.
    ///
    /// - Returns: Whether the font can be found by its PostScript name:
    ///   registered now, or before (by the app, say).
    @discardableResult
    static func registerBundledFont() -> Bool {
        isBundledFontRegistered
    }
}

private let isBundledFontRegistered: Bool = {
    if let url = Bundle.module.url(
        forResource: "EBGaramond-VariableFont_wght",
        withExtension: "ttf"
    ) {
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
