import GABCLayout
import SwiftUI

/// Converts the generated, platform-neutral Exsurge display list into a
/// SwiftUI `Path`. There is no SVG parser, font lookup, JavaScript, or WebView
/// in the rendering path.
public enum GregorianGlyphLibrary {
    /// A placed glyph's outline in layout coordinates. Fill it with the
    /// even-odd rule: some glyphs need it.
    public static func path(for glyph: GregorianPlacedGlyph) -> Path {
        Path(glyph.path)
    }
}
