import CoreGraphics

extension GregorianGlyphDefinition {
    /// The glyph's outline in the catalog's units, about its origin. Fill it
    /// with the even-odd rule: some glyphs need it.
    public var path: CGPath {
        let result = CGMutablePath()
        for commands in paths {
            let path = CGMutablePath()
            for command in commands {
                switch command {
                case let .move(point):
                    path.move(to: point)
                case let .line(point):
                    path.addLine(to: point)
                case let .quadratic(control, end):
                    path.addQuadCurve(to: end, control: control)
                case let .cubic(control1, control2, end):
                    path.addCurve(to: end, control1: control1, control2: control2)
                case .close:
                    path.closeSubpath()
                }
            }
            result.addPath(path)
        }
        return result
    }
}

extension GregorianGlyphCatalog {
    /// Each glyph's outline, built from its commands once. Nothing changes a
    /// path once it is built, so the paths are shared between threads.
    nonisolated(unsafe) fileprivate static let outlines: [GregorianGlyphName: CGPath] = Dictionary(
        uniqueKeysWithValues: GregorianGlyphName.allCases.map { ($0, definition(for: $0).path) }
    )
}

extension GregorianPlacedGlyph {
    /// The glyph's outline, scaled into its frame, in layout coordinates.
    /// Fill it with the even-odd rule: some glyphs need it.
    public var path: CGPath {
        let definition = GregorianGlyphCatalog.definition(for: kind.catalogName)
        guard definition.bounds.width > 0, definition.bounds.height > 0 else {
            return CGMutablePath()
        }

        let sourceBounds = CGRect(
            x: -definition.origin.x,
            y: -definition.origin.y,
            width: definition.bounds.width,
            height: definition.bounds.height
        )
        let scaleX = frame.width / sourceBounds.width
        let scaleY = frame.height / sourceBounds.height
        var transform = CGAffineTransform(
            a: scaleX,
            b: 0,
            c: 0,
            d: scaleY,
            tx: frame.minX - sourceBounds.minX * scaleX,
            ty: frame.minY - sourceBounds.minY * scaleY
        )
        let outline = GregorianGlyphCatalog.outlines[kind.catalogName] ?? definition.path
        return outline.copy(using: &transform) ?? CGMutablePath()
    }
}
