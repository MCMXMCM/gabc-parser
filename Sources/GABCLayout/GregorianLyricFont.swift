/// The face lyrics are measured in, and must be drawn in.
///
/// The layout engine measures lyrics in the face named by
/// ``GregorianLayoutMetrics/lyricFontName``, EB Garamond unless another is
/// named. The face must be registered before a score is laid out: by an app
/// that bundles its own copy, or by `GABCLyricFont`, which carries EB Garamond
/// and registers it (`GregorianLyricFont.registerBundledFont()`). GABCSwiftUI
/// registers it before it lays out a score.
public enum GregorianLyricFont {
    /// The PostScript name of EB Garamond's regular weight.
    public static let defaultName = "EBGaramond-Regular"
}
