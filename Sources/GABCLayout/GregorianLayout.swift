import CoreGraphics
import CoreText
import Foundation
import GABCParser

public struct GregorianLayoutMetrics: Hashable, Sendable {
    public static let minimumNotationScale = 0.6
    public static let maximumNotationScale = 1.6
    public static let notationScaleRange =
        minimumNotationScale...maximumNotationScale

    public static func clampedNotationScale(_ scale: Double) -> Double {
        max(minimumNotationScale, min(maximumNotationScale, scale))
    }

    public var notationScale: Double
    public var lyricScale: Double
    /// The PostScript name of the font lyrics are measured in. Whatever
    /// draws the layout should draw its lyrics in the font each one names.
    public var lyricFontName: String
    /// The pixels in a point of the screen the layout is drawn on. Given,
    /// the staff space is a whole number of pixels, and the staff and ledger
    /// lines, stems, episemata and bars are whole pixels wide, two at
    /// least, and set where their edges fall between pixels, so that they
    /// draw crisply; nil, as for print, sets them where they fall.
    public var displayScale: Double?

    public init(
        notationScale: Double = 1,
        lyricScale: Double = 1,
        lyricFontName: String = GregorianLyricFont.defaultName,
        displayScale: Double? = nil
    ) {
        self.notationScale = notationScale
        self.lyricScale = lyricScale
        self.lyricFontName = lyricFontName
        self.displayScale = displayScale
    }
}

/// The pixel grid of the screen a layout is drawn on, if one is given.
private struct PixelGrid {
    /// Pixels in a point; nil sets everything where it falls.
    let scale: CGFloat?

    /// A count of pixels to a thousandth, so that a place a hair's breadth
    /// either side of a pixel's middle rounds alike in every language.
    private func steady(_ pixels: CGFloat) -> CGFloat {
        (pixels * 1000).rounded() / 1000
    }

    /// The nearest length of whole pixels, one at least.
    func wholePixels(_ length: CGFloat) -> CGFloat {
        guard let scale else { return length }
        return max(1, steady(length * scale).rounded()) / scale
    }

    /// A length rounded up to whole pixels.
    func roundedUp(_ length: CGFloat) -> CGFloat {
        guard let scale else { return length }
        return (steady(length * scale) - 0.001).rounded(.up) / scale
    }

    /// A line's width in whole pixels, two at least.
    func lineWidth(_ width: CGFloat) -> CGFloat {
        guard let scale else { return width }
        return max(2, steady(width * scale).rounded()) / scale
    }

    /// Where a line of `width`, already whole pixels, is centred near
    /// `position` so that its edges fall between pixels: on the edge of a
    /// pixel if it is an even number of pixels wide, on the middle of one
    /// if odd.
    func centre(_ position: CGFloat, width: CGFloat, roundingUp: Bool = false) -> CGFloat {
        guard let scale else { return position }
        let offset: CGFloat = steady(width * scale).rounded().truncatingRemainder(dividingBy: 2) == 0 ? 0 : 0.5
        let pixels = steady(position * scale - offset)
        return ((roundingUp ? (pixels - 0.001).rounded(.up) : pixels.rounded()) + offset) / scale
    }

    /// Strokes snapped to the grid: each whole pixels wide, and set across
    /// or up and down where its edges fall between pixels.
    func snap(_ strokes: [GregorianPlacedStroke]) -> [GregorianPlacedStroke] {
        guard scale != nil else { return strokes }
        return strokes.map { stroke in
            let width = lineWidth(stroke.lineWidth)
            var start = stroke.start
            var end = stroke.end
            if abs(start.y - end.y) < 0.001 {
                start.y = centre(start.y, width: width)
                end.y = start.y
            } else if abs(start.x - end.x) < 0.001 {
                start.x = centre(start.x, width: width)
                end.x = start.x
            }
            return GregorianPlacedStroke(
                kind: stroke.kind,
                start: start,
                end: end,
                lineWidth: width,
                eventID: stroke.eventID
            )
        }
    }
}

public enum GregorianGlyphKind: Hashable, Sendable {
    // Stable public cases retained for tests and accessibility consumers.
    case cClef
    case fClef
    case notehead(GregorianNoteShape)
    case flat
    case natural
    case sharp
    case mora
    case custos
    case catalog(GregorianGlyphName)

    public var catalogName: GregorianGlyphName {
        switch self {
        case .cClef: .doClef
        case .fClef: .faClef
        case let .notehead(shape):
            switch shape {
            case .punctum: .punctumQuadratum
            case .virga: .virgaShort
            case .quilisma: .quilisma
            case .liquescent: .punctumQuadratumLiquescent
            case .inclinatum: .punctumInclinatum
            case .oriscus: .oriscusAsc
            case .stropha: .stropha
            case .custos: .custosShort
            }
        case .flat: .flat
        case .natural: .natural
        case .sharp: .sharp
        case .mora: .mora
        case .custos: .custosShort
        case let .catalog(name): name
        }
    }
}

public enum GregorianStrokeKind: Hashable, Sendable {
    case staff
    case ledger
    case connector
    case episema
    case division(GregorianDivision)
}

public struct GregorianPlacedGlyph: Hashable, Sendable {
    public let kind: GregorianGlyphKind
    public let frame: CGRect
    public let eventID: String?

    public init(kind: GregorianGlyphKind, frame: CGRect, eventID: String? = nil) {
        self.kind = kind
        self.frame = frame
        self.eventID = eventID
    }
}

public struct GregorianPlacedStroke: Hashable, Sendable {
    public let kind: GregorianStrokeKind
    public let start: CGPoint
    public let end: CGPoint
    public let lineWidth: CGFloat
    public let eventID: String?

    public init(
        kind: GregorianStrokeKind,
        start: CGPoint,
        end: CGPoint,
        lineWidth: CGFloat,
        eventID: String? = nil
    ) {
        self.kind = kind
        self.start = start
        self.end = end
        self.lineWidth = lineWidth
        self.eventID = eventID
    }
}

public struct GregorianPlacedLyric: Hashable, Sendable {
    public let text: String
    public let style: GregorianLyricStyle
    public let origin: CGPoint
    public let width: CGFloat
    public let fontSize: CGFloat
    /// The PostScript name of the font the lyric was measured in.
    public let fontName: String
    public let neumeID: String

    public init(
        text: String,
        style: GregorianLyricStyle = .regular,
        origin: CGPoint,
        width: CGFloat,
        fontSize: CGFloat,
        fontName: String = GregorianLyricFont.defaultName,
        neumeID: String
    ) {
        self.text = text
        self.style = style
        self.origin = origin
        self.width = width
        self.fontSize = fontSize
        self.fontName = fontName
        self.neumeID = neumeID
    }
}

public struct GregorianPlacedInitial: Hashable, Sendable {
    public let text: String
    public let annotation: String?
    public let annotationOrigin: CGPoint?
    public let annotationFontSize: CGFloat
    public let origin: CGPoint
    public let width: CGFloat
    public let fontSize: CGFloat
    /// The PostScript name of the font the initial and its annotation were
    /// measured in.
    public let fontName: String
    /// The style the initial and its annotation were measured in, and must
    /// be drawn in: bold, as the chant books set them.
    public let style: GregorianLyricStyle
    public let neumeID: String

    public init(
        text: String,
        annotation: String? = nil,
        annotationOrigin: CGPoint? = nil,
        annotationFontSize: CGFloat = 0,
        origin: CGPoint,
        width: CGFloat,
        fontSize: CGFloat,
        fontName: String = GregorianLyricFont.defaultName,
        style: GregorianLyricStyle = .regular,
        neumeID: String
    ) {
        self.text = text
        self.annotation = annotation
        self.annotationOrigin = annotationOrigin
        self.annotationFontSize = annotationFontSize
        self.origin = origin
        self.width = width
        self.fontSize = fontSize
        self.fontName = fontName
        self.style = style
        self.neumeID = neumeID
    }
}

public struct GregorianEventPlacement: Hashable, Sendable, Identifiable {
    public var id: String { eventID }
    public let eventID: String
    public let neumeID: String
    public let frame: CGRect
    public let lineIndex: Int

    public init(eventID: String, neumeID: String, frame: CGRect, lineIndex: Int) {
        self.eventID = eventID
        self.neumeID = neumeID
        self.frame = frame
        self.lineIndex = lineIndex
    }
}

public struct GregorianNeumePlacement: Hashable, Sendable, Identifiable {
    public let id: String
    public let eventIDs: [String]
    /// The notes the neume draws: more than its events when a note is
    /// written twice or thrice (`hvv`, `hsss`).
    public let noteCount: Int
    public let lyric: String
    public let inkFrame: CGRect
    public let hitFrame: CGRect
    public let lineIndex: Int

    public init(
        id: String,
        eventIDs: [String],
        noteCount: Int? = nil,
        lyric: String,
        inkFrame: CGRect,
        hitFrame: CGRect,
        lineIndex: Int
    ) {
        self.id = id
        self.eventIDs = eventIDs
        self.noteCount = noteCount ?? eventIDs.count
        self.lyric = lyric
        self.inkFrame = inkFrame
        self.hitFrame = hitFrame
        self.lineIndex = lineIndex
    }
}

public struct GregorianStaffLayout: Hashable, Sendable, Identifiable {
    public var id: Int { index }
    public let index: Int
    public let frame: CGRect
    public let systemFrame: CGRect
    public let firstEventID: String?

    public init(
        index: Int,
        frame: CGRect,
        systemFrame: CGRect? = nil,
        firstEventID: String?
    ) {
        self.index = index
        self.frame = frame
        self.systemFrame = systemFrame ?? frame
        self.firstEventID = firstEventID
    }
}

public struct GregorianLayout: Sendable {
    public let size: CGSize
    public let staffs: [GregorianStaffLayout]
    public let glyphs: [GregorianPlacedGlyph]
    public let strokes: [GregorianPlacedStroke]
    public let lyrics: [GregorianPlacedLyric]
    public let initials: [GregorianPlacedInitial]
    public let events: [GregorianEventPlacement]
    public let neumes: [GregorianNeumePlacement]

    public init(
        size: CGSize,
        staffs: [GregorianStaffLayout],
        glyphs: [GregorianPlacedGlyph],
        strokes: [GregorianPlacedStroke],
        lyrics: [GregorianPlacedLyric],
        initials: [GregorianPlacedInitial] = [],
        events: [GregorianEventPlacement],
        neumes: [GregorianNeumePlacement]
    ) {
        self.size = size
        self.staffs = staffs
        self.glyphs = glyphs
        self.strokes = strokes
        self.lyrics = lyrics
        self.initials = initials
        self.events = events
        self.neumes = neumes
    }

    public func neume(containing eventID: String) -> GregorianNeumePlacement? {
        neumes.first { $0.eventIDs.contains(eventID) }
    }
}

/// Native Gregorian engraving with the same three-stage shape as Exsurge:
/// semantic units, intrinsic measurement/line breaking, then positioned ink.
public struct GregorianEngravingLayoutEngine: Sendable {
    private static let lyricLineHeightMultiplier: CGFloat = 1.23

    public init() {}

    public static func openingLabel(forMode mode: String?) -> String? {
        guard let mode = mode?.trimmingCharacters(in: .whitespacesAndNewlines),
              !mode.isEmpty else {
            return nil
        }

        let compact = mode
            .lowercased()
            .filter { !$0.isWhitespace }
        guard let prefix = modePrefixes.first(where: { compact.hasPrefix($0) }) else {
            return nil
        }

        var conclusion = compact.dropFirst(prefix.count)
        if conclusion.first == "-" {
            conclusion.removeFirst()
        }
        if conclusion.hasPrefix("alt-") {
            conclusion = conclusion.dropFirst(4)
        }
        guard let concludingLetter = conclusion.first,
              ("a"..."g").contains(String(concludingLetter)) else {
            return nil
        }

        let variant = conclusion.dropFirst()
        guard variant.isEmpty
                || variant.allSatisfy(\.isNumber)
                || variant == "star"
                || variant == "*"
                || variant == "-" else {
            return nil
        }
        let label = mode.uppercased()
        // The books mark a starred ending with an asterisk (8 G*), which the
        // mode may spell out; a label so marked reads as itself.
        guard variant == "star", label.hasSuffix("STAR") else { return label }
        return label.dropLast(4).trimmingCharacters(in: .whitespaces) + "*"
    }

    /// The label printed above the initial of a piece that opens a chant:
    /// for an antiphon, its mode and the conclusion of its psalm tone, as
    /// ``openingLabel(forMode:)`` gives it; for a piece with no psalm tone, a
    /// hymn, a responsory or a part of the Mass, its mode alone ("8", "4").
    public static func openingLabel(forMode mode: String?, opensPiece: Bool) -> String? {
        if let label = openingLabel(forMode: mode) { return label }
        guard opensPiece,
              let compact = mode?.lowercased().filter({ !$0.isWhitespace }),
              let prefix = modePrefixes.first(where: { compact.hasPrefix($0) }) else {
            return nil
        }
        // A numeral, not the first letters of a word.
        if let next = compact.dropFirst(prefix.count).first, next.isLetter { return nil }
        let index = modePrefixes.firstIndex(of: prefix)!
        return modePrefixes[index < 8 ? index + 8 : index]
    }

    /// The modes as the GABC writes them, the longer Roman numerals first
    /// and each in the same place as its Arabic numeral eight on.
    private static let modePrefixes = [
        "viii", "vii", "iii", "vi", "iv", "ii", "v", "i",
        "8", "7", "3", "6", "4", "2", "5", "1"
    ]

    /// Lays out a score at a width.
    ///
    /// - Parameters:
    ///   - openingLabel: The label printed above the initial; the initial is
    ///     drawn where it is a psalm tone's (`8g`), or where the score opens
    ///     a piece.
    ///   - opensPiece: Whether the score opens a piece, a hymn, a responsory
    ///     or a part of the Mass, which takes an initial, with its mode alone
    ///     above it if it has no psalm tone.
    public func layout(
        score: GregorianScore,
        width requestedWidth: CGFloat,
        metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
        openingLabel: String? = nil,
        opensPiece: Bool = false
    ) -> GregorianLayout {
        let requestedNotationScale = CGFloat(
            GregorianLayoutMetrics.clampedNotationScale(
                metrics.notationScale
            )
        )
        let grid = PixelGrid(scale: metrics.displayScale.flatMap { $0 > 0 ? CGFloat($0) : nil })
        // On a screen, a staff space of whole pixels sets every line of the
        // staff alike.
        let notationScale = grid.wholePixels(12.5 * requestedNotationScale) / 12.5
        let lyricScale = max(
            0.5,
            min(
                3.5,
                CGFloat(metrics.lyricScale) * requestedNotationScale
            )
        )
        let lyricFontName = metrics.lyricFontName
        let width = max(240, requestedWidth)
        let glyphScale = notationScale / 16
        let staffStep = 100 * glyphScale // one GABC pitch step / half staff-space
        let staffGap = staffStep * 2
        let staffLineWidth = grid.lineWidth(max(0.72, 0.78 * notationScale))
        let sidePadding = 8 * notationScale
        let clefColumn = 18 * notationScale
        let custosColumn = 10 * notationScale
        let lyricFontSize = 21 * lyricScale
        let lyricHeight = ceil(
            lyricFontSize * Self.lyricLineHeightMultiplier
        )
        let hyphenWidth = measureLyric("-", fontSize: lyricFontSize, fontName: lyricFontName)
        let spacing = Spacing(staffSpace: staffGap, hyphen: hyphenWidth)
        let openingInitial = openingInitial(
            in: score,
            annotation: Self.openingLabel(forMode: openingLabel, opensPiece: opensPiece),
            opensPiece: opensPiece
        )
        let initialScale = max(notationScale, min(1.35, lyricScale))
        let initialFontSize = 56 * initialScale
        let initialAnnotationFontSize = 13 * max(notationScale, min(1.25, lyricScale))
        // The initial and its annotation are bold, as the books set them.
        let initialWidth = openingInitial.map {
            measureLyric($0.text, style: .accented, fontSize: initialFontSize, fontName: lyricFontName)
        } ?? 0
        let initialAnnotationWidth = openingInitial?.annotation.map {
            measureLyric($0, style: .accented, fontSize: initialAnnotationFontSize, fontName: lyricFontName)
        } ?? 0
        let lyricFont = CTFontCreateWithName(lyricFontName as CFString, 100, nil)
        let ascent = CTFontGetAscent(lyricFont) / 100
        let capHeight = CTFontGetCapHeight(lyricFont) / 100
        let initialColumn = openingInitial.map { _ in
            max(
                38 * notationScale,
                max(initialWidth, initialAnnotationWidth) + 8 * notationScale
            )
        } ?? 0

        let units = makeUnits(
            score: score,
            glyphScale: glyphScale,
            notationScale: notationScale,
            lyricFontSize: lyricFontSize,
            lyricFontName: lyricFontName,
            openingInitial: openingInitial
        )
        func lineFrame(_ lineIndex: Int) -> LineFrame {
            let staffLeft = sidePadding + (lineIndex == 0 ? initialColumn : 0)
            return LineFrame(
                staffLeft: staffLeft,
                notationStart: staffLeft + clefColumn + 4 * notationScale,
                notationEnd: width - sidePadding - custosColumn,
                textEnd: width - sidePadding
            )
        }
        let wordContinues = Self.wordContinuation(units)
        let lineRanges = breakLines(
            units,
            frame: lineFrame,
            spacing: spacing,
            wordContinues: wordContinues
        )
        let lines = lineRanges.map { Array(units[$0]) }
        let placements = lineRanges.enumerated().map { lineIndex, range in
            placeLine(
                units[range],
                frame: lineFrame(lineIndex),
                spacing: spacing,
                endsWithinWord: wordContinues[range.upperBound],
                endsParagraph: range.isEmpty || range.upperBound == units.count
                    || units[range.upperBound - 1].forcesBreak
            )
        }
        let lineVerticalMetrics = lines.enumerated().map { lineIndex, units in
            verticalMetrics(
                for: units,
                custosPitch: Self.custosPitch(endingLine: lineIndex, of: lines),
                glyphScale: glyphScale,
                staffStep: staffStep,
                staffGap: staffGap,
                notationScale: notationScale,
                lyricHeight: lyricHeight,
                openingInitialFontSize: lineIndex == 0 && openingInitial != nil
                    ? initialFontSize
                    : nil,
                grid: grid,
                staffLineWidth: staffLineWidth
            )
        }
        var lineOrigins: [CGFloat] = []
        var contentHeight: CGFloat = 0
        for metrics in lineVerticalMetrics {
            lineOrigins.append(contentHeight)
            contentHeight += metrics.height
        }

        var staffs: [GregorianStaffLayout] = []
        var glyphs: [GregorianPlacedGlyph] = []
        var strokes: [GregorianPlacedStroke] = []
        var lyrics: [GregorianPlacedLyric] = []
        var initials: [GregorianPlacedInitial] = []
        var events: [GregorianEventPlacement] = []
        var rawNeumes: [RawNeumePlacement] = []
        var activeClef = GregorianClef(kind: .c, line: 4)
        let punctumWidth = 100 * glyphScale

        for (lineIndex, lineUnits) in lines.enumerated() {
            let lineOriginY = lineOrigins[lineIndex]
            let lineMetrics = lineVerticalMetrics[lineIndex]
            let staffTop = lineOriginY + lineMetrics.topInset
            let staffBottom = staffTop + staffGap * 3
            let staffCenter = (staffTop + staffBottom) / 2
            let frame = lineFrame(lineIndex)
            let staffLeft = frame.staffLeft
            let lyricY = staffBottom + lineMetrics.lyricOffset
            let placement = placements[lineIndex]
            let firstEventID = lineUnits.compactMap(\.neume?.id).first
            staffs.append(
                GregorianStaffLayout(
                    index: lineIndex,
                    frame: CGRect(
                        x: staffLeft,
                        y: staffTop,
                        width: width - sidePadding - staffLeft,
                        height: staffGap * 3
                    ),
                    systemFrame: CGRect(
                        x: 0,
                        y: lineOriginY,
                        width: width,
                        height: lineMetrics.height
                    ),
                    firstEventID: firstEventID
                )
            )

            for staffLine in 0..<4 {
                let y = staffTop + CGFloat(staffLine) * staffGap
                strokes.append(
                    GregorianPlacedStroke(
                        kind: .staff,
                        start: CGPoint(x: staffLeft, y: y),
                        end: CGPoint(x: width - sidePadding, y: y),
                        lineWidth: staffLineWidth
                    )
                )
            }

            var leadingClefCount = 0
            for unit in lineUnits {
                guard case let .clef(clef) = unit.element else { break }
                activeClef = clef
                leadingClefCount += 1
            }
            appendClef(
                activeClef,
                x: staffLeft + 2 * notationScale,
                staffTop: staffTop,
                staffGap: staffGap,
                glyphScale: glyphScale,
                notationScale: notationScale,
                glyphs: &glyphs
            )

            if lineIndex == 0, let openingInitial {
                // The initial stands on the words' baseline, and its
                // annotation is centred over it, its capitals about the top
                // line, as in the Liber.
                let lyricBaseline = lyricY + ascent * lyricFontSize
                let annotationBaseline = staffTop - 0.14 * staffGap + capHeight * initialAnnotationFontSize / 2
                initials.append(
                    GregorianPlacedInitial(
                        text: openingInitial.text,
                        annotation: openingInitial.annotation,
                        annotationOrigin: openingInitial.annotation.map { _ in
                            CGPoint(
                                x: sidePadding + (initialColumn - initialAnnotationWidth) / 2,
                                y: max(0, annotationBaseline - ascent * initialAnnotationFontSize)
                            )
                        },
                        annotationFontSize: initialAnnotationFontSize,
                        origin: CGPoint(
                            x: sidePadding + (initialColumn - initialWidth) / 2,
                            y: lyricBaseline - ascent * initialFontSize
                        ),
                        width: initialWidth,
                        fontSize: initialFontSize,
                        fontName: lyricFontName,
                        style: .accented,
                        neumeID: openingInitial.neumeID
                    )
                )
            }

            for (unitIndex, unit) in lineUnits.enumerated() {
                let x = placement.x[unitIndex]
                switch unit.element {
                case let .clef(clef):
                    if unitIndex < leadingClefCount { continue }
                    activeClef = clef
                    appendClef(
                        clef,
                        x: x,
                        staffTop: staffTop,
                        staffGap: staffGap,
                        glyphScale: glyphScale,
                        notationScale: notationScale,
                        glyphs: &glyphs
                    )

                case let .accidental(accidental):
                    let y = staffCenter - CGFloat(accidental.pitch - 6) * staffStep
                    glyphs.append(
                        placedGlyph(
                            kind: Self.glyphKind(for: accidental.kind),
                            anchor: CGPoint(x: x, y: y),
                            scale: glyphScale * 0.9
                        )
                    )

                case let .division(division):
                    appendDivision(
                        division,
                        x: x + unit.inkRight / 2,
                        staffTop: staffTop,
                        staffGap: staffGap,
                        notationScale: notationScale,
                        strokes: &strokes
                    )

                case .forcedBreak:
                    break

                case let .lyricMark(mark):
                    lyrics.append(
                        GregorianPlacedLyric(
                            text: mark.text,
                            style: mark.style,
                            origin: CGPoint(x: placement.lyricX[unitIndex] ?? x, y: lyricY),
                            width: unit.lyricWidth,
                            fontSize: lyricFontSize,
                            fontName: lyricFontName,
                            neumeID: mark.id
                        )
                    )

                case let .neume(neume):
                    let composition = compose(
                        neume: neume,
                        context: unit.context,
                        originX: x - punctumWidth / 2,
                        staffCenter: staffCenter,
                        staffTop: staffTop,
                        staffBottom: staffBottom,
                        staffStep: staffStep,
                        glyphScale: glyphScale,
                        notationScale: notationScale,
                        lineIndex: lineIndex
                    )
                    glyphs.append(contentsOf: composition.glyphs)
                    strokes.append(contentsOf: composition.strokes)
                    events.append(contentsOf: composition.events)

                    if unit.hasLyric, let lyricX = placement.lyricX[unitIndex] {
                        lyrics.append(
                            GregorianPlacedLyric(
                                text: unit.lyricText,
                                style: neume.lyricStyle,
                                origin: CGPoint(x: lyricX, y: lyricY),
                                width: unit.lyricWidth,
                                fontSize: lyricFontSize,
                                fontName: lyricFontName,
                                neumeID: neume.id
                            )
                        )
                    }
                    // A custos is drawn and keeps its event's place, but it
                    // is not sung: the cantor guide cannot begin from it.
                    if neume.writtenNoteCount > 0 {
                        rawNeumes.append(
                            RawNeumePlacement(
                                neume: neume,
                                inkFrame: composition.inkFrame,
                                lineIndex: lineIndex,
                                staffTop: staffTop,
                                staffBottom: staffBottom + lineMetrics.lyricOffset + lyricHeight
                            )
                        )
                    }
                }
            }

            // The hyphen is the lyrics' own, set just after its syllable,
            // whatever the gap that follows; one at the staff's end divides
            // a word that goes on in the next.
            for unitIndex in placement.hyphenated {
                let unit = lineUnits[unitIndex]
                let next = units[(lineRanges[lineIndex].lowerBound + unitIndex + 1)...]
                    .first(where: \.hasLyric)
                guard let lyricX = placement.lyricX[unitIndex],
                      let id = unit.lyricID,
                      let nextID = next?.lyricID else {
                    continue
                }
                lyrics.append(
                    GregorianPlacedLyric(
                        text: "-",
                        origin: CGPoint(x: lyricX + unit.lyricWidth, y: lyricY),
                        width: hyphenWidth,
                        fontSize: lyricFontSize,
                        fontName: lyricFontName,
                        neumeID: "lyric-hyphen-\(id)-\(nextID)"
                    )
                )
            }

            if let custosPitch = Self.custosPitch(endingLine: lineIndex, of: lines) {
                let nextY = staffCenter - CGFloat(custosPitch - 6) * staffStep
                // The custos stands flush with the staff's end.
                let custosWidth = GregorianGlyphCatalog.definition(for: .custosShort)
                    .bounds.width * glyphScale
                let custosX = width - sidePadding - custosWidth
                glyphs.append(
                    placedGlyph(
                        kind: .custos,
                        anchor: CGPoint(x: custosX, y: nextY),
                        scale: glyphScale
                    )
                )
            }
        }

        strokes = grid.snap(mergeLedgerLines(strokes, scale: notationScale))
        let neumes = makeHitFrames(rawNeumes, width: width, scale: notationScale)
        return GregorianLayout(
            size: CGSize(width: width, height: max(64, contentHeight)),
            staffs: staffs,
            glyphs: glyphs,
            strokes: strokes,
            lyrics: lyrics,
            initials: initials,
            events: events,
            neumes: neumes
        )
    }

    /// How a syllable's lyric meets the lyric before it.
    private enum LyricJoin {
        /// It begins a word, or follows a rubric: a word's space parts them.
        case word
        /// It is the next syllable of the word: joined to the one before,
        /// or parted from it by one hyphen.
        case syllable
        /// One of the two writes its own hyphen at the join: they may
        /// touch, and take no other.
        case written
    }

    private struct LayoutUnit {
        let element: GregorianNotationElement
        /// The unit's ink, reckoned from where it is placed: a neume's from
        /// the centre of its first note, a clef's, an accidental's or a
        /// division's from the left of the room it takes, which runs to
        /// `inkRight`.
        let inkLeft: CGFloat
        let inkRight: CGFloat
        let lyricText: String
        let lyricWidth: CGFloat
        /// From the lyric's left edge to the centre of its sung vowel.
        let lyricFocus: CGFloat
        let join: LyricJoin
        let forcesBreak: Bool
        /// The notes beside a neume that its engraving needs.
        var context = NeumeContext()
        /// How far the unit's ink reaches below the staff's bottom line.
        var inkBelowStaff: CGFloat = 0
        /// How far the unit's ink reaches above the staff's top line.
        var inkAboveStaff: CGFloat = 0

        var neume: GregorianNeume? {
            guard case let .neume(neume) = element else { return nil }
            return neume
        }

        var hasLyric: Bool { !lyricText.isEmpty }

        var lyricID: String? {
            switch element {
            case let .neume(neume): neume.id
            case let .lyricMark(mark): mark.id
            default: nil
            }
        }
    }

    /// The room a staff gives its notes and words.
    private struct LineFrame {
        let staffLeft: CGFloat
        /// Where the first note may stand, clear of the clef.
        let notationStart: CGFloat
        /// Where the notes must end, short of the custos.
        let notationEnd: CGFloat
        /// Where the words must end; they may run on under the custos.
        let textEnd: CGFloat
    }

    /// A staff's units, set in place.
    private struct LinePlacement {
        /// Each unit's place, from which its `inkLeft` and `inkRight` are
        /// reckoned.
        var x: [CGFloat]
        /// The left edge of each unit's lyric.
        var lyricX: [CGFloat?]
        /// The units whose syllable a hyphen follows, within the staff or at
        /// its end.
        var hyphenated: [Int] = []
        var notationRight: CGFloat
        var textRight: CGFloat
        /// The words, bars, rubrics and clefs the staff may stretch before.
        var stretchPoints = 0
        /// The share of the staff's note spaces that it may give up.
        var spare: CGFloat = 0

        func fits(_ frame: LineFrame) -> Bool {
            notationRight <= frame.notationEnd + 0.01 && textRight <= frame.textEnd + 0.01
        }

        /// The room the staff leaves at its end: by the notes, short of the
        /// custos, or by the words, whichever leaves less. It is reckoned to
        /// a hundredth of a point, so that no choice of breaks turns on how
        /// the sums were rounded.
        func slack(in frame: LineFrame) -> CGFloat {
            let slack = min(frame.notationEnd - notationRight, frame.textEnd - textRight)
            return (slack * 100 + 0.5).rounded(.down) / 100
        }
    }

    /// The spaces a staff keeps between its notes and between its words, in
    /// points: Exsurge's, reckoned from the staff space.
    private struct Spacing {
        /// Between the neumes of one syllable.
        let withinSyllable: CGFloat
        /// Between one syllable's notes and the next's, within a word.
        let betweenSyllables: CGFloat
        /// Between one word's notes and the next's.
        let betweenWords: CGFloat
        /// After a bar, beyond the half of its room that follows its line.
        let afterDivision: CGFloat
        /// Before a clef within the staff.
        let beforeClef: CGFloat
        /// The least space between two words' texts.
        let word: CGFloat
        /// A hyphen's advance: the least space between two syllables of a
        /// word that do not touch.
        let hyphen: CGFloat
        /// The most a staff that ends a paragraph is stretched at each word
        /// or bar, as Exsurge stretches one; the measure of the stretch of
        /// any other.
        let maximumStretch: CGFloat

        /// The share of each space between notes that a full staff may give
        /// up, as Exsurge's.
        static let condensable: CGFloat = 0.3
        /// A gap between two syllables no wider than this is read as none.
        static let touching: CGFloat = 0.25

        init(staffSpace: CGFloat, hyphen: CGFloat) {
            withinSyllable = 0.25 * staffSpace
            betweenSyllables = 0.625 * staffSpace
            betweenWords = 0.875 * staffSpace
            afterDivision = 0.25 * staffSpace
            beforeClef = 0.5 * staffSpace
            word = max(hyphen, 0.5 * staffSpace)
            self.hyphen = hyphen
            maximumStretch = 0.5 * staffSpace
        }
    }

    private struct RawNeumePlacement {
        let neume: GregorianNeume
        let inkFrame: CGRect
        let lineIndex: Int
        let staffTop: CGFloat
        let staffBottom: CGFloat
    }

    private struct Composition {
        var glyphs: [GregorianPlacedGlyph] = []
        var strokes: [GregorianPlacedStroke] = []
        var events: [GregorianEventPlacement] = []
        var inkFrame = CGRect.null
    }

    private struct LineVerticalMetrics {
        let topInset: CGFloat
        let lyricOffset: CGFloat
        let height: CGFloat
    }

    private struct OpeningInitial {
        let text: String
        let annotation: String?
        let remainder: String
        let neumeID: String
    }

    /// The pitch of the custos that ends a staff: the next staff's first
    /// note. The last staff has none, and a custos written in the score
    /// (`f+`, as before a change of clef) already guides the eye to what
    /// follows, so the staff that ends with one is not given another.
    private static func custosPitch(endingLine lineIndex: Int, of lines: [[LayoutUnit]]) -> Int? {
        guard lineIndex + 1 < lines.count,
              lines[lineIndex].compactMap(\.neume).last?.notes.last?.isCustos != true else {
            return nil
        }
        return lines[lineIndex + 1].compactMap(\.neume).first?.notes.first?.pitch
    }

    private func openingInitial(
        in score: GregorianScore,
        annotation: String?,
        opensPiece: Bool
    ) -> OpeningInitial? {
        guard annotation != nil || opensPiece,
              let neume = score.neumes.first(where: { !$0.lyric.isEmpty }),
              let initialIndex = neume.lyric.firstIndex(where: \.isUppercase) else {
            return nil
        }
        let prefix = neume.lyric[..<initialIndex]
        guard prefix.allSatisfy({
            $0.isWhitespace || $0.isNumber || $0 == "."
        }) else {
            return nil
        }
        // The rest of the word the initial begins is small, however the
        // GABC writes it (PU-ER).
        return OpeningInitial(
            text: String(neume.lyric[initialIndex]),
            annotation: annotation,
            remainder: neume.lyric[neume.lyric.index(after: initialIndex)...].lowercased(),
            neumeID: neume.id
        )
    }

    private func verticalMetrics(
        for units: [LayoutUnit],
        custosPitch: Int?,
        glyphScale: CGFloat,
        staffStep: CGFloat,
        staffGap: CGFloat,
        notationScale: CGFloat,
        lyricHeight: CGFloat,
        openingInitialFontSize: CGFloat?,
        grid: PixelGrid,
        staffLineWidth: CGFloat
    ) -> LineVerticalMetrics {
        let notes = units.compactMap(\.neume).flatMap(\.notes)
        let accidentalPitches = units.compactMap { unit -> Int? in
            guard case let .accidental(accidental) = unit.element else { return nil }
            return accidental.pitch
        }
        let pitches = notes.map(\.pitch) + accidentalPitches + (custosPitch.map { [$0] } ?? [])
        let highestPitch = pitches.max() ?? 9
        var topInset = max(
            11 * notationScale,
            CGFloat(max(0, highestPitch - 9)) * staffStep + 10 * notationScale
        )
        // A custos's stem rises farther above its pitch than a note's head:
        // the staff is set low enough to hold it.
        let custosPitches = notes.filter(\.isCustos).map(\.pitch) + (custosPitch.map { [$0] } ?? [])
        if let highestCustos = custosPitches.max() {
            let stem = GregorianGlyphCatalog.definition(for: .custosShort).origin.y * glyphScale
            topInset = max(
                topInset,
                CGFloat(highestCustos - 9) * staffStep + stem + 2 * notationScale
            )
        }
        // A mark that rises higher than the notes leave room for, as an
        // episema set clear of a ledger line may, sets the staff lower still:
        // a staff's band holds all its ink, a point to spare.
        if let inkAbove = units.map(\.inkAboveStaff).max() {
            topInset = max(topInset, inkAbove + notationScale)
        }
        // The words stand as far below the lowest ink as they stand below a
        // note on the bottom line, and no nearer the staff.
        var inkBelow = units.map(\.inkBelowStaff).max() ?? 0
        if let custosPitch {
            let custos = GregorianGlyphCatalog.definition(for: .custosShort)
            let y = staffGap * 1.5 - CGFloat(custosPitch - 6) * staffStep
            inkBelow = max(inkBelow, y + (custos.bounds.height - custos.origin.y) * glyphScale - staffGap * 3)
        }
        let punctum = GregorianGlyphCatalog.definition(for: .punctumQuadratum)
        let headBelowLine = (punctum.bounds.height - punctum.origin.y) * glyphScale
        let lyricOffset = max(2 * notationScale, inkBelow - headBelowLine + 2 * notationScale)
        // On a screen, each staff begins on a whole pixel, and its top line
        // is set where its edges fall between pixels; the others follow it a
        // whole number of pixels apart.
        topInset = grid.centre(topInset, width: staffLineWidth, roundingUp: true)
        let systemGap = 4 * notationScale
        let notationHeight = topInset + staffGap * 3 + lyricOffset + lyricHeight + systemGap
        let initialHeight = openingInitialFontSize.map {
            topInset + ceil($0 * 1.15) + systemGap
        } ?? 0
        let height = grid.roundedUp(max(notationHeight, initialHeight))
        return LineVerticalMetrics(
            topInset: topInset,
            lyricOffset: lyricOffset,
            height: height
        )
    }

    private func makeUnits(
        score: GregorianScore,
        glyphScale: CGFloat,
        notationScale: CGFloat,
        lyricFontSize: CGFloat,
        lyricFontName: String,
        openingInitial: OpeningInitial?
    ) -> [LayoutUnit] {
        // The last lyric written, and the syllable it was sung on: none for
        // a rubric, which ends any word before it.
        var previousLyric: (text: String, syllableID: String?)?
        let punctum = 100 * glyphScale
        let staffSpace = 200 * glyphScale

        func unit(
            _ element: GregorianNotationElement,
            inkLeft: CGFloat = 0,
            inkRight: CGFloat,
            lyricText: String = "",
            lyricWidth: CGFloat = 0,
            lyricFocus: CGFloat = 0,
            join: LyricJoin = .word,
            forcesBreak: Bool = false,
            context: NeumeContext = NeumeContext(),
            inkBottom: CGFloat = 0,
            inkTop: CGFloat = 0
        ) -> LayoutUnit {
            LayoutUnit(
                element: element,
                inkLeft: inkLeft,
                inkRight: inkRight,
                lyricText: lyricText,
                lyricWidth: lyricWidth,
                lyricFocus: lyricFocus,
                join: join,
                forcesBreak: forcesBreak,
                context: context,
                // Reckoned on a staff whose top line is at 0.
                inkBelowStaff: max(0, inkBottom - 3 * staffSpace),
                inkAboveStaff: max(0, -inkTop)
            )
        }

        // A first syllable that the initial takes whole leaves a hyphen
        // under its notes where its word goes on, as Gregorio and Exsurge
        // set it.
        let openingHyphen: Bool = {
            guard let openingInitial, openingInitial.remainder.isEmpty,
                  let first = score.neumes.firstIndex(where: { $0.id == openingInitial.neumeID }),
                  let next = score.neumes[(first + 1)...].first(where: { !$0.lyric.isEmpty }) else {
                return false
            }
            return !next.startsWord && next.syllableID != score.neumes[first].syllableID
        }()
        // And so are its other syllables.
        var openingWord: Set<String> = []
        // A verse number written before the initial's syllable is left out:
        // the initial marks the first verse. The others keep theirs.
        var omittedMarks: Set<Int> = []
        if let openingInitial,
           let first = score.elements.firstIndex(where: {
               if case let .neume(neume) = $0 { return neume.id == openingInitial.neumeID }
               return false
           }) {
            for element in score.elements[(first + 1)...] {
                if case .lyricMark = element { break }
                guard case let .neume(neume) = element, !neume.lyric.isEmpty else { continue }
                if neume.startsWord { break }
                openingWord.insert(neume.id)
            }
            for index in score.elements.indices[..<first] {
                if case let .lyricMark(mark) = score.elements[index], Self.isVerseNumber(mark.text) {
                    omittedMarks.insert(index)
                }
            }
        }

        // Each neume's context: the last note of the neume of its syllable
        // that it follows with no space (`!`), and the next neume's first
        // note.
        let elements = score.elements
        var contexts = [NeumeContext](repeating: NeumeContext(), count: elements.count)
        var nextNote: GregorianNote?
        for index in elements.indices.reversed() {
            guard case let .neume(neume) = elements[index] else { continue }
            contexts[index].next = nextNote
            if let first = neume.notes.first, !first.isCustos { nextNote = first }
            if neume.spaceBefore == .joined, index > 0,
               case let .neume(previous) = elements[index - 1],
               previous.syllableID == neume.syllableID {
                contexts[index].joinedFrom = previous.notes.last
            }
        }

        return elements.enumerated().compactMap { elementIndex, element in
            switch element {
            case .clef:
                return unit(element, inkRight: 24 * notationScale)
            case let .accidental(accidental):
                let definition = GregorianGlyphCatalog.definition(
                    for: Self.glyphKind(for: accidental.kind).catalogName
                )
                let y = staffSpace * 1.5 - CGFloat(accidental.pitch - 6) * punctum
                return unit(
                    element,
                    // The accidental, and the space that parts it from its
                    // note: about a quarter of a staff space, as Gregorio's.
                    inkRight: definition.bounds.width * glyphScale * 0.9 + 0.27 * staffSpace,
                    inkBottom: y + (definition.bounds.height - definition.origin.y) * glyphScale * 0.9
                )
            case let .division(division):
                return unit(element, inkRight: (division == .final ? 17 : 12) * notationScale)
            case .forcedBreak:
                return unit(element, inkRight: 0, forcesBreak: true)
            case let .lyricMark(mark):
                if omittedMarks.contains(elementIndex) { return nil }
                previousLyric = (mark.text, nil)
                return unit(
                    element,
                    inkRight: 0,
                    lyricText: mark.text,
                    lyricWidth: measureLyric(
                        mark.text,
                        style: mark.style,
                        fontSize: lyricFontSize,
                        fontName: lyricFontName
                    )
                )
            case let .neume(neume):
                let opensScore = neume.id == openingInitial?.neumeID
                let lyricText = opensScore
                    ? (openingHyphen ? "-" : openingInitial?.remainder ?? neume.lyric)
                    : openingWord.contains(neume.id) ? neume.lyric.lowercased() : neume.lyric
                // The opening initial takes the syllable's first letter.
                let centeredRange = opensScore
                    ? neume.centeredRange.flatMap { range in
                        range.lowerBound >= 1 ? (range.lowerBound - 1)..<(range.upperBound - 1) : nil
                    }
                    : neume.centeredRange
                let lyric = lyricMetrics(
                    lyricText,
                    style: neume.lyricStyle,
                    centeredRange: centeredRange,
                    fontSize: lyricFontSize,
                    fontName: lyricFontName
                )
                var join = LyricJoin.word
                if !lyricText.isEmpty, !neume.startsWord,
                   let previousLyric,
                   let previousSyllable = previousLyric.syllableID,
                   previousSyllable != neume.syllableID {
                    join = previousLyric.text.hasSuffix("-") || lyricText.hasPrefix("-")
                        ? .written
                        : .syllable
                }
                if !lyricText.isEmpty {
                    previousLyric = (lyricText, neume.syllableID)
                }
                let context = contexts[elementIndex]
                let ink = notationInk(
                    neume,
                    context: context,
                    glyphScale: glyphScale,
                    notationScale: notationScale
                )
                return unit(
                    element,
                    // A neume is placed by its first note's centre, under
                    // which its syllable's vowel stands.
                    inkLeft: ink.left - punctum / 2,
                    inkRight: ink.right - punctum / 2,
                    lyricText: lyricText,
                    lyricWidth: lyric.width,
                    lyricFocus: lyric.focus,
                    join: join,
                    context: context,
                    inkBottom: ink.bottom,
                    inkTop: ink.top
                )
            }
        }
    }

    /// Whether a mark is a verse number: `1.`, `12`.
    private static func isVerseNumber(_ text: String) -> Bool {
        let digits = text.hasSuffix(".") ? text.dropLast() : Substring(text)
        return !digits.isEmpty && digits.allSatisfy { ("0"..."9").contains($0) }
    }

    private static func inkRight(of composition: Composition) -> CGFloat {
        let strokes = composition.strokes.map { max($0.start.x, $0.end.x) }.max()
            ?? -.greatestFiniteMagnitude
        return max(
            composition.inkFrame.isNull ? -.greatestFiniteMagnitude : composition.inkFrame.maxX,
            strokes
        )
    }

    private static func inkLeft(of composition: Composition) -> CGFloat {
        let strokes = composition.strokes.map { min($0.start.x, $0.end.x) }.min()
            ?? .greatestFiniteMagnitude
        return min(
            composition.inkFrame.isNull ? .greatestFiniteMagnitude : composition.inkFrame.minX,
            strokes
        )
    }

    /// A neume's ink, from where its notation begins: its notes, with their
    /// dots, episemata and ledger lines; and its lowest and highest, on a
    /// staff whose top line is at 0.
    private func notationInk(
        _ neume: GregorianNeume,
        context: NeumeContext,
        glyphScale: CGFloat,
        notationScale: CGFloat
    ) -> (left: CGFloat, right: CGFloat, bottom: CGFloat, top: CGFloat) {
        let staffStep = 100 * glyphScale
        let staffGap = staffStep * 2
        let composition = compose(
            neume: neume,
            context: context,
            originX: 0,
            staffCenter: staffGap * 1.5,
            staffTop: 0,
            staffBottom: staffGap * 3,
            staffStep: staffStep,
            glyphScale: glyphScale,
            notationScale: notationScale,
            lineIndex: 0
        )
        let left = Self.inkLeft(of: composition)
        let right = Self.inkRight(of: composition)
        let bottom = max(
            composition.inkFrame.isNull ? 0 : composition.inkFrame.maxY,
            composition.strokes.map { max($0.start.y, $0.end.y) + $0.lineWidth / 2 }.max() ?? 0
        )
        let top = min(
            composition.inkFrame.isNull ? 0 : composition.inkFrame.minY,
            composition.strokes.map { min($0.start.y, $0.end.y) - $0.lineWidth / 2 }.min() ?? 0
        )
        guard left.isFinite, right.isFinite, right > left else { return (0, staffStep, bottom, top) }
        return (left, right, bottom, top)
    }

    /// For each unit, whether a staff that ends before it divides a word:
    /// whether the next lyric is the next syllable of the word the last one
    /// began. One more than the units, for a staff that ends the score.
    private static func wordContinuation(_ units: [LayoutUnit]) -> [Bool] {
        var result = Array(repeating: false, count: units.count + 1)
        var continues = false
        for index in units.indices.reversed() {
            if units[index].hasLyric {
                continues = units[index].join == .syllable
            }
            result[index] = continues
        }
        return result
    }

    /// What a break before each unit costs, as TeX reckons penalties: lower
    /// is better, and nil where no staff may break. A staff best ends with a
    /// bar, then with a word; a word's syllables may be divided at a cost,
    /// and beside a syllable of one letter only as a last resort; a melisma
    /// at a bar, or where four of its notes or more stand on either side.
    /// No break falls between an accidental and its note, before a bar or a
    /// custos, before a rubric that closes the words before it or an
    /// asterisk however it is written, or after a rubric that heads the
    /// words after it. One more than the units: the end of the score.
    private static func breakPenalties(_ units: [LayoutUnit]) -> [Int?] {
        // The notes each syllable writes: a melisma of ten or more may be
        // divided between staffs, as Gregorio divides one.
        var syllableNotes: [String: Int] = [:]
        for unit in units {
            if let neume = unit.neume {
                syllableNotes[neume.syllableID, default: 0] += neume.writtenNoteCount
            }
        }

        func letterCount(_ text: String) -> Int {
            text.filter(\.isLetter).count
        }

        func neumePenalty(_ index: Int, afterDivision: Bool) -> Int? {
            let unit = units[index]
            guard let neume = unit.neume, neume.writtenNoteCount > 0 else { return nil }
            guard unit.hasLyric else {
                // A bar within a melisma divides it as well as any.
                if afterDivision { return -50 }
                // Elsewhere a melisma is divided only where it leaves four
                // notes or more on either staff, short of a bar or a word.
                var before = 0
                for earlier in units[..<index].reversed() {
                    if case .division = earlier.element { break }
                    before += earlier.neume?.writtenNoteCount ?? 0
                    if earlier.hasLyric { break }
                }
                var after = 0
                for later in units[index...] {
                    if case .division = later.element { break }
                    if later.hasLyric { break }
                    after += later.neume?.writtenNoteCount ?? 0
                }
                guard before >= 4, after >= 4 else { return nil }
                let previous = units[..<index].last { $0.neume != nil }?.neume
                if previous?.syllableID == neume.syllableID {
                    return (syllableNotes[neume.syllableID] ?? 0) >= 10 ? 300 : nil
                }
                // The notes of a group without words go on the melisma of
                // the syllable before them.
                return 200
            }
            switch unit.join {
            case .word:
                return afterDivision ? -50 : 0
            case .syllable, .written:
                let previousLyric = units[..<index].last(where: \.hasLyric)?.lyricText ?? ""
                // A letter alone at a staff's end or start is a last resort.
                return letterCount(previousLyric) <= 1 || letterCount(unit.lyricText) <= 1 ? 10_000 : 100
            }
        }

        var result = [Int?](repeating: nil, count: units.count + 1)
        result[units.count] = 0
        for index in units.indices.dropFirst() {
            let previous = units[index - 1]
            if case .accidental = previous.element { continue }
            if case let .lyricMark(mark) = previous.element, mark.beginsWhatFollows { continue }
            var afterDivision = false
            if case .division = previous.element { afterDivision = true }
            switch units[index].element {
            case .division, .forcedBreak:
                continue
            case let .lyricMark(mark):
                // An asterisk begins no staff, though it is written with the
                // word after it (`*~ab`).
                result[index] = mark.beginsWhatFollows && !mark.text.hasPrefix("*")
                    ? (afterDivision ? -50 : 0)
                    : nil
            case .clef:
                result[index] = 50
            case .accidental:
                // A break before an accidental is a break before its note.
                if index + 1 < units.count {
                    result[index] = neumePenalty(index + 1, afterDivision: afterDivision)
                }
            case .neume:
                result[index] = neumePenalty(index, afterDivision: afterDivision)
            }
        }
        return result
    }

    /// Sets a staff's units in place. Each is set after the notation before
    /// it, by the space its syllable and word call for, and moved on as far
    /// as its lyric must go to clear the lyric before it: by a word's space,
    /// or, within a word, to touch it. A syllable that the notes would part
    /// from the one before is joined to it if the note spaces since may
    /// close up so far, and is otherwise parted from it by one hyphen. A full
    /// staff gives up `condensation` of the share of each note space that it
    /// may; a staff that is justified adds `stretch` before each word, bar,
    /// rubric and clef after its first, never within a word.
    private func place(
        _ units: ArraySlice<LayoutUnit>,
        frame: LineFrame,
        spacing: Spacing,
        condensation: CGFloat,
        stretch: CGFloat = 0,
        endsWithinWord: Bool
    ) -> LinePlacement {
        let base = units.startIndex
        let count = units.count
        var result = LinePlacement(
            x: Array(repeating: 0, count: count),
            lyricX: Array(repeating: nil, count: count),
            notationRight: frame.notationStart,
            textRight: frame.staffLeft
        )
        var previousNotation: Int?
        var previousLyric: Int?
        var lyricRight: CGFloat?
        // The right of the last neume's ink.
        var notesRight: CGFloat?
        // Where the next text may begin: the staff's left, a bar's line, or
        // a clef that changes within the staff.
        var textFloor = frame.staffLeft
        var crossedDivision = false
        // The note spaces since the last lyric, each with the share of it
        // still to give up: a syllable may close them to join the one before.
        var run: [(index: Int, spare: CGFloat)] = []
        var leadingClefs = true
        // Whether the next neume's accidental has already stretched, or
        // not, before it.
        var stretchDecided = false
        let kept = 1 - Spacing.condensable * condensation

        func space(before index: Int) -> CGFloat {
            guard let previousNotation,
                  case let .neume(previous) = units[base + previousNotation].element else {
                if let previousNotation,
                   case .division = units[base + previousNotation].element {
                    return spacing.afterDivision
                }
                return 0
            }
            var next = units[base + index].element
            if case .accidental = next, base + index + 1 < units.endIndex {
                // An accidental takes the space of the neume it governs.
                next = units[base + index + 1].element
            }
            switch next {
            case let .neume(neume):
                guard neume.syllableID == previous.syllableID else {
                    return neume.startsWord ? spacing.betweenWords : spacing.betweenSyllables
                }
                // The neumes of a syllable, as the GABC parts them.
                switch neume.spaceBefore {
                case .automatic:
                    // A quilisma is sung from the note before it.
                    return neume.notes.first?.shape == .quilisma ? 0 : spacing.withinSyllable
                case .joined:
                    return 0
                case let .slashes(count):
                    return CGFloat(count) * spacing.withinSyllable
                case .space:
                    return 2 * spacing.withinSyllable
                }
            case .clef:
                return spacing.beforeClef
            case .accidental:
                return spacing.betweenSyllables
            default:
                return 0
            }
        }

        /// Whether the staff stretches before this unit: before each word,
        /// bar, rubric and clef after its first.
        func stretches(_ unit: LayoutUnit) -> Bool {
            guard previousNotation != nil || previousLyric != nil else { return false }
            switch unit.element {
            case .division, .lyricMark, .clef:
                return true
            case .neume:
                return unit.hasLyric && unit.join == .word
            case .accidental, .forcedBreak:
                return false
            }
        }

        for index in 0..<count {
            let unit = units[base + index]
            switch unit.element {
            case .forcedBreak:
                continue

            case .clef where leadingClefs:
                // Drawn at the staff's start, with the clef it sets.
                result.x[index] = frame.staffLeft

            case .clef, .accidental:
                leadingClefs = false
                let natural = space(before: index)
                var left = previousNotation == nil
                    ? frame.notationStart
                    : result.notationRight + natural * kept
                left = max(left, frame.notationStart)
                var stretchesHere = stretches(unit)
                if case .accidental = unit.element, base + index + 1 < units.endIndex {
                    // An accidental takes the stretch of the word it begins.
                    stretchesHere = stretches(units[base + index + 1])
                    stretchDecided = true
                }
                if stretchesHere {
                    left += stretch
                    result.stretchPoints += 1
                }
                result.x[index] = left
                result.notationRight = left + unit.inkRight
                if case .clef = unit.element {
                    textFloor = max(textFloor, left)
                }
                result.spare += natural * Spacing.condensable
                run.append((index, natural * Spacing.condensable * (1 - condensation)))
                previousNotation = index

            case .division:
                leadingClefs = false
                // A bar follows its syllable's words as well as its notes.
                var left = max(
                    previousNotation == nil ? frame.notationStart : result.notationRight,
                    lyricRight ?? frame.notationStart
                )
                if stretches(unit) {
                    left += stretch
                    result.stretchPoints += 1
                }
                result.x[index] = left
                result.notationRight = left + unit.inkRight
                textFloor = max(textFloor, left + unit.inkRight / 2)
                crossedDivision = true
                run.append((index, 0))
                previousNotation = index

            case .lyricMark:
                leadingClefs = false
                // A rubric clears the notes before it. After a bar or a clef
                // it stands where words would, under the room they keep for
                // the notes.
                var left = max(
                    textFloor,
                    notesRight.map { $0 + spacing.withinSyllable } ?? frame.staffLeft
                )
                if let lyricRight {
                    left = max(left, lyricRight + spacing.word)
                }
                if stretches(unit) {
                    left += stretch
                    result.stretchPoints += 1
                }
                result.x[index] = left
                result.lyricX[index] = left
                lyricRight = left + unit.lyricWidth
                result.notationRight = max(result.notationRight, left + unit.lyricWidth)
                previousLyric = index
                previousNotation = index
                crossedDivision = false
                run.removeAll()

            case .neume:
                leadingClefs = false
                let natural = space(before: index)
                let spare = natural * Spacing.condensable * (1 - condensation)
                let start = previousNotation == nil
                    ? frame.notationStart
                    : result.notationRight + natural * kept
                var position = max(start, frame.notationStart) - unit.inkLeft
                if unit.hasLyric {
                    let floor = textFloor + unit.lyricFocus
                    if let lyricRight, let previousLyric {
                        let solid = lyricRight + unit.lyricFocus
                        switch unit.join {
                        case .word:
                            position = max(position, solid + spacing.word, floor)
                        case .written:
                            position = max(position, solid, floor)
                        case .syllable where crossedDivision:
                            // A bar within a word parts its syllables.
                            position = max(position, solid + spacing.hyphen, floor)
                            result.hyphenated.append(previousLyric)
                        case .syllable:
                            let opening = position - solid
                            let closable = run.reduce(spare) { $0 + $1.spare }
                            if opening <= Spacing.touching {
                                position = max(position, solid)
                            } else if opening <= closable + Spacing.touching {
                                // Close up the notes since the syllable before,
                                // each space by its share, till the two meet.
                                let shift = min(opening, closable)
                                var given: CGFloat = 0
                                for entry in run {
                                    given += entry.spare
                                    result.x[entry.index] -= shift * given / closable
                                }
                                position -= shift
                            } else {
                                position = max(position, solid + spacing.hyphen)
                                result.hyphenated.append(previousLyric)
                            }
                        }
                    } else {
                        position = max(position, floor)
                    }
                }
                if !stretchDecided, stretches(unit) {
                    position += stretch
                    result.stretchPoints += 1
                }
                stretchDecided = false
                result.x[index] = position
                result.notationRight = position + unit.inkRight
                notesRight = position + unit.inkRight
                result.spare += natural * Spacing.condensable
                // An accidental stands just before the neume it governs.
                if let previousNotation, case .accidental = units[base + previousNotation].element {
                    result.x[previousNotation] = position + unit.inkLeft
                        - units[base + previousNotation].inkRight
                }
                if unit.hasLyric {
                    let left = position - unit.lyricFocus
                    result.lyricX[index] = left
                    lyricRight = left + unit.lyricWidth
                    previousLyric = index
                    crossedDivision = false
                    run.removeAll()
                } else {
                    run.append((index, spare))
                }
                previousNotation = index
            }
        }

        // A bar between two neumes stands midway between their notes, though
        // not over the words before it.
        for index in 0..<count {
            guard case .division = units[base + index].element,
                  index > 0, index + 1 < count,
                  units[base + index - 1].neume != nil,
                  units[base + index + 1].neume != nil else {
                continue
            }
            let before = units[base + index - 1]
            let after = units[base + index + 1]
            let width = units[base + index].inkRight
            let middle = (result.x[index - 1] + before.inkRight + result.x[index + 1] + after.inkLeft) / 2
            let wordsEnd = before.hasLyric
                ? (result.lyricX[index - 1] ?? 0) + before.lyricWidth
                : -CGFloat.greatestFiniteMagnitude
            result.x[index] = max(middle, wordsEnd) - width / 2
        }

        // A word that goes on in the next staff ends this one with a hyphen.
        if endsWithinWord, let previousLyric, let right = lyricRight,
           units[base + previousLyric].neume != nil {
            result.hyphenated.append(previousLyric)
            lyricRight = right + spacing.hyphen
        }
        result.textRight = lyricRight ?? frame.staffLeft
        return result
    }

    /// Sets a staff in place: at its natural spacing, closed up by as little
    /// as fits it when it is too full, and justified when it is not. The last
    /// staff of a paragraph, before a forced break or the score's end, is
    /// justified only to bring a closing bar to the staff's end, and then
    /// only by a little.
    private func placeLine(
        _ units: ArraySlice<LayoutUnit>,
        frame: LineFrame,
        spacing: Spacing,
        endsWithinWord: Bool,
        endsParagraph: Bool
    ) -> LinePlacement {
        func place(_ condensation: CGFloat, stretch: CGFloat = 0) -> LinePlacement {
            self.place(
                units,
                frame: frame,
                spacing: spacing,
                condensation: condensation,
                stretch: stretch,
                endsWithinWord: endsWithinWord
            )
        }
        let natural = place(0)
        guard natural.fits(frame) else {
            var best = place(1)
            guard best.fits(frame) else { return best }
            var low: CGFloat = 0
            var high: CGFloat = 1
            for _ in 0..<10 {
                let middle = (low + high) / 2
                let attempt = place(middle)
                if attempt.fits(frame) {
                    high = middle
                    best = attempt
                } else {
                    low = middle
                }
            }
            return best
        }
        let slack = natural.slack(in: frame)
        guard slack > 0.5, natural.stretchPoints > 0 else { return natural }
        let stretch = slack / CGFloat(natural.stretchPoints)
        if endsParagraph {
            let endsWithDivision = units.last(where: { !$0.forcesBreak }).map {
                if case .division = $0.element { return true }
                return false
            } ?? false
            guard endsWithDivision, stretch <= spacing.maximumStretch else { return natural }
        }
        return place(0, stretch: stretch)
    }

    /// Breaks the units into staffs, a paragraph at a time between forced
    /// breaks, choosing the breaks that best serve the whole paragraph, as
    /// TeX and Gregorio choose them: each staff is reckoned by how far it
    /// must be stretched or closed up and by what its break costs, and a
    /// last staff that holds a lone syllable is avoided.
    private func breakLines(
        _ units: [LayoutUnit],
        frame: (Int) -> LineFrame,
        spacing: Spacing,
        wordContinues: [Bool]
    ) -> [Range<Int>] {
        guard !units.isEmpty else { return [0..<0] }
        let penalties = Self.breakPenalties(units)
        var lines: [Range<Int>] = []
        var start = 0
        while start < units.count {
            let end = units[start...].firstIndex(where: \.forcesBreak).map { $0 + 1 } ?? units.count
            lines += breakParagraph(
                units,
                start..<end,
                frame: { lineStart in frame(lineStart == 0 ? 0 : 1) },
                spacing: spacing,
                wordContinues: wordContinues,
                penalties: penalties
            )
            start = end
        }
        return lines
    }

    private func breakParagraph(
        _ units: [LayoutUnit],
        _ paragraph: Range<Int>,
        frame: (Int) -> LineFrame,
        spacing: Spacing,
        wordContinues: [Bool],
        penalties: [Int?]
    ) -> [Range<Int>] {
        let candidates = [paragraph.lowerBound]
            + (paragraph.lowerBound + 1..<paragraph.upperBound).filter { penalties[$0] != nil }
            + [paragraph.upperBound]
        var cost = Array(repeating: Double.infinity, count: candidates.count)
        var previous = Array(repeating: -1, count: candidates.count)
        cost[0] = 0

        for end in 1..<candidates.count {
            let b = candidates[end]
            let endsParagraph = b == paragraph.upperBound
            let penalty = endsParagraph ? 0 : Double(penalties[b] ?? 0)
            for begin in stride(from: end - 1, through: 0, by: -1) where cost[begin].isFinite {
                let a = candidates[begin]
                let lineFrame = frame(a)
                let natural = place(
                    units[a..<b],
                    frame: lineFrame,
                    spacing: spacing,
                    condensation: 0,
                    endsWithinWord: wordContinues[b]
                )
                var badness: Double
                if natural.fits(lineFrame) {
                    if endsParagraph {
                        badness = 0
                    } else {
                        // A staff with nowhere to stretch, a word alone, is
                        // reckoned as if stretched in one place.
                        let stretch = natural.slack(in: lineFrame) / CGFloat(max(1, natural.stretchPoints))
                        badness = 100 * pow(Double(stretch / spacing.maximumStretch), 3)
                    }
                } else {
                    let condensed = place(
                        units[a..<b],
                        frame: lineFrame,
                        spacing: spacing,
                        condensation: 1,
                        endsWithinWord: wordContinues[b]
                    )
                    if condensed.fits(lineFrame) {
                        let overflow = -natural.slack(in: lineFrame)
                        let share = natural.spare > 0 ? min(1, Double(overflow / natural.spare)) : 1
                        badness = 100 * pow(share, 3)
                    } else if begin == end - 1, previous[end] < 0 {
                        // Nothing shorter fits: the staff is overfull, and
                        // worse than any that fits.
                        badness = 1e9
                    } else {
                        // A longer staff will not fit either.
                        break
                    }
                }
                var demerits = pow(10 + badness, 2)
                    + (penalty < 0 ? -penalty * penalty : penalty * penalty)
                if endsParagraph, begin > 0,
                   units[a..<b].filter(\.hasLyric).count <= 1 {
                    // A last staff of one syllable is worse than a loose
                    // staff before it.
                    demerits += 1_000_000
                }
                if cost[begin] + demerits < cost[end] {
                    cost[end] = cost[begin] + demerits
                    previous[end] = begin
                }
            }
        }

        // Every end has a way back: the staff from the candidate before it
        // either fits or is taken overfull.
        var breaks: [Int] = []
        var index = candidates.count - 1
        while index > 0, previous[index] >= 0 {
            breaks.append(candidates[index])
            index = previous[index]
        }
        var lines: [Range<Int>] = []
        var lineStart = paragraph.lowerBound
        for lineEnd in breaks.reversed() {
            lines.append(lineStart..<lineEnd)
            lineStart = lineEnd
        }
        return lines.isEmpty ? [paragraph] : lines
    }

    /// What a neume's builder needs of the notes beside it: the last note of
    /// the neume the GABC joins it to (`!`), from which its first stem may
    /// hang, and the first note of the next neume, by which a lone oriscus
    /// turns.
    private struct NeumeContext {
        var joinedFrom: GregorianNote?
        var next: GregorianNote?
    }

    /// Engraves a neume as Exsurge builds one, note by note from the left.
    /// Each note is set where the last ended, joined to it by a stem where
    /// the two stand more than a step apart. A clivis and a porrectus hang
    /// from a stem on their left; a virga's stem stands inside its head's
    /// right edge; a pes sets its upper note over the lower, joined on the
    /// right; a porrectus sets its last note over the end of its oblique;
    /// and the diamonds of a climacus step down from one another unjoined.
    private func compose(
        neume: GregorianNeume,
        context: NeumeContext = NeumeContext(),
        originX: CGFloat,
        staffCenter: CGFloat,
        staffTop: CGFloat,
        staffBottom: CGFloat,
        staffStep: CGFloat,
        glyphScale: CGFloat,
        notationScale: CGFloat,
        lineIndex: Int
    ) -> Composition {
        var result = Composition()
        let notes = neume.notes
        let punctumWidth = 100 * glyphScale
        // Exsurge's space between the parts of a neume: a quarter staff space.
        let intraNeumeSpacing = staffStep / 2
        // The weight of the stems that bind a neume's notes.
        let stemWidth = max(1, 18 * glyphScale)

        func noteY(_ pitch: Int) -> CGFloat {
            staffCenter - CGFloat(pitch - 6) * staffStep
        }
        func width(_ name: GregorianGlyphName) -> CGFloat {
            GregorianGlyphCatalog.definition(for: name).bounds.width * glyphScale
        }

        // Each note's glyph, the left of its first head, and the advance
        // from one head to the next of a note written twice or thrice.
        var glyphs = notes.map { _ in GregorianGlyphName.punctumQuadratum }
        var lefts = notes.map { _ in originX }
        var advances = notes.map { _ in CGFloat(0) }
        // The notes drawn with a virga's stem.
        var virgae = Set<Int>()

        // The builder's place, and the note the next may be joined to.
        var x = originX
        var last: (pitch: Int, alignsRight: Bool)?
        var lineIsHanging = false
        var minX = originX

        /// Exsurge's neume line: from the upper note's centre to the lower's,
        /// and on past the lower for a stem that hangs, a clivis's or a
        /// porrectus's.
        func stem(at stemX: CGFloat, from pitchA: Int, to pitchB: Int, hanging: Bool, eventID: String) {
            let upper = max(pitchA, pitchB) - 6
            var lower = min(pitchA, pitchB) - 6
            var overhang: CGFloat = 0
            if hanging {
                if upper - lower > 4 { lower = upper - 4 }
                // From a note on a line a step above the next, within the
                // staff, the stem reaches a step further.
                if upper - lower == 1, abs(upper) % 2 == 1, lower > -3 { lower -= 1 }
                overhang = GregorianGlyphCatalog.definition(for: .punctumQuadratum).bounds.height
                    * glyphScale / 2.2
            }
            result.strokes.append(
                GregorianPlacedStroke(
                    kind: .connector,
                    start: CGPoint(x: stemX + stemWidth / 2, y: noteY(upper + 6)),
                    end: CGPoint(x: stemX + stemWidth / 2, y: noteY(lower + 6) + overhang),
                    lineWidth: stemWidth,
                    eventID: eventID
                )
            )
        }

        func noteAt(_ index: Int, _ glyph: GregorianGlyphName, withLine: Bool = true) {
            let note = notes[index]
            let alignsRight = GregorianGlyphCatalog.definition(for: glyph).alignment == .right
            if withLine, let previous = last,
               lineIsHanging || previous.alignsRight || abs(previous.pitch - note.pitch) > 1 {
                let stemX = max(minX, x - stemWidth)
                stem(at: stemX, from: previous.pitch, to: note.pitch, hanging: lineIsHanging, eventID: note.id)
                if !alignsRight { x = stemX }
            }
            glyphs[index] = glyph
            if alignsRight, last != nil {
                lefts[index] = x - width(glyph)
            } else {
                lefts[index] = x
                // A note written twice or thrice (`hss`) sets a head for each,
                // a quarter staff space apart, an eighth after a stropha.
                advances[index] = width(glyph) + (note.shape == .stropha ? intraNeumeSpacing / 2 : intraNeumeSpacing)
                x += width(glyph) + CGFloat(note.repetitionCount - 1) * advances[index]
            }
            last = (note.pitch, alignsRight)
            lineIsHanging = false
        }

        func virgaAt(_ index: Int) {
            noteAt(index, .punctumQuadratum)
            // The stem stands inside the head's right edge; a virga written
            // twice or thrice (`hvv`) sets each head a quarter staff space
            // after the last one's stem.
            advances[index] = punctumWidth - stemWidth + intraNeumeSpacing
            x = lefts[index] + CGFloat(notes[index].repetitionCount - 1) * advances[index]
                + punctumWidth - stemWidth
            virgae.insert(index)
            last = (notes[index].pitch, false)
            lineIsHanging = false
        }

        func advance(by distance: CGFloat) {
            last = nil
            lineIsHanging = false
            x += distance
        }

        /// Starts a stem to hang on the left of the next note, down to
        /// `lower`, or joins the next note to the neume the GABC joins this
        /// one to.
        func lineFrom(_ lower: Int) {
            if x == originX, let joined = context.joinedFrom {
                last = (joined.pitch, false)
                minX = originX - stemWidth
            } else {
                last = (notes[lower].pitch, false)
                lineIsHanging = true
            }
        }

        /// Ends a stem from the last note, inside its right edge, down to
        /// `index`'s.
        func lineEnding(at index: Int) {
            guard let previous = last else { return }
            x -= stemWidth
            stem(at: x, from: previous.pitch, to: notes[index].pitch, hanging: true, eventID: notes[index].id)
            last = (notes[index].pitch, false)
        }

        func podatus(_ lower: Int, _ upper: Int) {
            var lowerGlyph: GregorianGlyphName
            let upperGlyph: GregorianGlyphName
            switch notes[upper].liquescence {
            case .small:
                lowerGlyph = .beginningAscLiquescent
                upperGlyph = .terminatingAscLiquescent
            case .ascending:
                lowerGlyph = .punctumQuadratum
                upperGlyph = .punctumQuadratumAscLiquescent
            case .descending:
                lowerGlyph = .punctumQuadratum
                upperGlyph = .punctumQuadratumDesLiquescent
            case .none:
                let wide = notes[upper].pitch - notes[lower].pitch > 1
                lowerGlyph = wide ? .podatusLower : .podatusLowerShort
                upperGlyph = wide ? .podatusUpper : .podatusUpperShort
            }
            if notes[lower].shape == .quilisma { lowerGlyph = .quilisma }
            noteAt(lower, lowerGlyph)
            noteAt(upper, upperGlyph)
            last = nil
        }

        func clivisUpper(_ upper: Int, _ lower: Int?, glyph: GregorianGlyphName = .punctumQuadratum) {
            if notes[upper].shape == .oriscus {
                noteAt(upper, .oriscusDes, withLine: false)
                return
            }
            var glyph = glyph
            if let lower {
                lineFrom(lower)
                lineIsHanging = notes[lower].pitch < notes[upper].pitch
                if notes[lower].liquescence == .small { glyph = .beginningDesLiquescent }
            }
            noteAt(upper, glyph)
        }

        /// The glyph of a note that ends a descent: a clivis's lower note, a
        /// torculus's last.
        func descentEnd(_ index: Int) -> GregorianGlyphName {
            switch notes[index].liquescence {
            case .small: .terminatingDesLiquescent
            case .ascending: .punctumQuadratumAscLiquescent
            case .descending: .punctumQuadratumDesLiquescent
            case .none: .punctumQuadratum
            }
        }

        func clivis(_ upper: Int, _ lower: Int) {
            clivisUpper(upper, lower)
            noteAt(lower, descentEnd(lower))
            last = nil
        }

        func inclinata(from start: Int) {
            // Each diamond steps from the last by its interval, as Exsurge
            // steps them: the advance of a diamond and a tenth for a unison,
            // two thirds of one for each step.
            let advance = width(.punctumInclinatum)
            for index in start..<notes.count {
                if index > start {
                    let steps = abs(notes[index - 1].pitch - notes[index].pitch)
                    x += advance * (steps == 0 ? 1.1 : CGFloat(steps) * 2 / 3)
                }
                glyphs[index] = notes[index].liquescence == .small
                    ? .punctumInclinatumLiquescent
                    : .punctumInclinatum
                lefts[index] = x
            }
            last = nil
        }

        func porrectusSwash(_ start: Int, _ end: Int) {
            if let previous = last,
               lineIsHanging || previous.alignsRight || abs(previous.pitch - notes[start].pitch) > 1 {
                x = max(minX, x - stemWidth)
                stem(at: x, from: previous.pitch, to: notes[start].pitch, hanging: lineIsHanging, eventID: notes[start].id)
            }
            let swash: GregorianGlyphName = switch max(1, min(4, notes[start].pitch - notes[end].pitch)) {
            case 1: .porrectus1
            case 2: .porrectus2
            case 3: .porrectus3
            default: .porrectus4
            }
            glyphs[start] = swash
            lefts[start] = x
            x += width(swash)
            // The swash engraves the second note too, at its right end.
            glyphs[end] = .none
            lefts[end] = x - punctumWidth
            last = (notes[end].pitch, false)
            lineIsHanging = false
        }

        /// A lone note's glyph, as Exsurge draws a punctum.
        func punctumGlyph(_ note: GregorianNote) -> GregorianGlyphName {
            if note.liquescence != .none {
                return switch (note.shape, note.liquescence) {
                case (.inclinatum, _): .punctumInclinatumLiquescent
                case (.oriscus, _): .oriscusLiquescent
                case (_, .ascending): .punctumQuadratumAscLiquescent
                case (_, .descending): .punctumQuadratumDesLiquescent
                default: .punctumQuadratumLiquescent
                }
            }
            if note.isCavum { return .punctumCavum }
            return switch note.shape {
            case .inclinatum: .punctumInclinatum
            case .quilisma: .quilisma
            case .oriscus: .oriscusDes
            case .stropha: .stropha
            default: .punctumQuadratum
            }
        }

        /// A note of a stropha's form: a stropha only where the GABC writes
        /// one (`s`); a plain note repeated is a punctum.
        func strophaGlyph(_ note: GregorianNote) -> GregorianGlyphName {
            if note.shape == .stropha { return .stropha }
            switch note.liquescence {
            case .ascending: return .punctumQuadratumAscLiquescent
            case .descending: return .punctumQuadratumDesLiquescent
            default: return note.isCavum ? .punctumCavum : .punctumQuadratum
            }
        }

        func head(_ index: Int) {
            if notes[index].shape == .virga {
                virgaAt(index)
            } else {
                noteAt(index, notes[index].shape == .quilisma ? .quilisma : .punctumQuadratum)
            }
        }

        // Where the diamonds begin, if all the notes from there are diamonds.
        let inclinataStart = (0..<notes.count).first { start in
            notes[start...].allSatisfy { $0.shape == .inclinatum }
        }
        switch neume.form {
        case .punctaInclinata:
            inclinata(from: 0)
        case .climacus where inclinataStart == 1, .ancus where inclinataStart == 1:
            head(0)
            advance(by: intraNeumeSpacing)
            inclinata(from: 1)
        case .pesSubpunctis where inclinataStart == 2:
            podatus(0, 1)
            advance(by: intraNeumeSpacing * 0.68)
            inclinata(from: 2)
        case .virga, .bivirga, .trivirga:
            for index in notes.indices {
                if index > 0 { advance(by: intraNeumeSpacing) }
                virgaAt(index)
            }
        case .apostropha, .distropha, .tristropha:
            let strophae = notes.prefix(2).filter { $0.shape == .stropha }.count
            let gap = neume.form == .tristropha
                ? (notes[0].shape == .stropha ? intraNeumeSpacing / 2 : intraNeumeSpacing)
                : intraNeumeSpacing - CGFloat(strophae) * intraNeumeSpacing / 4
            for index in notes.indices {
                if index > 0 { advance(by: gap) }
                noteAt(index, strophaGlyph(notes[index]))
            }
        case .oriscus where notes.count == 1:
            let rises = context.next.map { $0.pitch > notes[0].pitch } ?? false
            noteAt(0, notes[0].liquescence != .none ? .oriscusLiquescent : (rises ? .oriscusAsc : .oriscusDes))
        case .clivis where notes.count == 2:
            clivis(0, 1)
        case .ancus where notes.count == 3:
            clivisUpper(0, 1)
            let middleGlyph: GregorianGlyphName = notes[2].liquescence == .small
                ? .beginningDesLiquescent
                : .punctumQuadratum
            clivisUpper(1, notes[0].pitch - notes[1].pitch > 1 ? 0 : nil, glyph: middleGlyph)
            noteAt(2, descentEnd(2))
            last = nil
        case .podatus where notes.count == 2:
            podatus(0, 1)
        case .pesQuassus where notes.count == 2:
            noteAt(0, notes[0].shape == .oriscus ? .oriscusAsc : .punctumQuadratum)
            if notes[1].pitch - notes[0].pitch == 1 {
                virgaAt(1)
            } else {
                noteAt(1, notes[1].liquescence == .descending ? .punctumQuadratumDesLiquescent : .punctumQuadratum)
                lineEnding(at: 0)
            }
        case .porrectus where notes.count == 3:
            lineFrom(1)
            porrectusSwash(0, 1)
            let third: GregorianGlyphName = switch notes[2].liquescence {
            case .small: .terminatingAscLiquescent
            case .descending: .punctumQuadratumDesLiquescent
            default: notes[2].pitch - notes[1].pitch > 1 ? .podatusUpper : .podatusUpperShort
            }
            noteAt(2, third)
        case .porrectusFlexus where notes.count == 4:
            lineFrom(1)
            porrectusSwash(0, 1)
            noteAt(2, notes[3].liquescence == .small ? .punctumQuadratumDesLiquescent : .punctumQuadratum)
            noteAt(3, descentEnd(3))
        case .torculus where notes.count == 3:
            noteAt(0, notes[0].shape == .quilisma ? .quilisma : .punctumQuadratum)
            noteAt(1, .punctumQuadratum)
            noteAt(2, descentEnd(2))
        case .torculusResupinus where notes.count == 4:
            noteAt(0, notes[0].shape == .quilisma ? .quilisma : .punctumQuadratum)
            porrectusSwash(1, 2)
            noteAt(3, notes[3].liquescence == .small
                ? .terminatingAscLiquescent
                : (notes[2].liquescence == .descending ? .punctumQuadratumDesLiquescent : .podatusUpper))
        case .torculusResupinusFlexus where notes.count == 5:
            noteAt(0, notes[0].shape == .quilisma ? .quilisma : .punctumQuadratum)
            porrectusSwash(1, 2)
            noteAt(3, notes[4].liquescence == .small ? .punctumQuadratumDesLiquescent : .punctumQuadratum)
            noteAt(4, descentEnd(4))
        case .scandicus where notes.count == 3:
            if notes[2].shape == .virga {
                podatus(0, 1)
                virgaAt(2)
            } else {
                noteAt(0, notes[0].shape == .quilisma ? .quilisma : .punctumQuadratum)
                podatus(1, 2)
            }
        case .scandicusFlexus where notes.count == 4:
            if notes[2].shape == .virga {
                podatus(0, 1)
                advance(by: intraNeumeSpacing)
                clivis(2, 3)
            } else {
                noteAt(0, .punctumQuadratum)
                podatus(1, 2)
                advance(by: intraNeumeSpacing)
                noteAt(3, notes[3].liquescence == .ascending
                    ? .punctumQuadratumAscLiquescent
                    : (notes[3].liquescence == .descending ? .punctumQuadratumDesLiquescent : .punctumQuadratum))
            }
        case .salicus where notes.count == 3:
            noteAt(0, .punctumQuadratum)
            advance(by: intraNeumeSpacing)
            noteAt(1, .oriscusAsc)
            switch notes[2].liquescence {
            case .small: noteAt(2, .terminatingAscLiquescent)
            case .ascending: noteAt(2, .punctumQuadratumAscLiquescent)
            case .descending: noteAt(2, .punctumQuadratumDesLiquescent)
            case .none: virgaAt(2)
            }
        case .salicusFlexus where notes.count == 4:
            noteAt(0, .punctumQuadratum)
            advance(by: intraNeumeSpacing)
            noteAt(1, .oriscusAsc)
            noteAt(2, notes[3].liquescence == .small ? .punctumQuadratumDesLiquescent : .punctumQuadratum)
            noteAt(3, descentEnd(3))
        default:
            // A note alone, or a run the forms above do not name: its notes
            // one after another, joined where they leap.
            for index in notes.indices {
                if notes[index].shape == .virga {
                    virgaAt(index)
                } else {
                    noteAt(index, punctumGlyph(notes[index]))
                }
            }
        }

        // The head each note's signs are set by, the last of a note written
        // twice or thrice; nil for a custos.
        var signFrames = [CGRect?](repeating: nil, count: notes.count)
        for (index, note) in notes.enumerated() {
            let y = noteY(note.pitch)
            let glyph = glyphs[index]
            // Where the note's head is, whatever glyph engraves it.
            let headFrame = CGRect(
                x: lefts[index],
                y: y - punctumWidth / 2,
                width: punctumWidth,
                height: punctumWidth
            )
            if note.isCustos {
                // Drawn as the custos that ends a staff is drawn: without
                // signs, and not as a note that the cantor guide may light.
                let custos = placedGlyph(
                    kind: .custos,
                    anchor: CGPoint(
                        x: lefts[index]
                            + GregorianGlyphCatalog.definition(for: .custosShort).origin.x * glyphScale,
                        y: y
                    ),
                    scale: glyphScale
                )
                result.glyphs.append(custos)
                appendEvent(note, neume: neume, frame: custos.frame, lineIndex: lineIndex, to: &result)
                continue
            }
            if glyph == .none {
                // The second note of a porrectus, which its swash engraves.
                signFrames[index] = headFrame
                appendLedgerLines(
                    for: headFrame,
                    noteY: y,
                    staffTop: staffTop,
                    staffBottom: staffBottom,
                    staffGap: staffStep * 2,
                    scale: notationScale,
                    eventID: note.id,
                    strokes: &result.strokes
                )
                appendEvent(note, neume: neume, frame: headFrame, lineIndex: lineIndex, to: &result)
                continue
            }
            let isSwash = [.porrectus1, .porrectus2, .porrectus3, .porrectus4].contains(glyph)

            let definition = GregorianGlyphCatalog.definition(for: glyph)
            let first = placedGlyph(
                kind: .catalog(glyph),
                anchor: CGPoint(x: lefts[index] + definition.origin.x * glyphScale, y: y),
                scale: glyphScale,
                eventID: note.id
            )
            // The same glyph again for each further time the note is written.
            let heads = [first] + (1..<max(1, note.repetitionCount)).map { repetition in
                GregorianPlacedGlyph(
                    kind: first.kind,
                    frame: first.frame.offsetBy(dx: CGFloat(repetition) * advances[index], dy: 0),
                    eventID: note.id
                )
            }
            // Signs written after a repeated note belong to the last of them.
            let placed = heads[heads.count - 1]
            var noteFrame = heads.reduce(first.frame) { $0.union($1.frame) }
            if virgae.contains(index) {
                // A virga's stem falls nearly two steps from a note in a
                // space, nearly three from one on a line.
                let length = (abs(note.pitch - 6) % 2 == 0 ? 1.8 : 2.7) * staffStep
                for head in heads {
                    let stemX = head.frame.minX + punctumWidth - stemWidth / 2
                    result.strokes.append(
                        GregorianPlacedStroke(
                            kind: .connector,
                            start: CGPoint(x: stemX, y: y),
                            end: CGPoint(x: stemX, y: y + length),
                            lineWidth: stemWidth,
                            eventID: note.id
                        )
                    )
                    noteFrame = noteFrame.union(
                        CGRect(x: stemX - stemWidth / 2, y: y, width: stemWidth, height: length)
                    )
                }
            }

            if let accidental = note.accidental {
                let accidentalKind = Self.glyphKind(for: accidental)
                let accidentalScale = glyphScale * 0.9
                let accidentalDefinition = GregorianGlyphCatalog.definition(
                    for: accidentalKind.catalogName
                )
                let accidentalMinX = first.frame.minX
                    - accidentalDefinition.bounds.width * accidentalScale
                    - 2 * notationScale
                let accidentalGlyph = placedGlyph(
                    kind: accidentalKind,
                    anchor: CGPoint(
                        x: accidentalMinX + accidentalDefinition.origin.x * accidentalScale,
                        y: y
                    ),
                    scale: accidentalScale,
                    eventID: note.id
                )
                result.glyphs.append(accidentalGlyph)
                result.inkFrame = result.inkFrame.union(accidentalGlyph.frame)
            }

            result.glyphs.append(contentsOf: heads)
            result.inkFrame = result.inkFrame.union(noteFrame)

            signFrames[index] = placed.frame

            appendLedgerLines(
                // A swash's first head; any other glyph's whole frame.
                for: isSwash ? headFrame : noteFrame,
                noteY: y,
                staffTop: staffTop,
                staffBottom: staffBottom,
                staffGap: staffStep * 2,
                scale: notationScale,
                eventID: note.id,
                strokes: &result.strokes
            )
            appendEvent(note, neume: neume, frame: noteFrame, lineIndex: lineIndex, to: &result)
        }

        appendSigns(
            of: neume,
            frames: signFrames,
            glyphs: glyphs,
            staffCenter: staffCenter,
            staffStep: staffStep,
            glyphScale: glyphScale,
            notationScale: notationScale,
            to: &result
        )
        return result
    }

    /// Sets a neume's dots, episemata and ictus as Exsurge places them, the
    /// dot at the Liber's size: on the side the GABC writes, or else on the
    /// side the neume gives each note. A dot stands just after its note, in
    /// its space, or in the space above a note on a line; a double dot after
    /// a neume's last two notes gives each its own. An episema keeps clear
    /// of its note and of the staff's lines, and runs on over the next
    /// note's on the same side. An ictus stands a step beyond its note, off
    /// the line there.
    private func appendSigns(
        of neume: GregorianNeume,
        frames: [CGRect?],
        glyphs: [GregorianGlyphName],
        staffCenter: CGFloat,
        staffStep: CGFloat,
        glyphScale: CGFloat,
        notationScale: CGFloat,
        to result: inout Composition
    ) {
        let notes = neume.notes
        func noteY(_ pitch: Int) -> CGFloat {
            staffCenter - CGFloat(pitch - 6) * staffStep
        }
        func onLine(_ note: GregorianNote) -> Bool {
            abs(note.pitch - 6) % 2 == 1
        }

        // Dots. A double dot on a neume's last note dots the note before it
        // too, set after the last.
        var dots = notes.map { $0.moraCount }
        var dotAfterNext = notes.map { _ in false }
        if let last = notes.indices.last, last > 0, notes[last].moraCount > 1 {
            dots[last] -= 1
            dots[last - 1] += 1
            dotAfterNext[last - 1] = true
        }
        var dotSides = notes.map(\.moraPosition)
        func lowerDot(_ index: Int) {
            if dotSides[index] == nil, dots[index] > 0 { dotSides[index] = .below }
        }
        switch neume.form {
        case .podatus, .pesQuassus, .pesSubpunctis:
            // The lower note of a pes, on a line, has its dot below it.
            if notes.count > 1, onLine(notes[0]) { lowerDot(0) }
        case .clivis, .torculus, .porrectus:
            // A clivis's lower note, on a line a step down, has its dot
            // below it.
            let pair = neume.form == .clivis ? (0, 1) : (1, 2)
            if notes.count > pair.1, notes[pair.0].pitch - notes[pair.1].pitch == 1, onLine(notes[pair.1]) {
                lowerDot(pair.1)
            }
        default:
            break
        }
        if [.climacus, .punctaInclinata, .pesSubpunctis].contains(neume.form), notes.count > 1 {
            // So has the last diamond, on a line a step below the one before.
            let last = notes.count - 1
            if notes[last - 1].pitch - notes[last].pitch == 1, onLine(notes[last]) { lowerDot(last) }
        }
        let dotDefinition = GregorianGlyphCatalog.definition(for: .mora)
        // The Liber's dot: a third of a staff space across, a tenth of one
        // after its note.
        let dotScale = 0.33 * staffStep * 2 / dotDefinition.bounds.width
        let dotWidth = dotDefinition.bounds.width * dotScale
        let dotGap = 0.1 * staffStep * 2
        for (index, note) in notes.enumerated() where dots[index] > 0 {
            guard let frame = frames[index] else { continue }
            var y = noteY(note.pitch)
            switch dotSides[index] {
            case .above: y -= (onLine(note) ? 0.75 : 1.75) * staffStep
            case .below: y += (onLine(note) ? 0.75 : 1.75) * staffStep
            case nil:
                if onLine(note) {
                    y -= 0.75 * staffStep
                } else if index + 1 < notes.count, notes[index + 1].pitch == note.pitch - 1 {
                    y -= 0.25 * staffStep
                }
            }
            var left = frame.maxX + dotGap
            if index + 1 < notes.count, let next = frames[index + 1] {
                if dotAfterNext[index] {
                    left = next.maxX + dotGap
                } else if next.maxX > frame.maxX {
                    // Between the note and the end of the next.
                    left = frame.maxX + (next.maxX - frame.maxX - dotWidth) / 2
                }
            }
            for dot in 0..<dots[index] {
                let mora = placedGlyph(
                    kind: .mora,
                    anchor: CGPoint(
                        x: left + CGFloat(dot) * (dotWidth + dotGap) + dotDefinition.origin.x * dotScale,
                        y: y
                    ),
                    scale: dotScale,
                    eventID: note.id
                )
                result.glyphs.append(mora)
                result.inkFrame = result.inkFrame.union(mora.frame)
            }
        }

        // Episemata, on the side the neume gives each note unless the GABC
        // writes one.
        var episemaSides = notes.map(\.episemaPosition)
        func side(_ index: Int, _ position: GregorianEpisemaPosition) {
            if index < notes.count, episemaSides[index] == nil { episemaSides[index] = position }
        }
        func clivisSides(_ upper: Int, _ lower: Int) -> Bool {
            side(upper, .above)
            let hasTop = upper < notes.count && notes[upper].hasEpisema
            side(lower, hasTop ? .above : .below)
            return hasTop
        }
        func podatusSides(_ lower: Int, _ upper: Int) {
            side(lower, .below)
            side(upper, .above)
        }
        switch neume.form {
        case .clivis:
            _ = clivisSides(0, 1)
        case .ancus:
            _ = clivisSides(0, 2)
            _ = clivisSides(1, 2)
        case .podatus, .pesQuassus, .pesSubpunctis:
            podatusSides(0, 1)
        case .porrectus:
            _ = clivisSides(0, 1)
            podatusSides(1, 2)
        case .porrectusFlexus:
            side(0, .above)
            let hasTop = clivisSides(2, 3) || notes[0].hasEpisema
            side(1, hasTop ? .above : .below)
        case .torculus:
            let hasTop = clivisSides(1, 2)
            side(0, hasTop ? .above : .below)
        case .torculusResupinus, .torculusResupinusFlexus:
            _ = clivisSides(1, 2)
            podatusSides(2, 3)
            _ = clivisSides(1, 0)
        case .scandicus where notes.count > 2 && notes[2].shape == .virga:
            podatusSides(0, 1)
        case .scandicus, .scandicusFlexus:
            side(0, .below)
            podatusSides(1, 2)
        case .salicus, .salicusFlexus:
            for index in notes.indices { side(index, .below) }
        default:
            break
        }
        let episemaWeight = max(1, 1.3 * notationScale)
        let staffLines = 4
        var runs: [(side: GregorianEpisemaPosition, y: CGFloat, left: CGFloat, right: CGFloat, eventID: String)] = []
        for (index, note) in notes.enumerated() where note.hasEpisema {
            guard let frame = frames[index] else { continue }
            let position = episemaSides[index] ?? .above
            // Exsurge's place: a quarter step clear of the note, centred
            // between it and the next line if there is room, else in the
            // nearest third of a space off the line.
            let clearance = staffStep * 0.25
            var step: CGFloat
            if position == .below {
                var y = frame.maxY + clearance - staffCenter
                if glyphs[index] == .none { y += staffStep / 2 }
                step = (y / staffStep).rounded(.up)
                if abs(step.truncatingRemainder(dividingBy: 2)) == 0 {
                    step = (step + 3 / 4 + (y - clearance) / staffStep) / 2
                } else {
                    step = (((1.5 * y) / staffStep - 0.5).rounded(.up) * 2 + 1) / 3
                    if abs(step).truncatingRemainder(dividingBy: 2) == 1 {
                        step += abs(step) < CGFloat(staffLines) ? 2 / 3 : 1 / 3
                    }
                }
            } else {
                let y = frame.minY - clearance - staffCenter
                step = (y / staffStep).rounded(.down)
                if abs(step.truncatingRemainder(dividingBy: 2)) == 0 {
                    step = (step - 3 / 4 + (y + clearance) / staffStep) / 2
                } else {
                    step = (((1.5 * y) / staffStep - 0.5).rounded(.down) * 2 + 1) / 3
                    if abs(step).truncatingRemainder(dividingBy: 2) == 1 {
                        step -= abs(step) < CGFloat(staffLines) ? 2 / 3 : 1 / 3
                    }
                }
            }
            var left = frame.minX
            var width = frame.width
            switch glyphs[index] {
            case .porrectus1, .porrectus2, .porrectus3, .porrectus4:
                width = staffStep
            case .none:
                width = staffStep
                left = frame.maxX - width
            case .punctumInclinatum where index > 0
                && glyphs[index - 1] == .punctumInclinatum
                && notes[index - 1].pitch - note.pitch == 1:
                width *= 2 / 3
                left += 0.5 * width
            case .punctumInclinatumLiquescent:
                width *= 2 / 3
                left += 0.25 * width
            default:
                break
            }
            let y = staffCenter + step * staffStep
            // An episema runs on over the next note's on the same side, at
            // the height that clears both.
            if let previous = runs.last, previous.side == position, left - previous.right < staffStep {
                runs[runs.count - 1] = (
                    position,
                    position == .above ? min(previous.y, y) : max(previous.y, y),
                    previous.left,
                    left + width,
                    previous.eventID
                )
            } else {
                runs.append((position, y, left, left + width, note.id))
            }
        }
        for run in runs {
            result.strokes.append(
                GregorianPlacedStroke(
                    kind: .episema,
                    start: CGPoint(x: run.left, y: run.y),
                    end: CGPoint(x: run.right, y: run.y),
                    lineWidth: episemaWeight,
                    eventID: run.eventID
                )
            )
        }

        // The ictus: above a virga, the top of a rise and the head of a
        // fall, and below any other note, unless the GABC writes its side.
        for (index, note) in notes.enumerated() where note.hasIctus {
            guard let frame = frames[index] else { continue }
            let above: Bool
            if let written = note.ictusPosition {
                above = written == .above
            } else if note.shape == .virga {
                above = true
            } else if note.shape != .inclinatum, index + 1 < notes.count {
                above = notes[index + 1].pitch < note.pitch
            } else {
                above = index > 0 && index == notes.count - 1 && notes[index - 1].pitch < note.pitch
            }
            let position = note.pitch - 6 + (above ? 1 : -1)
            // Off a staff line, or nearer if the space is free; further from
            // an episema on the same side.
            let onStaffLine = abs(position) % 2 == 1 && abs(position) < staffLines
            let episemaBeside = note.hasEpisema && (episemaSides[index] ?? .above) == (above ? .above : .below)
            var extra: CGFloat = 0
            if note.shape == .inclinatum, !onStaffLine, !episemaBeside { extra = 0.3 }
            if episemaBeside { extra = 0.4 }
            let offset = staffStep * (extra + (onStaffLine ? 0.3 : -0.2)) * (above ? -1 : 1)
            var centerX = frame.midX
            switch glyphs[index] {
            case .porrectus1, .porrectus2, .porrectus3, .porrectus4: centerX = frame.minX + staffStep / 2
            case .none: centerX = frame.maxX - staffStep / 2
            default: break
            }
            let ictus = placedGlyph(
                kind: .catalog(above ? .verticalEpisemaAbove : .verticalEpisemaBelow),
                anchor: CGPoint(x: centerX, y: noteY(position + 6) + offset),
                scale: glyphScale,
                eventID: note.id
            )
            result.glyphs.append(ictus)
            result.inkFrame = result.inkFrame.union(ictus.frame)
        }
    }

    /// The letters a syllable is sung on: the first vowel of its last word
    /// (a note may carry the end of one word and the beginning of the next,
    /// `t Dó`), with the vowels that follow it (`ae`, `au`), past a
    /// consonantal i that begins it (`ius`, `Ie`) or a u after q or g (`qui`,
    /// `guis`); the whole syllable, if it has no vowel.
    private static func sungVowel(in characters: [Character]) -> Range<Int> {
        func vowel(from start: Int) -> Range<Int>? {
            guard var first = characters[start...].firstIndex(where: isVowel) else { return nil }
            if first + 1 < characters.count, isVowel(characters[first + 1]) {
                let letter = baseLetter(characters[first])
                if letter == "i", first == start {
                    first += 1
                } else if letter == "u", first > start,
                          let before = baseLetter(characters[first - 1]),
                          before == "q" || before == "g" {
                    first += 1
                }
            }
            var end = first + 1
            while end < characters.count, isVowel(characters[end]) { end += 1 }
            return first..<end
        }
        let lastWord = characters.lastIndex(where: \.isWhitespace).map { $0 + 1 } ?? 0
        return (lastWord < characters.count ? vowel(from: lastWord) : nil)
            ?? vowel(from: 0)
            ?? 0..<characters.count
    }

    /// A letter without its accents, in lower case: `ǽ` is `æ`, `Ë` is `e`.
    private static func baseLetter(_ character: Character) -> Character? {
        // The first scalar: the accents decompose into scalars of their own,
        // which the character would still hold.
        String(character).decomposedStringWithCanonicalMapping.lowercased()
            .unicodeScalars.first.map(Character.init)
    }

    private static func isVowel(_ character: Character) -> Bool {
        baseLetter(character).map { "aeiouyæœ".contains($0) } ?? false
    }

    private static func glyphKind(for accidental: GregorianAccidental) -> GregorianGlyphKind {
        switch accidental {
        case .flat: .flat
        case .natural: .natural
        case .sharp: .sharp
        }
    }

    private func appendEvent(
        _ note: GregorianNote,
        neume: GregorianNeume,
        frame: CGRect,
        lineIndex: Int,
        to result: inout Composition
    ) {
        result.events.append(
            GregorianEventPlacement(
                eventID: note.id,
                neumeID: neume.id,
                frame: frame.insetBy(dx: -2, dy: -4),
                lineIndex: lineIndex
            )
        )
        result.inkFrame = result.inkFrame.union(frame)
    }

    private func appendClef(
        _ clef: GregorianClef,
        x: CGFloat,
        staffTop: CGFloat,
        staffGap: CGFloat,
        glyphScale: CGFloat,
        notationScale: CGFloat,
        glyphs: inout [GregorianPlacedGlyph]
    ) {
        let lineY = staffTop + CGFloat(4 - clef.line) * staffGap
        let kind: GregorianGlyphKind = clef.kind == .c ? .cClef : .fClef
        let clefGlyph = placedGlyph(
            kind: kind,
            anchor: CGPoint(x: x, y: lineY),
            scale: glyphScale
        )
        glyphs.append(clefGlyph)
        if clef.flattensB {
            // The flat stands on the B the clef flattens, a step below the
            // clef's line, just after the clef.
            let flatScale = glyphScale * 0.9
            let flat = GregorianGlyphCatalog.definition(for: .flat)
            glyphs.append(
                placedGlyph(
                    kind: .flat,
                    anchor: CGPoint(
                        x: clefGlyph.frame.maxX + notationScale + flat.origin.x * flatScale,
                        y: lineY + staffGap / 2
                    ),
                    scale: flatScale
                )
            )
        }
    }

    private func appendDivision(
        _ division: GregorianDivision,
        x: CGFloat,
        staffTop: CGFloat,
        staffGap: CGFloat,
        notationScale: CGFloat,
        strokes: inout [GregorianPlacedStroke]
    ) {
        let height: CGFloat = switch division {
        case .minima: staffGap
        case .minor: staffGap * 2
        case .major, .final: staffGap * 3
        }
        // A quarter bar crosses the top line, as the Liber draws it.
        let top = division == .minima
            ? staffTop - staffGap / 2
            : staffTop + (staffGap * 3 - height) / 2
        let count = division == .final ? 2 : 1
        for index in 0..<count {
            let barX = x + CGFloat(index) * 4 * notationScale
            strokes.append(
                GregorianPlacedStroke(
                    kind: .division(division),
                    start: CGPoint(x: barX, y: top),
                    end: CGPoint(x: barX, y: top + height),
                    lineWidth: max(1.2, 1.45 * notationScale)
                )
            )
        }
    }

    private func placedGlyph(
        kind: GregorianGlyphKind,
        anchor: CGPoint,
        scale: CGFloat,
        eventID: String? = nil
    ) -> GregorianPlacedGlyph {
        let definition = GregorianGlyphCatalog.definition(for: kind.catalogName)
        return GregorianPlacedGlyph(
            kind: kind,
            frame: CGRect(
                x: anchor.x - definition.origin.x * scale,
                y: anchor.y - definition.origin.y * scale,
                width: definition.bounds.width * scale,
                height: definition.bounds.height * scale
            ),
            eventID: eventID
        )
    }

    private func lyricMetrics(
        _ lyric: String,
        style: GregorianLyricStyle,
        centeredRange: Range<Int>? = nil,
        fontSize: CGFloat,
        fontName: String
    ) -> (width: CGFloat, focus: CGFloat) {
        guard !lyric.isEmpty else { return (0, 0) }
        let width = measureLyric(lyric, style: style, fontSize: fontSize, fontName: fontName)
        let characters = Array(lyric)
        let centre = centeredRange.flatMap { range in
            range.lowerBound >= 0 && range.upperBound <= characters.count && !range.isEmpty
                ? range
                : nil
        } ?? Self.sungVowel(in: characters)
        let prefix = String(characters[..<centre.lowerBound])
        let nucleus = String(characters[centre])
        let focus = measureLyric(prefix, style: style, fontSize: fontSize, fontName: fontName)
            + measureLyric(nucleus, style: style, fontSize: fontSize, fontName: fontName) / 2
        return (width, focus)
    }

    private func measureLyric(
        _ lyric: String,
        style: GregorianLyricStyle = .regular,
        fontSize: CGFloat,
        fontName: String
    ) -> CGFloat {
        guard !lyric.isEmpty else { return 0 }
        let regularFont = CTFontCreateWithName(
            fontName as CFString,
            fontSize,
            nil
        )
        let traits: CTFontSymbolicTraits = switch style {
        case .accented:
            .boldTrait
        case .preparatory:
            .italicTrait
        case .regular, .rubric:
            []
        }
        let font = traits.isEmpty
            ? regularFont
            : CTFontCreateCopyWithSymbolicTraits(
                regularFont,
                fontSize,
                nil,
                traits,
                traits
            ) ?? regularFont
        let attributes = [kCTFontAttributeName: font] as CFDictionary
        let attributed = CFAttributedStringCreate(nil, lyric as CFString, attributes)!
        let line = CTLineCreateWithAttributedString(attributed)
        // Measured exactly: syllables of a word set solid meet exactly.
        return CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
    }

    /// The ledger lines a note at `noteY` needs, as wide as its head at
    /// `frame` and a staff line's weight, as the books draw them. They
    /// follow the note's pitch, not its glyph, whose stem or swash may reach
    /// far from the head.
    private func appendLedgerLines(
        for frame: CGRect,
        noteY: CGFloat,
        staffTop: CGFloat,
        staffBottom: CGFloat,
        staffGap: CGFloat,
        scale: CGFloat,
        eventID: String,
        strokes: inout [GregorianPlacedStroke]
    ) {
        if noteY <= staffTop - staffGap / 2 {
            var y = staffTop - staffGap
            while y >= noteY - 1 {
                strokes.append(
                    GregorianPlacedStroke(
                        kind: .ledger,
                        start: CGPoint(x: frame.minX - 3 * scale, y: y),
                        end: CGPoint(x: frame.maxX + 3 * scale, y: y),
                        lineWidth: max(0.72, 0.78 * scale),
                        eventID: eventID
                    )
                )
                y -= staffGap
            }
        } else if noteY >= staffBottom + staffGap / 2 {
            var y = staffBottom + staffGap
            while y <= noteY + 1 {
                strokes.append(
                    GregorianPlacedStroke(
                        kind: .ledger,
                        start: CGPoint(x: frame.minX - 3 * scale, y: y),
                        end: CGPoint(x: frame.maxX + 3 * scale, y: y),
                        lineWidth: max(0.72, 0.78 * scale),
                        eventID: eventID
                    )
                )
                y += staffGap
            }
        }
    }

    private func mergeLedgerLines(
        _ strokes: [GregorianPlacedStroke],
        scale: CGFloat
    ) -> [GregorianPlacedStroke] {
        let nonLedger = strokes.filter {
            if case .ledger = $0.kind { return false }
            return true
        }
        let ledgers = strokes.filter {
            if case .ledger = $0.kind { return true }
            return false
        }.sorted {
            abs($0.start.y - $1.start.y) < 0.1
                ? $0.start.x < $1.start.x
                : $0.start.y < $1.start.y
        }
        var merged: [GregorianPlacedStroke] = []
        for ledger in ledgers {
            if let last = merged.last,
               abs(last.start.y - ledger.start.y) < 0.1,
               ledger.start.x <= last.end.x + 2 * scale {
                merged[merged.count - 1] = GregorianPlacedStroke(
                    kind: .ledger,
                    start: last.start,
                    end: CGPoint(x: max(last.end.x, ledger.end.x), y: last.end.y),
                    lineWidth: max(last.lineWidth, ledger.lineWidth),
                    eventID: last.eventID
                )
            } else {
                merged.append(ledger)
            }
        }
        return nonLedger + merged
    }

    private func makeHitFrames(
        _ raw: [RawNeumePlacement],
        width: CGFloat,
        scale: CGFloat
    ) -> [GregorianNeumePlacement] {
        var result: [GregorianNeumePlacement] = []
        let grouped = Dictionary(grouping: raw, by: \.lineIndex)
        for lineIndex in grouped.keys.sorted() {
            let placements = (grouped[lineIndex] ?? []).sorted { $0.inkFrame.midX < $1.inkFrame.midX }
            for (index, placement) in placements.enumerated() {
                let previousMid = index > 0 ? placements[index - 1].inkFrame.midX : 0
                let nextMid = index + 1 < placements.count
                    ? placements[index + 1].inkFrame.midX
                    : width
                let leftBoundary = index > 0
                    ? (previousMid + placement.inkFrame.midX) / 2
                    : max(0, placement.inkFrame.midX - 22 * scale)
                let rightBoundary = index + 1 < placements.count
                    ? (placement.inkFrame.midX + nextMid) / 2
                    : min(width, placement.inkFrame.midX + 22 * scale)
                let desiredWidth = max(44, placement.inkFrame.width + 14 * scale)
                let centeredLeft = placement.inkFrame.midX - desiredWidth / 2
                let hitLeft = max(leftBoundary, centeredLeft)
                let hitRight = min(rightBoundary, hitLeft + desiredWidth)
                result.append(
                    GregorianNeumePlacement(
                        id: placement.neume.id,
                        eventIDs: placement.neume.eventIDs,
                        noteCount: placement.neume.writtenNoteCount,
                        lyric: placement.neume.lyric,
                        inkFrame: placement.inkFrame,
                        hitFrame: CGRect(
                            x: hitLeft,
                            y: placement.staffTop - 7 * scale,
                            width: max(1, hitRight - hitLeft),
                            height: max(44, placement.staffBottom - placement.staffTop + 7 * scale)
                        ),
                        lineIndex: lineIndex
                    )
                )
            }
        }
        return result
    }
}
