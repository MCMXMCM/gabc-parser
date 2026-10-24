import Foundation

public struct GregorianSourceRange: Codable, Hashable, Sendable {
    public let lowerBound: Int
    public let upperBound: Int

    public init(lowerBound: Int, upperBound: Int) {
        self.lowerBound = lowerBound
        self.upperBound = upperBound
    }
}

public enum GregorianClefKind: String, Codable, Hashable, Sendable {
    case c
    case f
}

public struct GregorianClef: Codable, Hashable, Sendable {
    public let kind: GregorianClefKind
    public let line: Int
    public let flattensB: Bool

    public init(kind: GregorianClefKind, line: Int, flattensB: Bool = false) {
        self.kind = kind
        self.line = line
        self.flattensB = flattensB
    }
}

public enum GregorianAccidental: String, Codable, Hashable, Sendable {
    case flat
    case natural
    case sharp
}

public enum GregorianDivision: String, Codable, Hashable, Sendable {
    case minima
    case minor
    case major
    case final
}

public enum GregorianNoteShape: String, Codable, Hashable, Sendable {
    case punctum
    case virga
    case quilisma
    case liquescent
    case inclinatum
    case oriscus
    case stropha
    /// A custos written in the score itself (`f+`): a guide to the pitch
    /// that follows, usually before a change of clef. The compiled timeline
    /// holds an event for it, as for every pitch letter, but it is drawn as
    /// a custos and is never sung.
    case custos
}

public enum GregorianLiquescence: String, Codable, Hashable, Sendable {
    case none
    case small
    case ascending
    case descending
}

public enum GregorianEpisemaPosition: String, Codable, Hashable, Sendable {
    case above
    case below
}

public struct GregorianAccidentalMark: Codable, Hashable, Sendable {
    public let kind: GregorianAccidental
    public let pitch: Int
    public let sourceRange: GregorianSourceRange

    public init(
        kind: GregorianAccidental,
        pitch: Int,
        sourceRange: GregorianSourceRange
    ) {
        self.kind = kind
        self.pitch = pitch
        self.sourceRange = sourceRange
    }
}

/// Semantic neume families used by the native compositor. These deliberately
/// mirror Exsurge's named-neume layer rather than treating a neume as an
/// arbitrary row of noteheads.
public enum GregorianNeumeForm: String, Codable, Hashable, Sendable {
    case apostropha
    case bivirga
    case trivirga
    case climacus
    case clivis
    case ancus
    case distropha
    case oriscus
    case pesQuassus
    case pesSubpunctis
    case podatus
    case porrectus
    case porrectusFlexus
    case punctaInclinata
    case punctum
    case salicus
    case salicusFlexus
    case scandicus
    case scandicusFlexus
    case torculus
    case torculusResupinus
    case torculusResupinusFlexus
    case tristropha
    case virga
}

public enum GregorianLyricStyle: String, Codable, Hashable, Sendable {
    case regular
    case preparatory
    case accented
    case rubric
}

public struct GregorianNote: Codable, Hashable, Sendable, Identifiable {
    public let id: String
    public let pitch: Int
    public let relativePitch: Int
    public let shape: GregorianNoteShape
    public let accidental: GregorianAccidental?
    public let hasMora: Bool
    public let hasEpisema: Bool
    public let moraCount: Int
    /// The side of the note the GABC sets its dot on (`.1` above, `.0`
    /// below); nil where it leaves the side to the engraver.
    public let moraPosition: GregorianEpisemaPosition?
    /// The side the GABC sets the horizontal episema on (`_1`, `_0`); nil
    /// where it leaves the side to the engraver.
    public let episemaPosition: GregorianEpisemaPosition?
    public let liquescence: GregorianLiquescence
    public let isCavum: Bool
    public let hasIctus: Bool
    /// The side the GABC sets the ictus on (`'1`, `'0`); nil where it
    /// leaves the side to the engraver.
    public let ictusPosition: GregorianEpisemaPosition?
    /// How many notes the one pitch letter writes. GABC abbreviates two
    /// virgae or three strophae on a pitch as `hvv` and `hsss`; the compiled
    /// timeline holds a single event for the group, which is drawn and sung
    /// as that many notes.
    public let repetitionCount: Int
    public let sourceRange: GregorianSourceRange

    /// True for a custos, which guides the eye and is not sung.
    public var isCustos: Bool { shape == .custos }

    /// The notes this one event draws and sings: as many as it is written,
    /// and none for a custos.
    public var writtenNoteCount: Int { isCustos ? 0 : repetitionCount }

    /// A custos, or a note written more than once, is a neume in itself.
    var standsAlone: Bool { isCustos || repetitionCount > 1 }

    public init(
        id: String,
        pitch: Int,
        relativePitch: Int,
        shape: GregorianNoteShape,
        accidental: GregorianAccidental?,
        hasMora: Bool,
        hasEpisema: Bool,
        moraCount: Int = 0,
        moraPosition: GregorianEpisemaPosition? = nil,
        episemaPosition: GregorianEpisemaPosition? = nil,
        liquescence: GregorianLiquescence = .none,
        isCavum: Bool = false,
        hasIctus: Bool = false,
        ictusPosition: GregorianEpisemaPosition? = nil,
        repetitionCount: Int = 1,
        sourceRange: GregorianSourceRange
    ) {
        self.id = id
        self.pitch = pitch
        self.relativePitch = relativePitch
        self.shape = shape
        self.accidental = accidental
        self.hasMora = hasMora
        self.hasEpisema = hasEpisema
        self.moraCount = max(moraCount, hasMora ? 1 : 0)
        self.moraPosition = moraPosition
        self.episemaPosition = episemaPosition
        self.liquescence = liquescence
        self.isCavum = isCavum
        self.hasIctus = hasIctus
        self.ictusPosition = ictusPosition
        self.repetitionCount = max(1, repetitionCount)
        self.sourceRange = sourceRange
    }
}

/// The space a GABC group writes before a neume, after the neume before it
/// in the same group.
public enum GregorianNeumeSpace: Codable, Hashable, Sendable {
    /// None is written: the engraver's own, between the neumes it divides a
    /// run of notes into.
    case automatic
    /// `!`: the two neumes are joined.
    case joined
    /// `/`, `//`: a small space for each slash.
    case slashes(Int)
    /// A space within the group: a neumatic cut.
    case space
}

public struct GregorianNeume: Codable, Hashable, Sendable, Identifiable {
    public let id: String
    public let syllableID: String
    public let lyric: String
    public let lyricStyle: GregorianLyricStyle
    public let startsWord: Bool
    public let notes: [GregorianNote]
    public let form: GregorianNeumeForm
    public let sourceRange: GregorianSourceRange
    /// The letters of `lyric`, as character offsets, that a brace pair in
    /// the GABC marks to be centred under the first note (`{ia}.`); nil when
    /// none is marked, and the sung vowel is centred.
    public let centeredRange: Range<Int>?
    /// The space written before the neume, after the one before it in its
    /// group: `.automatic` for a group's first neume.
    public let spaceBefore: GregorianNeumeSpace

    public var eventIDs: [String] {
        notes.map(\.id)
    }

    /// The notes the neume draws and sings: one for each note, or as many
    /// as a doubled note is written, and none for a custos.
    public var writtenNoteCount: Int {
        notes.reduce(0) { $0 + $1.writtenNoteCount }
    }

    public init(
        id: String,
        syllableID: String,
        lyric: String,
        lyricStyle: GregorianLyricStyle = .regular,
        startsWord: Bool,
        notes: [GregorianNote],
        form: GregorianNeumeForm? = nil,
        sourceRange: GregorianSourceRange,
        centeredRange: Range<Int>? = nil,
        spaceBefore: GregorianNeumeSpace = .automatic
    ) {
        self.id = id
        self.syllableID = syllableID
        self.lyric = lyric
        self.lyricStyle = lyricStyle
        self.startsWord = startsWord
        self.notes = notes
        self.form = form ?? GregorianNeumeClassifier.classify(notes)
        self.sourceRange = sourceRange
        self.centeredRange = centeredRange
        self.spaceBefore = spaceBefore
    }
}

public struct GregorianLyricMark: Codable, Hashable, Sendable, Identifiable {
    /// Named by where it is written, after its score, as the score's notes
    /// are: `lyric-mark-5-7`, or `kyrie-lyric-mark-5-7`.
    public var id: String {
        "\(scoreID.map { "\($0)-" } ?? "")lyric-mark-\(sourceRange.lowerBound)-\(sourceRange.upperBound)"
    }

    public let text: String
    public let style: GregorianLyricStyle
    public let startsWord: Bool
    public let sourceRange: GregorianSourceRange
    /// The identifier of the score the mark was parsed in, if one was given.
    public let scoreID: String?
    /// Whether the mark heads the words after it, as ℣., ℟., a verse number
    /// or any sign written before a syllable's words does, rather than
    /// closing the words before it, as an asterisk, a flex or a repeat mark
    /// written after them or alone does.
    public let beginsWhatFollows: Bool

    public init(
        text: String,
        style: GregorianLyricStyle,
        startsWord: Bool,
        sourceRange: GregorianSourceRange,
        scoreID: String? = nil,
        beginsWhatFollows: Bool = false
    ) {
        self.text = text
        self.style = style
        self.startsWord = startsWord
        self.sourceRange = sourceRange
        self.scoreID = scoreID
        self.beginsWhatFollows = beginsWhatFollows
    }
}

public enum GregorianNotationElement: Codable, Hashable, Sendable {
    case clef(GregorianClef)
    case accidental(GregorianAccidentalMark)
    case neume(GregorianNeume)
    case lyricMark(GregorianLyricMark)
    case division(GregorianDivision)
    case forcedBreak
}

public struct GregorianScore: Codable, Hashable, Sendable {
    public let elements: [GregorianNotationElement]

    public init(elements: [GregorianNotationElement]) {
        self.elements = elements
    }

    public var neumes: [GregorianNeume] {
        elements.compactMap { element in
            guard case let .neume(neume) = element else { return nil }
            return neume
        }
    }

    public var eventIDs: [String] {
        neumes.flatMap(\.eventIDs)
    }

    public var lyricText: String {
        var result = ""
        var separatesFollowingLyric = false

        func append(_ text: String, startsWord: Bool, isLyricMark: Bool) {
            guard !text.isEmpty else { return }
            if !result.isEmpty,
               (startsWord || separatesFollowingLyric),
               result.last?.isWhitespace != true {
                result.append(" ")
            }
            result.append(text)
            separatesFollowingLyric = isLyricMark
        }

        for element in elements {
            switch element {
            case .neume(let neume):
                append(
                    neume.lyric,
                    startsWord: neume.startsWord,
                    isLyricMark: false
                )
            case .lyricMark(let mark):
                append(
                    mark.text,
                    startsWord: mark.startsWord,
                    isLyricMark: true
                )
            case .clef, .accidental, .division, .forcedBreak:
                continue
            }
        }

        return result.replacingOccurrences(
            of: #"\s+([,.;:!?])"#,
            with: "$1",
            options: .regularExpression
        )
    }
}

public enum GregorianScoreParserError: Error, Equatable, LocalizedError, Sendable {
    case unterminatedNotation(offset: Int)
    case invalidClef(String, offset: Int)
    case unsupportedToken(String, offset: Int)
    case tooManyNotationNotes(offset: Int)
    case timelineMismatch(expected: Int, parsed: Int)
    case eventSyllableMismatch(eventID: String)

    public var errorDescription: String? {
        switch self {
        case let .unterminatedNotation(offset):
            "The GABC notation beginning at offset \(offset) is not closed."
        case let .invalidClef(token, offset):
            "Invalid clef \(token) at offset \(offset)."
        case let .unsupportedToken(token, offset):
            "Unsupported GABC token \(token) at offset \(offset)."
        case let .tooManyNotationNotes(offset):
            "The notation contains more notes than its compiled timeline near offset \(offset)."
        case let .timelineMismatch(expected, parsed):
            "The compiled timeline contains \(expected) notes but the notation contains \(parsed)."
        case let .eventSyllableMismatch(eventID):
            "Timeline event \(eventID) is assigned to a different syllable than its neume."
        }
    }
}

public enum GregorianScoreParser {
    /// Parses a GABC score, numbering its written notes as events in the
    /// order written.
    ///
    /// The notes are named `note-0`, `note-1` and so on, and the syllables
    /// `syllable-0`, `syllable-1`: one for each notation group that writes a
    /// note, whether or not the group carries lyric text. These are the names
    /// a content compiler gives the events of a compiled timeline. A score
    /// identifier prefixes both (`kyrie-note-0`), which keeps the names of
    /// several scores apart.
    ///
    /// The notation follows the `%%` that ends a GABC file's header; text
    /// without one is read as notation alone.
    public static func parse(gabc: String, scoreID: String? = nil) throws -> GregorianScore {
        try parse(gabc: gabc, events: .numbered(scoreID: scoreID))
    }

    /// Parses a GABC score whose notes a compiled timeline names, one event
    /// for each written note in order. The parse fails if the notation
    /// writes more or fewer notes than the timeline holds, or if a group
    /// that carries lyric text spans more than one of its syllables.
    public static func parse<Timeline: GregorianTimeline>(
        gabc: String,
        timeline: Timeline
    ) throws -> GregorianScore {
        try parse(gabc: gabc, events: .compiled(timeline.events.map { ResolvedEvent($0) }))
    }

    private struct ResolvedEvent {
        let id: String
        let syllableID: String
        let relativePitch: Int

        init(id: String, syllableID: String, relativePitch: Int) {
            self.id = id
            self.syllableID = syllableID
            self.relativePitch = relativePitch
        }

        init(_ event: some GregorianTimelineEvent) {
            self.init(
                id: event.id,
                syllableID: event.syllableID,
                relativePitch: event.relativePitch
            )
        }
    }

    private enum EventSource {
        case compiled([ResolvedEvent])
        case numbered(scoreID: String?)
    }

    private static func parse(gabc: String, events source: EventSource) throws -> GregorianScore {
        let bodyStart = gabc.range(of: "%%")?.upperBound ?? gabc.startIndex
        let body = String(gabc[bodyStart...])
            // In GABC, a single percent sign begins a comment through the end
            // of its line. Preserve the newline so surrounding lyric fragments
            // cannot be joined accidentally.
            .replacingOccurrences(
                of: #"%[^\r\n]*"#,
                with: "",
                options: .regularExpression
            )
        let compiledEvents: [ResolvedEvent]?
        let scoreID: String?
        switch source {
        case let .compiled(events):
            compiledEvents = events
            scoreID = nil
        case let .numbered(numberedScoreID):
            compiledEvents = nil
            scoreID = numberedScoreID
        }
        let eventPrefix = scoreID.map { "\($0)-" } ?? ""
        // Each timeline event's syllable, to check a lyric's notes against.
        let syllableIDs = compiledEvents.map { events in
            Dictionary(events.map { ($0.id, $0.syllableID) }, uniquingKeysWith: { first, _ in first })
        }
        var elements: [GregorianNotationElement] = []
        var cursor = body.startIndex
        // The cursor's offset in characters, kept as it advances.
        var cursorOffset = 0
        var eventIndex = 0
        // Numbered events give each group that writes a note a syllable.
        var syllableIndex = 0
        // A word begins where a space stands before a group, and a clef or a
        // bar that stands there alone does not use the beginning up: in
        // "mus.(h.) (:)De(g)us" the bar ends one word and "De" begins the
        // next, though no space follows the bar. Notes without words are the
        // melisma of the syllable before them, and leave it a word unfinished.
        var wordStartIsPending = false

        while let open = body[cursor...].firstIndex(of: "(") {
            let lyricSource = withoutTags(String(body[cursor..<open]))
            let openOffset = cursorOffset + body.distance(from: cursor, to: open)
            guard let close = body[open...].firstIndex(of: ")") else {
                throw GregorianScoreParserError.unterminatedNotation(offset: openOffset)
            }

            let notationStart = body.index(after: open)
            let notation = String(body[notationStart..<close])
            let groupOffset = openOffset + 1
            let parsedLyricStyle = lyricStyle(lyricSource)
            let sourceStartsWord = lyricStartsWord(lyricSource, isFirst: elements.isEmpty)

            let parsed = try parseNotationGroup(notation, baseOffset: groupOffset)
            let hasNotes = parsed.contains { token in
                if case .notes = token { return true }
                return false
            }
            // The rubrics written with a sung syllable stand apart from it:
            // a sign or a verse number before it, a flex after it.
            let cleaned = cleanLyric(lyricSource)
            let sung = hasNotes ? sungLyric(cleaned) : SungLyric(cleaned)
            let lyric = sung.text
            let startsWord = sourceStartsWord
                || !sung.leading.isEmpty
                || (wordStartIsPending && !lyric.isEmpty)
            let leadingStartsWord = sourceStartsWord || wordStartIsPending
            if hasNotes {
                // A rubric written after the syllable ends its word.
                wordStartIsPending = !sung.trailing.isEmpty
            } else if !lyric.isEmpty {
                // A rubric that stands alone ends the word before it, and
                // begins none: `ne,(f.) R/.(::)In(f)` begins a word at "In".
                wordStartIsPending = true
            } else if sourceStartsWord, !parsed.isEmpty {
                wordStartIsPending = true
            }
            for (index, mark) in sung.leading.enumerated() {
                elements.append(
                    .lyricMark(
                        GregorianLyricMark(
                            text: standaloneLyricText(mark),
                            style: .rubric,
                            startsWord: index == 0 ? leadingStartsWord : true,
                            sourceRange: GregorianSourceRange(
                                lowerBound: cursorOffset + index,
                                upperBound: cursorOffset + index + 1
                            ),
                            scoreID: scoreID,
                            beginsWhatFollows: true
                        )
                    )
                )
            }
            // A lyric written with a bar or a clef and no notes is a rubric.
            // One that heads what follows stands after the bar, at the head
            // of its words: `me(g.)um.(f.) R/.(::)In(f)` reads `um. ‖ ℟. In`.
            var standaloneMark: GregorianLyricMark?
            if !lyric.isEmpty, !hasNotes {
                let displayLyric = standaloneLyricText(lyric)
                standaloneMark = GregorianLyricMark(
                    text: displayLyric,
                    style: standaloneLyricStyle(
                        displayLyric,
                        parsedStyle: parsedLyricStyle
                    ),
                    startsWord: startsWord,
                    sourceRange: GregorianSourceRange(
                        lowerBound: cursorOffset,
                        upperBound: openOffset
                    ),
                    scoreID: scoreID,
                    beginsWhatFollows: headsWhatFollows(displayLyric)
                )
            }
            if let standaloneMark, !standaloneMark.beginsWhatFollows {
                elements.append(.lyricMark(standaloneMark))
            }
            var appliedLyric = false

            for token in parsed {
                switch token {
                case let .clef(clef):
                    elements.append(.clef(clef))
                case let .accidental(accidental):
                    elements.append(.accidental(accidental))
                case let .division(division):
                    elements.append(.division(division))
                case .forcedBreak:
                    elements.append(.forcedBreak)
                case let .notes(notes, spaceBefore):
                    var mappedNotes: [GregorianNote] = []
                    var mappedEvents: [ResolvedEvent] = []
                    for note in notes {
                        let event: ResolvedEvent
                        if let compiledEvents {
                            guard compiledEvents.indices.contains(eventIndex) else {
                                throw GregorianScoreParserError.tooManyNotationNotes(
                                    offset: note.sourceRange.lowerBound
                                )
                            }
                            event = compiledEvents[eventIndex]
                        } else {
                            event = ResolvedEvent(
                                id: "\(eventPrefix)note-\(eventIndex)",
                                syllableID: "\(eventPrefix)syllable-\(syllableIndex)",
                                // Pitch letters run from `a` (0); `h` is 7.
                                relativePitch: note.pitch - 7
                            )
                        }
                        mappedEvents.append(event)
                        mappedNotes.append(
                            GregorianNote(
                                id: event.id,
                                pitch: note.pitch,
                                relativePitch: event.relativePitch,
                                shape: note.shape,
                                accidental: note.accidental,
                                hasMora: note.hasMora,
                                hasEpisema: note.hasEpisema,
                                moraCount: note.moraCount,
                                moraPosition: note.moraPosition,
                                episemaPosition: note.episemaPosition,
                                liquescence: note.liquescence,
                                isCavum: note.isCavum,
                                hasIctus: note.hasIctus,
                                ictusPosition: note.ictusPosition,
                                repetitionCount: note.repetitionCount,
                                sourceRange: note.sourceRange
                            )
                        )
                        eventIndex += 1
                    }

                    guard let first = mappedNotes.first, let event = mappedEvents.first else { continue }
                    let neumeLyric = appliedLyric ? "" : lyric
                    if !neumeLyric.isEmpty,
                       let syllableIDs,
                       mappedNotes.contains(where: { syllableIDs[$0.id] != event.syllableID }) {
                        throw GregorianScoreParserError.eventSyllableMismatch(eventID: first.id)
                    }
                    let segments = GregorianNeumeSegmenter.segment(mappedNotes)
                    for (segmentIndex, segment) in segments.enumerated() {
                        guard let segmentFirst = segment.first else { continue }
                        // A punctum that begins a pressus abuts the neume it
                        // repeats the first note of (`hhg`, `ffg`).
                        let joinsPressus = segmentIndex > 0 && segment.count > 1
                            && segments[segmentIndex - 1].count == 1
                            && segments[segmentIndex - 1].last.map { previous in
                                previous.pitch == segmentFirst.pitch && previous.shape == .punctum
                                    && previous.liquescence == .none && previous.moraCount == 0
                            } == true
                        elements.append(
                            .neume(
                                GregorianNeume(
                                    id: segmentFirst.id,
                                    syllableID: event.syllableID,
                                    lyric: segmentIndex == 0 ? neumeLyric : "",
                                    lyricStyle: segmentIndex == 0 && !neumeLyric.isEmpty
                                        ? parsedLyricStyle
                                        : .regular,
                                    startsWord: segmentIndex == 0 && !appliedLyric ? startsWord : false,
                                    notes: segment,
                                    sourceRange: GregorianSourceRange(
                                        lowerBound: segment.first?.sourceRange.lowerBound ?? groupOffset,
                                        upperBound: segment.last?.sourceRange.upperBound ?? groupOffset
                                    ),
                                    centeredRange: segmentIndex == 0 && !neumeLyric.isEmpty
                                        ? sung.centeredRange
                                        : nil,
                                    spaceBefore: segmentIndex == 0
                                        ? spaceBefore
                                        : (joinsPressus ? .joined : .automatic)
                                )
                            )
                        )
                    }
                    appliedLyric = true
                }
            }
            if let standaloneMark, standaloneMark.beginsWhatFollows {
                elements.append(.lyricMark(standaloneMark))
            }
            for (index, mark) in sung.trailing.enumerated() {
                let lowerBound = openOffset - sung.trailing.count + index
                elements.append(
                    .lyricMark(
                        GregorianLyricMark(
                            text: standaloneLyricText(mark),
                            style: .rubric,
                            startsWord: true,
                            sourceRange: GregorianSourceRange(
                                lowerBound: lowerBound,
                                upperBound: lowerBound + 1
                            ),
                            scoreID: scoreID
                        )
                    )
                )
            }
            if hasNotes {
                syllableIndex += 1
            }

            cursor = body.index(after: close)
            cursorOffset = groupOffset + body.distance(from: notationStart, to: close) + 1
        }

        if let compiledEvents, eventIndex != compiledEvents.count {
            throw GregorianScoreParserError.timelineMismatch(
                expected: compiledEvents.count,
                parsed: eventIndex
            )
        }
        return GregorianScore(elements: elements)
    }

    private enum ParsedToken {
        case clef(GregorianClef)
        case accidental(GregorianAccidentalMark)
        case notes([ParsedNote], spaceBefore: GregorianNeumeSpace)
        case division(GregorianDivision)
        case forcedBreak
    }

    private struct ParsedNote {
        let pitch: Int
        let shape: GregorianNoteShape
        let accidental: GregorianAccidental?
        let hasMora: Bool
        var hasEpisema: Bool
        let moraCount: Int
        let moraPosition: GregorianEpisemaPosition?
        var episemaPosition: GregorianEpisemaPosition?
        /// The notes its episema covers, counting back from this one: as
        /// many as the GABC writes underscores (`kj__`).
        let episemaReach: Int
        let liquescence: GregorianLiquescence
        let isCavum: Bool
        let hasIctus: Bool
        let ictusPosition: GregorianEpisemaPosition?
        let repetitionCount: Int
        let sourceRange: GregorianSourceRange
    }

    private static func parseNotationGroup(
        _ notation: String,
        baseOffset: Int
    ) throws -> [ParsedToken] {
        let characters = Array(notation)
        var result: [ParsedToken] = []
        var currentNotes: [ParsedNote] = []
        var index = 0
        // The separator written since the last run of notes, which the next
        // run takes as its space before: the first, as Exsurge reads it,
        // with `//` counted as one.
        var spaceBefore = GregorianNeumeSpace.automatic

        func flushNotes() {
            if !currentNotes.isEmpty {
                result.append(.notes(currentNotes, spaceBefore: spaceBefore))
                currentNotes.removeAll(keepingCapacity: true)
                spaceBefore = .automatic
            }
        }

        func separate(_ space: GregorianNeumeSpace) {
            switch (spaceBefore, space) {
            case (.automatic, _):
                spaceBefore = space
            case let (.slashes(count), .slashes(more)):
                spaceBefore = .slashes(count + more)
            default:
                break
            }
        }

        // A `[…]` annotation is skipped whole, to its closing bracket or the
        // end of the group.
        func indexAfterBracket(at index: Int) -> Int {
            characters[(index + 1)...].firstIndex(of: "]").map { $0 + 1 } ?? characters.count
        }

        func isFlatCClef(at index: Int) -> Bool {
            characters[index] == "c"
                && index + 2 < characters.count
                && characters[index + 1] == "b"
                && characters[index + 2].isNumber
        }

        func isClef(at index: Int) -> Bool {
            (characters[index] == "c" || characters[index] == "f")
                && index + 1 < characters.count
                && (characters[index + 1].isNumber || isFlatCClef(at: index))
        }

        while index < characters.count {
            let character = characters[index]

            if character == "[" {
                flushNotes()
                index = indexAfterBracket(at: index)
                continue
            }

            if character.isWhitespace || character == "/" || character == "!" {
                flushNotes()
                separate(character == "/" ? .slashes(1) : character == "!" ? .joined : .space)
                index += 1
                continue
            }

            if isClef(at: index) {
                let flattensB = isFlatCClef(at: index)
                flushNotes()
                let lineIndex = index + (flattensB ? 2 : 1)
                let token = String(characters[index...lineIndex])
                guard let line = Int(String(characters[lineIndex])), (1...4).contains(line) else {
                    throw GregorianScoreParserError.invalidClef(
                        token,
                        offset: baseOffset + index
                    )
                }
                result.append(
                    .clef(
                        GregorianClef(
                            kind: character == "c" ? .c : .f,
                            line: line,
                            flattensB: flattensB
                        )
                    )
                )
                index = lineIndex + 1
                continue
            }

            if character == ":" || character == ";" || character == "," || character == "`" {
                flushNotes()
                if character == ":", index + 1 < characters.count, characters[index + 1] == ":" {
                    result.append(.division(.final))
                    index += 2
                } else {
                    let division: GregorianDivision = switch character {
                    case ",", "`": .minima
                    case ";": .minor
                    default: .major
                    }
                    result.append(.division(division))
                    index += 1
                }
                continue
            }

            if character == "z" || character == "Z" {
                flushNotes()
                // `z0` writes a custos for the next note, as before a change
                // of clef, and breaks no line: a staff's end has its own.
                if character == "z", index + 1 < characters.count, characters[index + 1] == "0" {
                    index += 2
                    continue
                }
                result.append(.forcedBreak)
                index += 1
                continue
            }

            guard let pitch = pitchValue(character) else {
                if isIgnorableControl(character) {
                    index += 1
                    continue
                }
                throw GregorianScoreParserError.unsupportedToken(
                    String(character),
                    offset: baseOffset + index
                )
            }

            let noteStart = index
            if index + 1 < characters.count {
                let accidentalCharacter = characters[index + 1]
                let accidental: GregorianAccidental? = switch accidentalCharacter {
                case "x": .flat
                case "y": .natural
                case "#": .sharp
                default: nil
                }
                if let accidental {
                    flushNotes()
                    result.append(
                        .accidental(
                            GregorianAccidentalMark(
                                kind: accidental,
                                pitch: pitch,
                                sourceRange: GregorianSourceRange(
                                    lowerBound: baseOffset + noteStart,
                                    upperBound: baseOffset + noteStart + 2
                                )
                            )
                        )
                    )
                    index += 2
                    continue
                }
            }
            index += 1
            var suffix = ""
            while index < characters.count {
                let next = characters[index]
                if next == "[" {
                    index = indexAfterBracket(at: index)
                    continue
                }
                if next.isWhitespace || next == "/" || next == ":" || next == ";" || next == ","
                    || next == "`" || next == "z" || next == "Z"
                    || pitchValue(next) != nil
                    || isClef(at: index) {
                    break
                }
                suffix.append(next)
                index += 1
            }

            let liquescence: GregorianLiquescence
            if suffix.contains("<") {
                liquescence = .ascending
            } else if suffix.contains(">") {
                liquescence = .descending
            } else if suffix.contains("~") {
                liquescence = .small
            } else {
                liquescence = .none
            }

            // A pitch letter whose signs end in `+` is a custos, as in
            // Exsurge: it stands apart from the notes on either side.
            let isCustos = suffix.hasSuffix("+")

            let shape: GregorianNoteShape
            if isCustos {
                shape = .custos
            } else if suffix.contains("w") || suffix.contains("W") {
                shape = .quilisma
            } else if character.isUppercase {
                // A capital is an inclinatum, liquescent or not: `F~` is the
                // diminished inclinatum that ends an ancus.
                shape = .inclinatum
            } else if suffix.contains("<") || suffix.contains(">") || suffix.contains("~") {
                shape = .liquescent
            } else if suffix.contains("v") || suffix.contains("V") {
                shape = .virga
            } else if suffix.contains("o") || suffix.contains("O") {
                shape = .oriscus
            } else if suffix.contains("s") || suffix.contains("S") {
                shape = .stropha
            } else {
                shape = .punctum
            }

            let accidental: GregorianAccidental?
            if suffix.contains("x") {
                accidental = .flat
            } else if suffix.contains("y") {
                accidental = .natural
            } else if suffix.contains("#") {
                accidental = .sharp
            } else {
                accidental = nil
            }

            let unsupported = suffix.filter { !isSupportedSuffix($0) }
            if let token = unsupported.first {
                throw GregorianScoreParserError.unsupportedToken(
                    String(token),
                    offset: baseOffset + noteStart + 1
                )
            }

            if isCustos {
                flushNotes()
            }
            currentNotes.append(
                ParsedNote(
                    pitch: pitch,
                    shape: shape,
                    accidental: accidental,
                    hasMora: suffix.contains("."),
                    hasEpisema: suffix.contains("_"),
                    moraCount: suffix.filter { $0 == "." }.count,
                    moraPosition: signPosition(".", in: suffix),
                    episemaPosition: signPosition("_", in: suffix),
                    episemaReach: suffix.filter { $0 == "_" }.count,
                    liquescence: liquescence,
                    // r0 is a hollow note within lines; r1 to r5 are signs
                    // written above the note (an accent, a circle), not a
                    // hollow note.
                    isCavum: isCavum(suffix),
                    hasIctus: suffix.contains("'"),
                    ictusPosition: signPosition("'", in: suffix),
                    repetitionCount: isCustos ? 1 : repeatedNoteCount(suffix),
                    sourceRange: GregorianSourceRange(
                        lowerBound: baseOffset + noteStart,
                        upperBound: baseOffset + index
                    )
                )
            )
            if isCustos || suffix.contains("!") {
                flushNotes()
                if suffix.contains("!") { separate(.joined) }
            }
        }

        flushNotes()

        // An episema written with more than one underscore (`kj__`) reaches
        // back over as many notes of the group.
        let places = result.indices.flatMap { tokenIndex -> [(token: Int, note: Int)] in
            guard case let .notes(notes, _) = result[tokenIndex] else { return [] }
            return notes.indices.map { (tokenIndex, $0) }
        }
        for (order, place) in places.enumerated() {
            guard case let .notes(notes, _) = result[place.token] else { continue }
            let note = notes[place.note]
            for back in stride(from: 1, to: note.episemaReach, by: 1) where order >= back {
                let earlier = places[order - back]
                guard case .notes(var earlierNotes, let space) = result[earlier.token] else { continue }
                earlierNotes[earlier.note].hasEpisema = true
                if earlierNotes[earlier.note].episemaPosition == nil {
                    earlierNotes[earlier.note].episemaPosition = note.episemaPosition
                }
                result[earlier.token] = .notes(earlierNotes, spaceBefore: space)
            }
        }
        return result
    }

    /// The side a sign's digit sets it on: `1` above, `0` below.
    private static func signPosition(_ sign: Character, in suffix: String) -> GregorianEpisemaPosition? {
        let characters = Array(suffix)
        for index in characters.indices.dropLast() where characters[index] == sign {
            if characters[index + 1] == "1" { return .above }
            if characters[index + 1] == "0" { return .below }
        }
        return nil
    }

    /// GABC's quick virga and stropha: each repeated sign is another note
    /// on the same pitch (`hvv` is two virgae, `hsss` three strophae).
    private static func repeatedNoteCount(_ suffix: String) -> Int {
        var count = 1
        var shape: Character?
        for sign in suffix where sign == "s" || sign == "v" {
            if shape == sign { count += 1 }
            shape = sign
        }
        return count
    }

    private static func isCavum(_ suffix: String) -> Bool {
        let characters = Array(suffix)
        return characters.indices.contains { index in
            guard characters[index] == "r" || characters[index] == "R" else { return false }
            guard index + 1 < characters.count, let sign = characters[index + 1].wholeNumberValue else {
                return true
            }
            return sign == 0
        }
    }

    private static func pitchValue(_ character: Character) -> Int? {
        let scalars = String(character).lowercased().unicodeScalars
        guard scalars.count == 1, let scalar = scalars.first, ("a"..."m").contains(scalar) else {
            return nil
        }
        return Int(scalar.value - ("a" as Unicode.Scalar).value)
    }

    private static func isSupportedSuffix(_ character: Character) -> Bool {
        character.isNumber
            || [".", "_", "w", "W", "<", ">", "~", "x", "y", "#", "v", "V", "o", "O", "q", "Q", "s", "S", "r", "R", "'", "!", "@", "-", "+", "*", "†", "("]
                .contains(character)
    }

    private static func isIgnorableControl(_ character: Character) -> Bool {
        character.isNumber
            || ["'", "!", "@", "]", "-", "+", "_", "*", "†", "(", "."].contains(character)
    }

    // Lyric markup: tags, and the psalm-tone pointing of preparatory and
    // accented syllables.
    private static let tagPattern = #"<[^>]+>"#
    private static let preparatoryPattern = #"_([^_]+)_"#
    private static let accentedPattern = #"\*([^*]+)\*"#

    private static func withoutTags(_ source: String) -> String {
        source.replacingOccurrences(of: tagPattern, with: "", options: .regularExpression)
    }

    /// A group's lyric, and the letters a brace pair in it marks to be
    /// centred, as character offsets into the lyric.
    private struct CleanLyric {
        let text: String
        let centeredRange: Range<Int>?
    }

    /// A sung syllable's lyric, and the rubrics written before and after it.
    private struct SungLyric {
        let leading: [String]
        let text: String
        let centeredRange: Range<Int>?
        let trailing: [String]

        init(leading: [String] = [], text: String, centeredRange: Range<Int>?, trailing: [String] = []) {
            self.leading = leading
            self.text = text
            self.centeredRange = centeredRange
            self.trailing = trailing
        }

        init(_ lyric: CleanLyric) {
            self.init(text: lyric.text, centeredRange: lyric.centeredRange)
        }
    }

    /// A group's lyric as sung, from its source without tags.
    private static func cleanLyric(_ source: String) -> CleanLyric {
        let text = source
            // Psalm-tone sources use paired underscores for preparatory
            // syllables and paired asterisks for accented syllables. These
            // delimiters describe pointing/style and are not lyric text.
            // The paired patterns intentionally leave a standalone
            // liturgical asterisk unchanged.
            .replacingOccurrences(of: preparatoryPattern, with: "$1", options: .regularExpression)
            .replacingOccurrences(of: accentedPattern, with: "$1", options: .regularExpression)
            // A tilde is a space that no line break may take.
            .replacingOccurrences(of: "~", with: " ")
            .replacingOccurrences(of: "\n", with: " ")
            .split(whereSeparator: \.isWhitespace)
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        // Braces name the letters to centre under the note, in place of the
        // sung vowel: `{ia}.`, `p{a}u`.
        var characters: [Character] = []
        var braceStart: Int?
        var braceEnd: Int?
        for character in text {
            switch character {
            case "{":
                if braceStart == nil { braceStart = characters.count }
            case "}":
                if braceStart != nil, braceEnd == nil { braceEnd = characters.count }
            default:
                characters.append(character)
            }
        }
        let centeredRange = braceStart.flatMap { start in
            braceEnd.flatMap { end in end > start ? start..<end : nil }
        }
        return CleanLyric(text: String(characters), centeredRange: centeredRange)
    }

    /// A sung syllable's lyric, without the rubrics written before or after
    /// its words: `V/. Dó`, `2. Pro`, `* ab`, `hæc, †`. A lyric made of
    /// rubrics alone is left whole.
    private static func sungLyric(_ lyric: CleanLyric) -> SungLyric {
        let words = lyric.text.split(separator: " ").map(String.init)
        var first = 0
        var last = words.count
        while first < last, isRubricMark(words[first]) { first += 1 }
        while last > first, isRubricMark(words[last - 1]) { last -= 1 }
        guard first < last, first > 0 || last < words.count else { return SungLyric(lyric) }
        let text = words[first..<last].joined(separator: " ")
        let dropped = words[..<first].reduce(0) { $0 + $1.count + 1 }
        let centeredRange = lyric.centeredRange.flatMap { range -> Range<Int>? in
            let lower = range.lowerBound - dropped
            let upper = range.upperBound - dropped
            return lower >= 0 && upper <= text.count ? lower..<upper : nil
        }
        return SungLyric(
            leading: Array(words[..<first]),
            text: text,
            centeredRange: centeredRange,
            trailing: Array(words[last...])
        )
    }

    private static let rubricMarks: Set<String> = [
        "*", "**", "†", "‡", "+", "✠", "℣.", "℟.", "V/.", "R/.", "ij.", "iij.", "ij", "iij"
    ]

    /// Whether a word of a lyric is a rubric: a sign, a repeat mark, or a
    /// verse number.
    private static func isRubricMark(_ word: String) -> Bool {
        rubricMarks.contains(word) || isVerseNumber(word)
    }

    private static func isVerseNumber(_ word: String) -> Bool {
        word.range(of: #"^[0-9]+\.?$"#, options: .regularExpression) != nil
    }

    /// Whether a rubric written alone heads the words after it: ℣., ℟., the
    /// cross signed at a canticle's first word, or a verse number. The
    /// others close the words before them.
    private static func headsWhatFollows(_ text: String) -> Bool {
        text == "℣." || text == "℟." || text == "✠" || isVerseNumber(text)
    }

    /// A group's lyric style, from its source without tags.
    private static func lyricStyle(_ source: String) -> GregorianLyricStyle {
        if source.range(of: accentedPattern, options: .regularExpression) != nil {
            return .accented
        }
        if source.range(of: preparatoryPattern, options: .regularExpression) != nil {
            return .preparatory
        }
        return .regular
    }

    private static func standaloneLyricText(_ lyric: String) -> String {
        switch lyric {
        case "V/.":
            "℣."
        case "R/.":
            "℟."
        default:
            lyric
        }
    }

    private static func standaloneLyricStyle(
        _ lyric: String,
        parsedStyle: GregorianLyricStyle
    ) -> GregorianLyricStyle {
        guard parsedStyle == .regular else { return parsedStyle }
        return isRubricMark(lyric) ? .rubric : .regular
    }

    /// Whether a group's lyric, from its source without tags, begins a word.
    private static func lyricStartsWord(_ source: String, isFirst: Bool) -> Bool {
        isFirst || source.first?.isWhitespace == true
    }
}

public enum GregorianNeumeClassifier {
    public static func classify(_ notes: [GregorianNote]) -> GregorianNeumeForm {
        guard let first = notes.first else { return .punctum }
        let pitches = notes.map(\.pitch)
        let directions = zip(pitches, pitches.dropFirst()).map { direction($0, $1) }
        let allEqual = Set(pitches).count == 1
        let allInclinata = notes.allSatisfy { $0.shape == .inclinatum }
        let allVirga = notes.allSatisfy { $0.shape == .virga }
        // A note written twice or thrice (`hvv`, `hsss`) counts as so many.
        let writtenCount = notes.reduce(0) { $0 + $1.repetitionCount }

        if allInclinata { return .punctaInclinata }
        if writtenCount == 1 {
            return switch first.shape {
            case .virga: .virga
            case .stropha: .apostropha
            case .oriscus: .oriscus
            default: .punctum
            }
        }
        if allEqual, allVirga {
            return writtenCount >= 3 ? .trivirga : .bivirga
        }
        if allEqual {
            return writtenCount >= 3 ? .tristropha : .distropha
        }
        if notes.count == 2 {
            if directions[0] > 0 {
                return notes.first?.shape == .oriscus ? .pesQuassus : .podatus
            }
            return directions[0] < 0 ? .clivis : .punctum
        }

        // A pes and the diamonds that fall from it.
        if directions.first == 1, notes.count >= 3,
           notes.dropFirst(2).allSatisfy({ $0.shape == .inclinatum }),
           directions.dropFirst().allSatisfy({ $0 < 0 }) {
            return .pesSubpunctis
        }

        let firstTwo = Array(directions.prefix(2))
        if firstTwo == [1, 1] {
            if notes.dropFirst().contains(where: { $0.shape == .oriscus }) {
                return directions.dropFirst(2).contains(-1) ? .salicusFlexus : .salicus
            }
            return directions.dropFirst(2).contains(-1) ? .scandicusFlexus : .scandicus
        }
        if firstTwo == [-1, -1] {
            return (notes.last?.liquescence ?? .none) != .none ? .ancus : .climacus
        }
        if firstTwo == [1, -1] {
            return directions.dropFirst(2).starts(with: [1, -1])
                ? .torculusResupinusFlexus
                : (directions.dropFirst(2).first == 1 ? .torculusResupinus : .torculus)
        }
        if firstTwo == [-1, 1] {
            return directions.dropFirst(2).first == -1 ? .porrectusFlexus : .porrectus
        }
        return .punctum
    }
}

/// Whether a melody rises (1), falls (-1) or holds (0) from one pitch to the next.
private func direction(_ from: Int, _ to: Int) -> Int {
    (to - from).signum()
}

/// Splits a continuous GABC note run into the named forms accepted by the
/// native compositor. This is the native counterpart of Exsurge's
/// `createNeumesFromNotes` state machine.
public enum GregorianNeumeSegmenter {
    public static func segment(_ notes: [GregorianNote]) -> [[GregorianNote]] {
        // A note written twice or thrice (`hvv`, `hsss`) is a bivirga or a
        // tristropha in itself, and a custos belongs to no neume. Each stands
        // alone, and the notes on either side group as they would without it.
        guard notes.contains(where: \.standsAlone) else { return segmentRun(notes) }
        var result: [[GregorianNote]] = []
        var run: [GregorianNote] = []
        for note in notes {
            if note.standsAlone {
                result.append(contentsOf: segmentRun(run))
                run.removeAll(keepingCapacity: true)
                result.append([note])
            } else {
                run.append(note)
            }
        }
        result.append(contentsOf: segmentRun(run))
        return result
    }

    private static func segmentRun(_ notes: [GregorianNote]) -> [[GregorianNote]] {
        guard notes.count > 1 else { return notes.isEmpty ? [] : [notes] }
        var result: [[GregorianNote]] = []
        var index = 0

        while index < notes.count {
            let remaining = notes.count - index
            guard remaining > 1 else {
                result.append([notes[index]])
                break
            }

            let first = notes[index]
            let second = notes[index + 1]
            let firstDirection = direction(first.pitch, second.pitch)

            if first.shape == .virga || first.shape == .stropha {
                let count = repeatedShapeCount(
                    notes,
                    from: index,
                    shape: first.shape,
                    pitch: first.pitch,
                    maximum: 3
                )
                result.append(Array(notes[index..<(index + count)]))
                index += count
                continue
            }
            if first.shape == .inclinatum {
                var end = index + 1
                while end < notes.count, notes[end].shape == .inclinatum { end += 1 }
                result.append(Array(notes[index..<end]))
                index = end
                continue
            }

            var length = 1
            if firstDirection > 0 {
                if remaining >= 3, notes[index + 2].shape == .inclinatum,
                   direction(second.pitch, notes[index + 2].pitch) < 0 {
                    length = 3
                    while index + length < notes.count,
                          notes[index + length].shape == .inclinatum,
                          direction(notes[index + length - 1].pitch, notes[index + length].pitch) < 0 {
                        length += 1
                    }
                } else if remaining >= 3 {
                    let secondDirection = direction(second.pitch, notes[index + 2].pitch)
                    if secondDirection > 0 {
                        length = 3
                        if remaining >= 4,
                           direction(notes[index + 2].pitch, notes[index + 3].pitch) < 0 {
                            length = 4
                        }
                    } else if secondDirection < 0 {
                        length = 3
                        if remaining >= 4,
                           direction(notes[index + 2].pitch, notes[index + 3].pitch) > 0 {
                            length = 4
                            if remaining >= 5,
                               direction(notes[index + 3].pitch, notes[index + 4].pitch) < 0 {
                                length = 5
                            }
                        }
                    } else {
                        length = 2
                    }
                } else {
                    length = 2
                }
            } else if firstDirection < 0 {
                if second.shape == .inclinatum {
                    length = 2
                    while index + length < notes.count,
                          notes[index + length].shape == .inclinatum,
                          direction(notes[index + length - 1].pitch, notes[index + length].pitch) < 0 {
                        length += 1
                    }
                } else if remaining >= 3,
                          direction(second.pitch, notes[index + 2].pitch) < 0,
                          notes[index + 2].liquescence != .none {
                    length = 3
                } else if remaining >= 3,
                          direction(second.pitch, notes[index + 2].pitch) > 0 {
                    length = 3
                    if remaining >= 4,
                       direction(notes[index + 2].pitch, notes[index + 3].pitch) < 0 {
                        length = 4
                    }
                } else {
                    length = 2
                }
            } else {
                length = unisonLength(notes, from: index)
            }

            result.append(Array(notes[index..<(index + length)]))
            index += length
        }
        return result
    }

    private static func repeatedShapeCount(
        _ notes: [GregorianNote],
        from index: Int,
        shape: GregorianNoteShape,
        pitch: Int,
        maximum: Int
    ) -> Int {
        var count = 0
        while index + count < notes.count, count < maximum,
              notes[index + count].shape == shape,
              notes[index + count].pitch == pitch {
            count += 1
        }
        return max(1, count)
    }

    /// How many plain notes repeated on one pitch make a neume, as Exsurge
    /// groups them: two a distropha and three a tristropha only where the
    /// run ends with them. Where the melody goes on, the last of them begins
    /// the next neume, a pressus (`ffg` is a punctum and a pes), and a dotted
    /// or diminished note stands alone.
    private static func unisonLength(_ notes: [GregorianNote], from index: Int) -> Int {
        let first = notes[index]
        guard index + 1 < notes.count, notes[index + 1].pitch == first.pitch,
              isPlain(notes[index + 1]), first.moraCount == 0, first.liquescence != .small else {
            return 1
        }
        let second = notes[index + 1]
        guard index + 2 < notes.count else { return 2 }
        guard notes[index + 2].pitch == first.pitch else { return 1 }
        if second.moraCount > 0 { return 2 }
        return index + 3 < notes.count ? 2 : 3
    }

    /// A note of no shape of its own: a punctum, liquescent or not.
    private static func isPlain(_ note: GregorianNote) -> Bool {
        note.shape == .punctum || note.shape == .liquescent
    }
}
