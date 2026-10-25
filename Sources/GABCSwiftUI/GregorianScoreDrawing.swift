import GABCLayout
import SwiftUI

/// A score laid out at one width, with its drawing prepared. Both are
/// `Sendable` values: prepare them away from the main actor for long
/// documents, then draw them with ``GregorianScoreCanvas``.
public struct GregorianPreparedScore: Sendable {
    public let score: GregorianScore
    public let layout: GregorianLayout
    public let drawing: GregorianScoreDrawing

    /// Lays a score out at a width and prepares its drawing.
    ///
    /// - Parameters:
    ///   - mode: The score's mode, shown above a large initial when it names
    ///     a conclusion (`8g`, `7c2`), or when the score opens a piece; see
    ///     ``GregorianEngravingLayoutEngine/openingLabel(forMode:opensPiece:)``.
    ///   - opensPiece: Whether the score opens a piece, a hymn, a responsory
    ///     or a part of the Mass, which takes an initial.
    public init(
        score: GregorianScore,
        width: CGFloat,
        metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
        mode: String? = nil,
        opensPiece: Bool = false
    ) {
        // The lyrics are measured and drawn in EB Garamond.
        GregorianLyricFont.registerBundledFont()
        self.init(
            score: score,
            layout: GregorianEngravingLayoutEngine().layout(
                score: score,
                width: width,
                metrics: metrics,
                openingLabel: mode,
                opensPiece: opensPiece
            )
        )
    }

    /// Prepares the drawing of a score already laid out.
    public init(score: GregorianScore, layout: GregorianLayout) {
        self.score = score
        self.layout = layout
        drawing = GregorianScoreDrawing(layout: layout)
    }
}

/// A layout's static ink, gathered into one tile for each staff system.
public struct GregorianScoreDrawing: Sendable {
    public struct StrokeBatch: Sendable {
        public let path: Path
        public let lineWidth: CGFloat

        public init(path: Path, lineWidth: CGFloat) {
            self.path = path
            self.lineWidth = lineWidth
        }
    }

    public struct Tile: Sendable {
        public let frame: CGRect
        public let strokeBatches: [StrokeBatch]
        public let glyphPaths: [Path]
        public let lyrics: [GregorianPlacedLyric]
        public let initials: [GregorianPlacedInitial]

        public init(
            frame: CGRect,
            strokes: [GregorianPlacedStroke],
            glyphs: [GregorianPlacedGlyph],
            lyrics: [GregorianPlacedLyric],
            initials: [GregorianPlacedInitial]
        ) {
            self.frame = frame
            strokeBatches = Dictionary(grouping: strokes, by: \.lineWidth)
                .sorted { $0.key < $1.key }
                .map { lineWidth, strokes in
                    var path = Path()
                    for stroke in strokes {
                        path.move(to: stroke.start)
                        path.addLine(to: stroke.end)
                    }
                    return StrokeBatch(path: path, lineWidth: lineWidth)
                }

            // Even-odd fill is required by some catalog glyphs. Keep intersecting
            // glyphs in separate paths so overlapping glyphs cannot cancel each
            // other's fill, while still batching the usual non-overlapping glyphs.
            struct GlyphBatch {
                var path = Path()
                var occupiedFrames: [CGRect] = []
            }

            var glyphBatches: [GlyphBatch] = []
            for glyph in glyphs {
                let occupiedFrame = glyph.frame.insetBy(dx: -0.5, dy: -0.5)
                let batchIndex = glyphBatches.firstIndex { batch in
                    batch.occupiedFrames.allSatisfy {
                        !$0.intersects(occupiedFrame)
                    }
                } ?? glyphBatches.endIndex
                if batchIndex == glyphBatches.endIndex {
                    glyphBatches.append(GlyphBatch())
                }
                glyphBatches[batchIndex].path.addPath(GregorianGlyphLibrary.path(for: glyph))
                glyphBatches[batchIndex].occupiedFrames.append(occupiedFrame)
            }
            glyphPaths = glyphBatches.map(\.path)
            self.lyrics = lyrics
            self.initials = initials
        }
    }

    public let tiles: [Tile]

    public init(layout: GregorianLayout) {
        let frames = layout.staffs.isEmpty
            ? [CGRect(origin: .zero, size: layout.size)]
            : layout.staffs.map(\.systemFrame)
        var strokes = Array(repeating: [GregorianPlacedStroke](), count: frames.count)
        var glyphs = Array(repeating: [GregorianPlacedGlyph](), count: frames.count)
        var lyrics = Array(repeating: [GregorianPlacedLyric](), count: frames.count)
        var initials = Array(repeating: [GregorianPlacedInitial](), count: frames.count)

        func tileIndex(for y: CGFloat) -> Int {
            frames.firstIndex { y < $0.maxY } ?? frames.index(before: frames.endIndex)
        }

        for stroke in layout.strokes {
            let centerY = (stroke.start.y + stroke.end.y) / 2
            strokes[tileIndex(for: centerY)].append(stroke)
        }
        for glyph in layout.glyphs {
            glyphs[tileIndex(for: glyph.frame.midY)].append(glyph)
        }
        for lyric in layout.lyrics {
            lyrics[tileIndex(for: lyric.origin.y)].append(lyric)
        }
        for initial in layout.initials {
            initials[tileIndex(for: initial.origin.y)].append(initial)
        }

        tiles = frames.indices.map { index in
            Tile(
                frame: frames[index],
                strokes: strokes[index],
                glyphs: glyphs[index],
                lyrics: lyrics[index],
                initials: initials[index]
            )
        }
    }
}
