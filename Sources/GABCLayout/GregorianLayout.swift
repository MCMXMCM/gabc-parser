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

    public init(
        notationScale: Double = 1,
        lyricScale: Double = 1,
        lyricFontName: String = GregorianLyricFont.defaultName
    ) {
        self.notationScale = notationScale
        self.lyricScale = lyricScale
        self.lyricFontName = lyricFontName
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
    /// The advance, in punctum widths, from one note to the next on the same
    /// pitch: the spacing of a bivirga or a tristropha.
    private static let repeatedNoteAdvance: CGFloat = 0.92

    public init() {}

    public static func openingLabel(forMode mode: String?) -> String? {
        guard let mode = mode?.trimmingCharacters(in: .whitespacesAndNewlines),
              !mode.isEmpty else {
            return nil
        }

        let compact = mode
            .lowercased()
            .filter { !$0.isWhitespace }
        let modePrefixes = [
            "viii", "vii", "iii", "vi", "iv", "ii", "v", "i",
            "8", "7", "6", "5", "4", "3", "2", "1"
        ]
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
                || variant == "-" else {
            return nil
        }
        return mode.uppercased()
    }

    public func layout(
        score: GregorianScore,
        width requestedWidth: CGFloat,
        metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
        openingLabel: String? = nil
    ) -> GregorianLayout {
        let notationScale = CGFloat(
            GregorianLayoutMetrics.clampedNotationScale(
                metrics.notationScale
            )
        )
        let lyricScale = max(
            0.5,
            min(
                3.5,
                CGFloat(metrics.lyricScale) * notationScale
            )
        )
        let lyricFontName = metrics.lyricFontName
        let width = max(240, requestedWidth)
        let glyphScale = notationScale / 16
        let staffStep = 100 * glyphScale // one GABC pitch step / half staff-space
        let staffGap = staffStep * 2
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
            annotation: Self.openingLabel(forMode: openingLabel)
        )
        let initialScale = max(notationScale, min(1.35, lyricScale))
        let initialFontSize = 56 * initialScale
        let initialAnnotationFontSize = 13 * max(notationScale, min(1.25, lyricScale))
        let initialWidth = openingInitial.map {
            measureLyric($0.text, fontSize: initialFontSize, fontName: lyricFontName)
        } ?? 0
        let initialAnnotationWidth = openingInitial?.annotation.map {
            measureLyric($0, fontSize: initialAnnotationFontSize, fontName: lyricFontName)
        } ?? 0
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
            placeFitting(
                units[range],
                frame: lineFrame(lineIndex),
                spacing: spacing,
                endsWithinWord: wordContinues[range.upperBound]
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
                    : nil
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
                        lineWidth: max(0.72, 0.78 * notationScale)
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
                initials.append(
                    GregorianPlacedInitial(
                        text: openingInitial.text,
                        annotation: openingInitial.annotation,
                        annotationOrigin: openingInitial.annotation.map { _ in
                            CGPoint(
                                x: sidePadding + (initialColumn - initialWidth) / 2,
                                y: max(0, staffTop - initialAnnotationFontSize)
                            )
                        },
                        annotationFontSize: initialAnnotationFontSize,
                        origin: CGPoint(
                            x: sidePadding + (initialColumn - initialWidth) / 2,
                            y: staffTop
                        ),
                        width: initialWidth,
                        fontSize: initialFontSize,
                        fontName: lyricFontName,
                        neumeID: openingInitial.neumeID
                    )
                )
            }

            var inkRight = frame.notationStart
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
                            anchor: CGPoint(x: x + unit.inkRight / 2, y: y),
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
                    inkRight = max(inkRight, Self.inkRight(of: composition))

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
                // A staff too short to be closed up (a single long melisma)
                // may still reach the custos, which then stands clear of it
                // as far as the staff allows.
                let custosWidth = GregorianGlyphCatalog.definition(for: .custosShort)
                    .bounds.width * glyphScale
                let custosX = min(
                    max(
                        width - sidePadding - 7 * notationScale,
                        inkRight + 1.5 * notationScale
                    ),
                    width - sidePadding - custosWidth
                )
                glyphs.append(
                    placedGlyph(
                        kind: .custos,
                        anchor: CGPoint(x: custosX, y: nextY),
                        scale: glyphScale
                    )
                )
            }
        }

        strokes = mergeLedgerLines(strokes, scale: notationScale)
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
        let breakPenalty: Int
        let forcesBreak: Bool

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

        func fits(_ frame: LineFrame) -> Bool {
            notationRight <= frame.notationEnd + 0.01 && textRight <= frame.textEnd + 0.01
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
        annotation: String?
    ) -> OpeningInitial? {
        guard let annotation,
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
        return OpeningInitial(
            text: String(neume.lyric[initialIndex]),
            annotation: annotation,
            remainder: String(neume.lyric[neume.lyric.index(after: initialIndex)...]),
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
        openingInitialFontSize: CGFloat?
    ) -> LineVerticalMetrics {
        let notes = units.compactMap(\.neume).flatMap(\.notes)
        let accidentalPitches = units.compactMap { unit -> Int? in
            guard case let .accidental(accidental) = unit.element else { return nil }
            return accidental.pitch
        }
        let pitches = notes.map(\.pitch) + accidentalPitches + (custosPitch.map { [$0] } ?? [])
        let highestPitch = pitches.max() ?? 9
        let lowestPitch = pitches.min() ?? 3
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
        let belowStaff = lowestPitch < 3
            ? CGFloat(3 - lowestPitch) * staffStep + 10 * notationScale
            : 0
        let lyricOffset = belowStaff > 0
            ? belowStaff + 6 * notationScale
            : 2 * notationScale
        let systemGap = 4 * notationScale
        let notationHeight = topInset + staffGap * 3 + lyricOffset + lyricHeight + systemGap
        let initialHeight = openingInitialFontSize.map {
            topInset + ceil($0 * 1.15) + systemGap
        } ?? 0
        let height = max(notationHeight, initialHeight)
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
        var previousWasAccidental = false
        var previousWasLyricMark = false
        // The last lyric written, and the syllable it was sung on: none for
        // a rubric, which ends any word before it.
        var previousLyric: (text: String, syllableID: String?)?
        let punctum = 100 * glyphScale

        func unit(
            _ element: GregorianNotationElement,
            inkLeft: CGFloat = 0,
            inkRight: CGFloat,
            lyricText: String = "",
            lyricWidth: CGFloat = 0,
            lyricFocus: CGFloat = 0,
            join: LyricJoin = .word,
            breakPenalty: Int,
            forcesBreak: Bool = false
        ) -> LayoutUnit {
            LayoutUnit(
                element: element,
                inkLeft: inkLeft,
                inkRight: inkRight,
                lyricText: lyricText,
                lyricWidth: lyricWidth,
                lyricFocus: lyricFocus,
                join: join,
                breakPenalty: breakPenalty,
                forcesBreak: forcesBreak
            )
        }

        return score.elements.map { element in
            switch element {
            case .clef:
                previousWasAccidental = false
                previousWasLyricMark = false
                return unit(element, inkRight: 24 * notationScale, breakPenalty: 1_000)
            case let .accidental(accidental):
                previousWasAccidental = true
                previousWasLyricMark = false
                let definition = GregorianGlyphCatalog.definition(
                    for: Self.glyphKind(for: accidental.kind).catalogName
                )
                return unit(
                    element,
                    inkRight: definition.bounds.width * glyphScale * 0.9 + 4 * notationScale,
                    // Permit wrapping before an accidental, but never between
                    // the accidental and the neume it governs.
                    breakPenalty: 18
                )
            case let .division(division):
                previousWasAccidental = false
                previousWasLyricMark = false
                return unit(
                    element,
                    inkRight: (division == .final ? 17 : 12) * notationScale,
                    // A division belongs at the end of a phrase, not at the
                    // beginning of the next staff. The following word offers
                    // the usable break point after the bar.
                    breakPenalty: 1_000
                )
            case .forcedBreak:
                previousWasAccidental = false
                previousWasLyricMark = false
                return unit(element, inkRight: 0, breakPenalty: 0, forcesBreak: true)
            case let .lyricMark(mark):
                previousWasAccidental = false
                previousWasLyricMark = true
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
                    ),
                    // A note-less rubric belongs between the neighboring
                    // syllables and should not begin a staff by itself.
                    breakPenalty: 1_000
                )
            case let .neume(neume):
                let followsUnbreakableElement = previousWasAccidental || previousWasLyricMark
                previousWasAccidental = false
                previousWasLyricMark = false
                let opensScore = neume.id == openingInitial?.neumeID
                let lyricText = opensScore ? openingInitial?.remainder ?? neume.lyric : neume.lyric
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
                let ink = notationInk(neume, glyphScale: glyphScale, notationScale: notationScale)
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
                    // Chant may wrap between syllables when that produces a
                    // fuller staff (for example "pró-" / "ximos"). A custos,
                    // like a division, belongs at the end of what it follows.
                    breakPenalty: followsUnbreakableElement || neume.writtenNoteCount == 0
                        ? 1_000
                        : 18
                )
            }
        }
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
    /// dots, episemata and ledger lines.
    private func notationInk(
        _ neume: GregorianNeume,
        glyphScale: CGFloat,
        notationScale: CGFloat
    ) -> (left: CGFloat, right: CGFloat) {
        let staffStep = 100 * glyphScale
        let staffGap = staffStep * 2
        let composition = compose(
            neume: neume,
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
        guard left.isFinite, right.isFinite, right > left else { return (0, staffStep) }
        return (left, right)
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

    /// Sets a staff's units in place. Each is set after the notation before
    /// it, by the space its syllable and word call for, and moved on as far
    /// as its lyric must go to clear the lyric before it: by a word's space,
    /// or, within a word, to touch it. A syllable that the notes would part
    /// from the one before is joined to it if the note spaces since may
    /// close up so far, and is otherwise parted from it by one hyphen. A full
    /// staff gives up `condensation` of the share of each note space that it
    /// may.
    private func place(
        _ units: ArraySlice<LayoutUnit>,
        frame: LineFrame,
        spacing: Spacing,
        condensation: CGFloat,
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
        // Where the next text may begin: the staff's left, or a bar's line.
        var textFloor = frame.staffLeft
        var crossedDivision = false
        // The note spaces since the last lyric, each with the share of it
        // still to give up: a syllable may close them to join the one before.
        var run: [(index: Int, spare: CGFloat)] = []
        var leadingClefs = true
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
                if neume.syllableID == previous.syllableID {
                    // A quilisma is sung from the note before it.
                    return neume.notes.first?.shape == .quilisma ? 0 : spacing.withinSyllable
                }
                return neume.startsWord ? spacing.betweenWords : spacing.betweenSyllables
            case .clef:
                return spacing.beforeClef
            case .accidental:
                return spacing.betweenSyllables
            default:
                return 0
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
                let left = previousNotation == nil
                    ? frame.notationStart
                    : result.notationRight + natural * kept
                result.x[index] = max(left, frame.notationStart)
                result.notationRight = result.x[index] + unit.inkRight
                run.append((index, natural * Spacing.condensable * (1 - condensation)))
                previousNotation = index

            case .division:
                leadingClefs = false
                // A bar follows its syllable's words as well as its notes.
                let left = max(
                    previousNotation == nil ? frame.notationStart : result.notationRight,
                    lyricRight ?? frame.notationStart
                )
                result.x[index] = left
                result.notationRight = left + unit.inkRight
                textFloor = max(textFloor, left + unit.inkRight / 2)
                crossedDivision = true
                run.append((index, 0))
                previousNotation = index

            case .lyricMark:
                leadingClefs = false
                var left = max(
                    textFloor,
                    previousNotation == nil ? frame.staffLeft : result.notationRight + spacing.withinSyllable
                )
                if let lyricRight {
                    left = max(left, lyricRight + spacing.word)
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
                result.x[index] = position
                result.notationRight = position + unit.inkRight
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

        // A word that goes on in the next staff ends this one with a hyphen.
        if endsWithinWord, let previousLyric, let right = lyricRight,
           units[base + previousLyric].neume != nil {
            result.hyphenated.append(previousLyric)
            lyricRight = right + spacing.hyphen
        }
        result.textRight = lyricRight ?? frame.staffLeft
        return result
    }

    /// Sets a staff in place with its notes at their natural spacing, or,
    /// if they do not fit so, closed up by as little as fits them.
    private func placeFitting(
        _ units: ArraySlice<LayoutUnit>,
        frame: LineFrame,
        spacing: Spacing,
        endsWithinWord: Bool
    ) -> LinePlacement {
        func place(_ condensation: CGFloat) -> LinePlacement {
            self.place(
                units,
                frame: frame,
                spacing: spacing,
                condensation: condensation,
                endsWithinWord: endsWithinWord
            )
        }
        let natural = place(0)
        guard !natural.fits(frame) else { return natural }
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

    /// Breaks the units into staffs: each takes as many as fit it with its
    /// notes closed up as far as they may be, and then breaks where it best
    /// may, in its latter half.
    private func breakLines(
        _ units: [LayoutUnit],
        frame: (Int) -> LineFrame,
        spacing: Spacing,
        wordContinues: [Bool]
    ) -> [Range<Int>] {
        guard !units.isEmpty else { return [0..<0] }
        var lines: [Range<Int>] = []
        var start = 0
        while start < units.count {
            let lineFrame = frame(lines.count)
            var end = start
            var split: Int?
            while end < units.count {
                let unit = units[end]
                end += 1
                if unit.forcesBreak {
                    split = end
                    break
                }
                guard end - start > 1 else { continue }
                // Permit condensation before wrapping, as Exsurge does.
                let fits = place(
                    units[start..<end],
                    frame: lineFrame,
                    spacing: spacing,
                    condensation: 1,
                    endsWithinWord: wordContinues[end]
                ).fits(lineFrame)
                if fits { continue }
                let fallback = end - 1
                let earliest = start + max(1, (fallback - start) / 2)
                split = (earliest...fallback)
                    .filter { candidate in units[start..<candidate].contains { $0.neume != nil } }
                    .min { lhs, rhs in
                        units[lhs].breakPenalty == units[rhs].breakPenalty
                            ? lhs > rhs
                            : units[lhs].breakPenalty < units[rhs].breakPenalty
                    } ?? fallback
                break
            }
            let upper = split ?? end
            lines.append(start..<upper)
            start = upper
        }
        return lines
    }

    private func compose(
        neume: GregorianNeume,
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
        let punctumWidth = 100 * glyphScale
        var noteOrigins: [CGFloat] = []

        switch neume.form {
        case .podatus where neume.notes.count >= 2,
             .pesQuassus where neume.notes.count >= 2:
            noteOrigins = Array(repeating: originX, count: neume.notes.count)
            if neume.notes.count > 2 {
                for index in 2..<neume.notes.count {
                    noteOrigins[index] = originX + punctumWidth * CGFloat(index - 1)
                }
            }
        case .climacus, .ancus, .punctaInclinata:
            noteOrigins = neume.notes.indices.map { originX + CGFloat($0) * punctumWidth * 0.72 }
        case .porrectus where neume.notes.count >= 3,
             .porrectusFlexus where neume.notes.count >= 3:
            let swashWidth = GregorianGlyphCatalog.definition(for: Self.porrectusSwash(neume)).bounds.width * glyphScale
            noteOrigins = [originX, originX + swashWidth - punctumWidth]
            for index in 2..<neume.notes.count {
                noteOrigins.append(originX + swashWidth + CGFloat(index - 2) * punctumWidth * 0.92)
            }
        default:
            noteOrigins = neume.notes.indices.map { originX + CGFloat($0) * punctumWidth * 0.92 }
        }
        // A note written twice or thrice (`hvv`, `hsss`) draws a glyph for
        // each, and moves the notes after it along by as much.
        let repeatedAdvance = punctumWidth * Self.repeatedNoteAdvance
        var repeatedBefore = 0
        for index in neume.notes.indices {
            noteOrigins[index] += CGFloat(repeatedBefore) * repeatedAdvance
            repeatedBefore += neume.notes[index].repetitionCount - 1
        }

        let isPorrectus = neume.form == .porrectus || neume.form == .porrectusFlexus
        for (index, note) in neume.notes.enumerated() {
            let y = staffCenter - CGFloat(note.pitch - 6) * staffStep
            let noteOriginX = noteOrigins[index]
            // Where the note's head is, whatever glyph engraves it.
            let headFrame = CGRect(
                x: noteOriginX,
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
                        x: noteOriginX
                            + GregorianGlyphCatalog.definition(for: .custosShort).origin.x * glyphScale,
                        y: y
                    ),
                    scale: glyphScale
                )
                result.glyphs.append(custos)
                appendEvent(note, neume: neume, frame: custos.frame, lineIndex: lineIndex, to: &result)
                continue
            }
            var glyphName = glyphName(for: note)
            if note.shape == .oriscus, note.liquescence == .none,
               index + 1 < neume.notes.count {
                glyphName = neume.notes[index + 1].pitch < note.pitch ? .oriscusDes : .oriscusAsc
            }

            if [.apostropha, .distropha, .tristropha].contains(neume.form) {
                glyphName = .stropha
            } else if neume.form == .pesQuassus, neume.notes.count >= 2 {
                glyphName = index == 0 ? .oriscusAsc : (index == 1 ? .podatusUpper : glyphName)
            } else if neume.form == .podatus, neume.notes.count >= 2 {
                glyphName = index == 0 ? .podatusLower : (index == 1 ? .podatusUpper : glyphName)
            } else if isPorrectus, index == 0 {
                glyphName = Self.porrectusSwash(neume)
            } else if isPorrectus, index == 1 {
                // The porrectus swash already engraves the first two notes.
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

            let definition = GregorianGlyphCatalog.definition(for: glyphName)
            let first = placedGlyph(
                kind: .catalog(glyphName),
                anchor: CGPoint(x: noteOriginX + definition.origin.x * glyphScale, y: y),
                scale: glyphScale,
                eventID: note.id
            )
            // The same glyph again for each further time the note is written.
            let repetitions = (1..<max(1, note.repetitionCount)).map { repetition in
                GregorianPlacedGlyph(
                    kind: first.kind,
                    frame: first.frame.offsetBy(dx: CGFloat(repetition) * repeatedAdvance, dy: 0),
                    eventID: note.id
                )
            }
            // Signs written after a repeated note belong to the last of them.
            let placed = repetitions.last ?? first
            let noteFrame = repetitions.reduce(first.frame) { $0.union($1.frame) }

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

            if index > 0, !Self.connectedForms.contains(neume.form) {
                let previous = neume.notes[index - 1]
                if previous.pitch != note.pitch {
                    let previousY = staffCenter - CGFloat(previous.pitch - 6) * staffStep
                    let connectorX = noteOriginX + 0.7 * glyphScale
                    result.strokes.append(
                        GregorianPlacedStroke(
                            kind: .connector,
                            start: CGPoint(x: connectorX, y: previousY),
                            end: CGPoint(x: connectorX, y: y),
                            lineWidth: max(1, 18 * glyphScale),
                            eventID: note.id
                        )
                    )
                }
            }

            result.glyphs.append(first)
            result.glyphs.append(contentsOf: repetitions)
            result.inkFrame = result.inkFrame.union(noteFrame)

            for moraIndex in 0..<note.moraCount {
                let mora = placedGlyph(
                    kind: .mora,
                    anchor: CGPoint(
                        x: placed.frame.maxX + (5 + CGFloat(moraIndex) * 3.5) * notationScale,
                        y: y
                    ),
                    scale: glyphScale * 0.72,
                    eventID: note.id
                )
                result.glyphs.append(mora)
                result.inkFrame = result.inkFrame.union(mora.frame)
            }
            if note.hasEpisema {
                let above = note.episemaPosition == .above
                let episemaY = above ? placed.frame.minY - 3 * notationScale : placed.frame.maxY + 3 * notationScale
                result.strokes.append(
                    GregorianPlacedStroke(
                        kind: .episema,
                        start: CGPoint(x: placed.frame.minX - notationScale, y: episemaY),
                        end: CGPoint(x: placed.frame.maxX + notationScale, y: episemaY),
                        lineWidth: max(1, 1.3 * notationScale),
                        eventID: note.id
                    )
                )
            }
            if note.hasIctus {
                let above = note.episemaPosition == .above
                let ictusName: GregorianGlyphName = above
                    ? .verticalEpisemaAbove
                    : .verticalEpisemaBelow
                let ictus = placedGlyph(
                    kind: .catalog(ictusName),
                    anchor: CGPoint(
                        x: placed.frame.midX,
                        y: above
                            ? placed.frame.minY - 2 * notationScale
                            : placed.frame.maxY + 2 * notationScale
                    ),
                    scale: glyphScale,
                    eventID: note.id
                )
                result.glyphs.append(ictus)
                result.inkFrame = result.inkFrame.union(ictus.frame)
            }

            appendLedgerLines(
                // The swash's first head; any other glyph's whole frame.
                for: isPorrectus && index == 0 ? headFrame : noteFrame,
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
        return result
    }

    private func glyphName(for note: GregorianNote) -> GregorianGlyphName {
        if note.isCavum { return .punctumCavum }
        switch note.shape {
        case .punctum:
            return switch note.liquescence {
            case .ascending: .punctumQuadratumAscLiquescent
            case .descending: .punctumQuadratumDesLiquescent
            case .small: .punctumQuadratumLiquescent
            case .none: .punctumQuadratum
            }
        case .virga: return .virgaShort
        case .quilisma: return .quilisma
        case .liquescent:
            return note.liquescence == .ascending
                ? .terminatingAscLiquescent
                : .terminatingDesLiquescent
        case .inclinatum:
            return note.liquescence == .none ? .punctumInclinatum : .punctumInclinatumLiquescent
        case .oriscus:
            return note.liquescence == .none ? .oriscusAsc : .oriscusLiquescent
        case .stropha: return .stropha
        case .custos: return .custosShort
        }
    }

    /// Forms whose glyphs join their notes themselves, with no connector
    /// drawn between them.
    private static let connectedForms: Set<GregorianNeumeForm> = [
        .podatus, .pesQuassus, .porrectus, .porrectusFlexus
    ]

    /// The swash that engraves a porrectus's first two notes, by their interval.
    private static func porrectusSwash(_ neume: GregorianNeume) -> GregorianGlyphName {
        let first = neume.notes.first?.pitch ?? 0
        let second = neume.notes.dropFirst().first?.pitch ?? 0
        return switch max(1, min(4, abs(first - second))) {
        case 1: .porrectus1
        case 2: .porrectus2
        case 3: .porrectus3
        default: .porrectus4
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
        let top = division == .minima
            ? staffTop + staffGap
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
    /// `frame`. They follow the note's pitch, not its glyph, whose stem or
    /// swash may reach far from the head.
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
                        lineWidth: scale,
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
                        lineWidth: scale,
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
