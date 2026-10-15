import CoreText
import Foundation

/// The face lyrics are measured in, and must be drawn in.
///
/// The package bundles EB Garamond, and the layout engine registers it with
/// the process before it measures a lyric, so that the default works without
/// the app bundling the font itself. Any installed or registered font can be
/// named in ``GregorianLayoutMetrics/lyricFontName`` instead.
public enum GregorianLyricFont {
    /// The PostScript name of the bundled EB Garamond's regular weight.
    public static let defaultName = "EBGaramond-Regular"

    /// Registers the bundled EB Garamond with the process. Only the first
    /// call does anything. Call it to draw text in the bundled face before
    /// anything has been laid out.
    ///
    /// - Returns: Whether the font can be found by its PostScript name:
    ///   registered now, or before (by the app, say).
    @discardableResult
    public static func registerBundledFont() -> Bool {
        isRegistered
    }

    private static let isRegistered: Bool = {
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
        let font = CTFontCreateWithName(defaultName as CFString, 12, nil)
        return CTFontCopyPostScriptName(font) as String == defaultName
    }()
}
