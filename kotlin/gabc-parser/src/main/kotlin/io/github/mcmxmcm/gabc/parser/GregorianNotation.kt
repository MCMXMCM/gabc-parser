package io.github.mcmxmcm.gabc.parser

import java.text.BreakIterator

// Gregorian notation as the engraver knows it: clefs, accidentals, divisions, lyric marks and the notes of each
// syllable grouped into the named neumes of Exsurge's compositor; and the parser that reads it from GABC. A port
// of the Swift package's GregorianNotation.swift.

public data class GregorianSourceRange(val lowerBound: Int, val upperBound: Int)

public enum class GregorianClefKind { C, F }

public data class GregorianClef(val kind: GregorianClefKind, val line: Int, val flattensB: Boolean = false)

public enum class GregorianAccidental { Flat, Natural, Sharp }

public enum class GregorianDivision { Minima, Minor, Major, Final }

public enum class GregorianNoteShape {
    Punctum,
    Virga,
    Quilisma,
    Liquescent,
    Inclinatum,
    Oriscus,
    Stropha,

    /**
     * A custos written in the score itself (`f+`): a guide to the pitch that follows, usually before a change of
     * clef. The compiled timeline holds an event for it, as for every pitch letter, but it is drawn as a custos and
     * is never sung.
     */
    Custos,
}

public enum class GregorianLiquescence { None, Small, Ascending, Descending }

public enum class GregorianEpisemaPosition { Above, Below }

public data class GregorianAccidentalMark(
    val kind: GregorianAccidental,
    val pitch: Int,
    val sourceRange: GregorianSourceRange,
)

/**
 * Semantic neume families used by the native compositor. These deliberately mirror Exsurge's named-neume layer
 * rather than treating a neume as an arbitrary row of noteheads.
 */
public enum class GregorianNeumeForm {
    Apostropha,
    Bivirga,
    Trivirga,
    Climacus,
    Clivis,
    Ancus,
    Distropha,
    Oriscus,
    PesQuassus,
    PesSubpunctis,
    Podatus,
    Porrectus,
    PorrectusFlexus,
    PunctaInclinata,
    Punctum,
    Salicus,
    SalicusFlexus,
    Scandicus,
    ScandicusFlexus,
    Torculus,
    TorculusResupinus,
    TorculusResupinusFlexus,
    Tristropha,
    Virga,
}

public enum class GregorianLyricStyle { Regular, Preparatory, Accented, Rubric }

/**
 * A written note. [moraCount] is at least one when the note [hasMora], and [repetitionCount] at least one, as the
 * parser writes them.
 */
public data class GregorianNote(
    val id: String,
    val pitch: Int,
    val relativePitch: Int,
    val shape: GregorianNoteShape,
    val accidental: GregorianAccidental?,
    val hasMora: Boolean,
    val hasEpisema: Boolean,
    val moraCount: Int = if (hasMora) 1 else 0,
    /** The side of the note the GABC sets its dot on (`.1` above, `.0` below); null where it leaves the side to the engraver. */
    val moraPosition: GregorianEpisemaPosition? = null,
    /** The side the GABC sets the horizontal episema on (`_1`, `_0`); null where it leaves the side to the engraver. */
    val episemaPosition: GregorianEpisemaPosition? = null,
    val liquescence: GregorianLiquescence = GregorianLiquescence.None,
    val isCavum: Boolean = false,
    val hasIctus: Boolean = false,
    /** The side the GABC sets the ictus on (`'1`, `'0`); null where it leaves the side to the engraver. */
    val ictusPosition: GregorianEpisemaPosition? = null,
    /**
     * How many notes the one pitch letter writes. GABC abbreviates two virgae or three strophae on a pitch as
     * `hvv` and `hsss`; the compiled timeline holds a single event for the group, which is drawn and sung as that
     * many notes.
     */
    val repetitionCount: Int = 1,
    val sourceRange: GregorianSourceRange,
) {
    /** True for a custos, which guides the eye and is not sung. */
    val isCustos: Boolean get() = shape == GregorianNoteShape.Custos

    /** The notes this one event draws and sings: as many as it is written, and none for a custos. */
    val writtenNoteCount: Int get() = if (isCustos) 0 else repetitionCount

    /** A custos, or a note written more than once, is a neume in itself. */
    internal val standsAlone: Boolean get() = isCustos || repetitionCount > 1
}

/** The space a GABC group writes before a neume, after the neume before it in the same group. */
public sealed interface GregorianNeumeSpace {
    /** None is written: the engraver's own, between the neumes it divides a run of notes into. */
    public data object Automatic : GregorianNeumeSpace

    /** `!`: the two neumes are joined. */
    public data object Joined : GregorianNeumeSpace

    /** `/`, `//`: a small space for each slash. */
    public data class Slashes(val count: Int) : GregorianNeumeSpace

    /** A space within the group: a neumatic cut. */
    public data object Space : GregorianNeumeSpace
}

public data class GregorianNeume(
    val id: String,
    val syllableId: String,
    val lyric: String,
    val lyricStyle: GregorianLyricStyle = GregorianLyricStyle.Regular,
    val startsWord: Boolean,
    val notes: List<GregorianNote>,
    val form: GregorianNeumeForm = GregorianNeumeClassifier.classify(notes),
    val sourceRange: GregorianSourceRange,
    /**
     * The letters of [lyric], as character offsets, that a brace pair in the GABC marks to be centred under the first
     * note (`{ia}.`); null when none is marked, and the sung vowel is centred.
     */
    val centeredRange: IntRange? = null,
    /** The space written before the neume, after the one before it in its group: [GregorianNeumeSpace.Automatic] for a group's first neume. */
    val spaceBefore: GregorianNeumeSpace = GregorianNeumeSpace.Automatic,
) {
    val eventIds: List<String> get() = notes.map { it.id }

    /** The notes the neume draws and sings: one for each note, or as many as a doubled note is written, and none for a custos. */
    val writtenNoteCount: Int get() = notes.sumOf { it.writtenNoteCount }
}

public data class GregorianLyricMark(
    val text: String,
    val style: GregorianLyricStyle,
    val startsWord: Boolean,
    val sourceRange: GregorianSourceRange,
    /** The identifier of the score the mark was parsed in, if one was given. */
    val scoreId: String? = null,
    /**
     * Whether the mark heads the words after it, as ℣., ℟., a verse number or any sign written before a syllable's words
     * does, rather than closing the words before it, as an asterisk, a flex or a repeat mark written after them or alone
     * does.
     */
    val beginsWhatFollows: Boolean = false,
) {
    /** Named by where it is written, after its score, as the score's notes are: `lyric-mark-5-7`, or `kyrie-lyric-mark-5-7`. */
    val id: String get() = "${scoreId?.let { "$it-" } ?: ""}lyric-mark-${sourceRange.lowerBound}-${sourceRange.upperBound}"
}

public sealed interface GregorianNotationElement {
    public data class Clef(val clef: GregorianClef) : GregorianNotationElement
    public data class Accidental(val mark: GregorianAccidentalMark) : GregorianNotationElement
    public data class Neume(val neume: GregorianNeume) : GregorianNotationElement
    public data class LyricMark(val mark: GregorianLyricMark) : GregorianNotationElement
    public data class Division(val division: GregorianDivision) : GregorianNotationElement
    public data object ForcedBreak : GregorianNotationElement
}

public data class GregorianScore(val elements: List<GregorianNotationElement>) {
    val neumes: List<GregorianNeume> get() = elements.mapNotNull { (it as? GregorianNotationElement.Neume)?.neume }

    val eventIds: List<String> get() = neumes.flatMap { it.eventIds }

    /** The words the score sings, as they read. */
    val lyricText: String
        get() {
            val result = StringBuilder()
            var separatesFollowingLyric = false
            fun append(text: String, startsWord: Boolean, isLyricMark: Boolean) {
                if (text.isEmpty()) return
                if (result.isNotEmpty() && (startsWord || separatesFollowingLyric) && !result.last().isWhitespace()) {
                    result.append(' ')
                }
                result.append(text)
                separatesFollowingLyric = isLyricMark
            }
            for (element in elements) {
                when (element) {
                    is GregorianNotationElement.Neume -> append(element.neume.lyric, element.neume.startsWord, false)
                    is GregorianNotationElement.LyricMark -> append(element.mark.text, element.mark.startsWord, true)
                    else -> Unit
                }
            }
            return SPACE_BEFORE_PUNCTUATION.replace(result.toString(), "$1")
        }
}

/**
 * Unicode's spaces, as Swift's `isWhitespace` and Foundation's regular expressions read them, as a character class:
 * the JDK's `\s` is ASCII alone, and Android's regular expressions don't take the flag that widens it.
 */
internal const val UNICODE_SPACE: String =
    "[\t\n\u000B\u000C\r \u0085\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]"

/** Spaces before punctuation, which a score's lyric text closes up. */
private val SPACE_BEFORE_PUNCTUATION = Regex("$UNICODE_SPACE+([,.;:!?])")

public sealed class GregorianScoreParserError(message: String) : Exception(message) {
    public data class UnterminatedNotation(val offset: Int) :
        GregorianScoreParserError("The GABC notation beginning at offset $offset is not closed.")

    public data class InvalidClef(val token: String, val offset: Int) :
        GregorianScoreParserError("Invalid clef $token at offset $offset.")

    public data class UnsupportedToken(val token: String, val offset: Int) :
        GregorianScoreParserError("Unsupported GABC token $token at offset $offset.")

    public data class TooManyNotationNotes(val offset: Int) :
        GregorianScoreParserError("The notation contains more notes than its compiled timeline near offset $offset.")

    public data class TimelineMismatch(val expected: Int, val parsed: Int) :
        GregorianScoreParserError("The compiled timeline contains $expected notes but the notation contains $parsed.")

    public data class EventSyllableMismatch(val eventId: String) :
        GregorianScoreParserError("Timeline event $eventId is assigned to a different syllable than its neume.")
}

public object GregorianScoreParser {
    private val comment = Regex("""%[^\r\n]*""")
    private val tag = Regex("""<[^>]+>""")
    private val preparatory = Regex("""_([^_]+)_""")
    private val accented = Regex("""\*([^*]+)\*""")

    private val spaces = Regex("$UNICODE_SPACE+")

    /** The signs and repeat marks set as rubrics, beside the verse numbers. */
    private val rubrics = setOf("*", "**", "†", "‡", "+", "✠", "℣.", "℟.", "V/.", "R/.", "ij.", "iij.", "ij", "iij")
    private val verseNumber = Regex("^[0-9]+\\.?$")

    /**
     * Parses a GABC score, numbering its written notes as events in the order written.
     *
     * The notes are named `note-0`, `note-1` and so on, and the syllables `syllable-0`, `syllable-1`: one for each
     * notation group that writes a note, whether or not the group carries lyric text. These are the names a content
     * compiler gives the events of a compiled timeline. A score identifier prefixes both (`kyrie-note-0`), which
     * keeps the names of several scores apart.
     *
     * The notation follows the `%%` that ends a GABC file's header; text without one is read as notation alone.
     */
    @Throws(GregorianScoreParserError::class)
    public fun parse(gabc: String, scoreId: String? = null): GregorianScore =
        parse(gabc, EventSource.Numbered(scoreId))

    /**
     * Parses a GABC score whose notes a compiled timeline names, one event for each written note in order. The
     * parse fails if the notation writes more or fewer notes than the timeline holds, or if a group that carries
     * lyric text spans more than one of its syllables.
     */
    @Throws(GregorianScoreParserError::class)
    public fun parse(gabc: String, timeline: GregorianTimeline<*>): GregorianScore =
        parse(gabc, EventSource.Compiled(timeline.events.map { ResolvedEvent(it.id, it.syllableId, it.relativePitch) }))

    private class ResolvedEvent(val id: String, val syllableId: String, val relativePitch: Int)

    private sealed interface EventSource {
        class Compiled(val events: List<ResolvedEvent>) : EventSource
        class Numbered(val scoreId: String?) : EventSource
    }

    private fun parse(gabc: String, source: EventSource): GregorianScore {
        val bodyStart = gabc.indexOf("%%").let { if (it < 0) 0 else it + 2 }
        // In GABC, a single percent sign begins a comment through the end of its line. The newline stays, so the
        // lyric fragments around it can't be joined.
        val body = comment.replace(gabc.substring(bodyStart), "")
        val offsets = CharacterOffsets(body)
        val compiledEvents = (source as? EventSource.Compiled)?.events
        val scoreId = (source as? EventSource.Numbered)?.scoreId
        val eventPrefix = scoreId?.let { "$it-" } ?: ""
        // The syllable of each compiled event, by its name; the first event given a name keeps it.
        val compiledSyllables = compiledEvents?.let { events ->
            HashMap<String, String>().apply { for (event in events) getOrPut(event.id) { event.syllableId } }
        }
        val elements = mutableListOf<GregorianNotationElement>()
        var cursor = 0
        var eventIndex = 0
        // Numbered events give each group that writes a note a syllable.
        var syllableIndex = 0
        // A word begins where a space stands before a group, and a clef or a bar that stands there alone does not
        // use the beginning up: in "mus.(h.) (:)De(g)us" the bar ends one word and "De" begins the next, though no
        // space follows the bar. Notes without words are the melisma of the syllable before them, and leave it a
        // word unfinished.
        var wordStartIsPending = false

        while (true) {
            val open = body.indexOf('(', cursor)
            if (open < 0) break
            // The lyric is read without its markup tags (`<i>`, `<sp>`).
            val lyricSource = tag.replace(body.substring(cursor, open), "")
            val close = body.indexOf(')', open)
            if (close < 0) throw GregorianScoreParserError.UnterminatedNotation(offsets[open])

            val notationStart = open + 1
            val notation = body.substring(notationStart, close)
            val groupOffset = notationStart
            val parsedLyricStyle = lyricStyle(lyricSource)
            val sourceStartsWord = lyricStartsWord(lyricSource, isFirst = elements.isEmpty())

            val parsed = parseNotationGroup(notation) { offsets[groupOffset + it] }
            val hasNotes = parsed.any { it is ParsedToken.Notes }
            // The rubrics written with a sung syllable stand apart from it: a sign or a verse number before it, a flex
            // after it.
            val cleaned = cleanLyric(lyricSource)
            val sung = if (hasNotes) sungLyric(cleaned) else SungLyric(cleaned)
            val lyric = sung.text
            val startsWord = sourceStartsWord || sung.leading.isNotEmpty() || (wordStartIsPending && lyric.isNotEmpty())
            val leadingStartsWord = sourceStartsWord || wordStartIsPending
            if (hasNotes) {
                // A rubric written after the syllable ends its word.
                wordStartIsPending = sung.trailing.isNotEmpty()
            } else if (lyric.isNotEmpty()) {
                // A rubric that stands alone ends the word before it, and begins none: `ne,(f.) R/.(::)In(f)` begins a
                // word at "In".
                wordStartIsPending = true
            } else if (sourceStartsWord && parsed.isNotEmpty()) {
                wordStartIsPending = true
            }
            for ((index, mark) in sung.leading.withIndex()) {
                elements += GregorianNotationElement.LyricMark(
                    GregorianLyricMark(
                        text = standaloneLyricText(mark),
                        style = GregorianLyricStyle.Rubric,
                        startsWord = if (index == 0) leadingStartsWord else true,
                        sourceRange = GregorianSourceRange(offsets[cursor] + index, offsets[cursor] + index + 1),
                        scoreId = scoreId,
                        beginsWhatFollows = true,
                    ),
                )
            }
            // A lyric written with a bar or a clef and no notes is a rubric. One that heads what follows stands after
            // the bar, at the head of its words: `me(g.)um.(f.) R/.(::)In(f)` reads `um. ‖ ℟. In`.
            val standaloneMark = if (lyric.isNotEmpty() && !hasNotes) {
                val displayLyric = standaloneLyricText(lyric)
                GregorianLyricMark(
                    text = displayLyric,
                    style = standaloneLyricStyle(displayLyric, parsedLyricStyle),
                    startsWord = startsWord,
                    sourceRange = GregorianSourceRange(offsets[cursor], offsets[open]),
                    scoreId = scoreId,
                    beginsWhatFollows = headsWhatFollows(displayLyric),
                )
            } else {
                null
            }
            if (standaloneMark != null && !standaloneMark.beginsWhatFollows) {
                elements += GregorianNotationElement.LyricMark(standaloneMark)
            }
            var appliedLyric = false

            for (token in parsed) {
                when (token) {
                    is ParsedToken.Clef -> elements += GregorianNotationElement.Clef(token.clef)
                    is ParsedToken.Accidental -> elements += GregorianNotationElement.Accidental(token.mark)
                    is ParsedToken.Division -> elements += GregorianNotationElement.Division(token.division)
                    ParsedToken.ForcedBreak -> elements += GregorianNotationElement.ForcedBreak
                    is ParsedToken.Notes -> {
                        val mappedNotes = mutableListOf<GregorianNote>()
                        val mappedEvents = mutableListOf<ResolvedEvent>()
                        for (note in token.notes) {
                            val event = if (compiledEvents != null) {
                                if (eventIndex !in compiledEvents.indices) {
                                    throw GregorianScoreParserError.TooManyNotationNotes(note.sourceRange.lowerBound)
                                }
                                compiledEvents[eventIndex]
                            } else {
                                ResolvedEvent(
                                    id = "${eventPrefix}note-$eventIndex",
                                    syllableId = "${eventPrefix}syllable-$syllableIndex",
                                    // Pitch letters run from `a` (0); `h` is 7.
                                    relativePitch = note.pitch - 7,
                                )
                            }
                            mappedEvents += event
                            mappedNotes += GregorianNote(
                                id = event.id,
                                pitch = note.pitch,
                                relativePitch = event.relativePitch,
                                shape = note.shape,
                                accidental = note.accidental,
                                hasMora = note.hasMora,
                                hasEpisema = note.hasEpisema,
                                moraCount = note.moraCount,
                                moraPosition = note.moraPosition,
                                episemaPosition = note.episemaPosition,
                                liquescence = note.liquescence,
                                isCavum = note.isCavum,
                                hasIctus = note.hasIctus,
                                ictusPosition = note.ictusPosition,
                                repetitionCount = note.repetitionCount,
                                sourceRange = note.sourceRange,
                            )
                            eventIndex += 1
                        }

                        val first = mappedNotes.firstOrNull() ?: continue
                        val event = mappedEvents.first()
                        val neumeLyric = if (appliedLyric) "" else lyric
                        if (neumeLyric.isNotEmpty() && compiledSyllables != null &&
                            mappedNotes.any { compiledSyllables[it.id] != event.syllableId }
                        ) {
                            throw GregorianScoreParserError.EventSyllableMismatch(first.id)
                        }
                        val segments = GregorianNeumeSegmenter.segment(mappedNotes)
                        for ((segmentIndex, segment) in segments.withIndex()) {
                            val segmentFirst = segment.firstOrNull() ?: continue
                            // A punctum that begins a pressus abuts the neume it repeats the first note of (`hhg`, `ffg`).
                            val previousSegment = segments.getOrNull(segmentIndex - 1)
                            val joinsPressus = segment.size > 1 && previousSegment?.size == 1 &&
                                previousSegment.last().let { previous ->
                                    previous.pitch == segmentFirst.pitch && previous.shape == GregorianNoteShape.Punctum &&
                                        previous.liquescence == GregorianLiquescence.None && previous.moraCount == 0
                                }
                            elements += GregorianNotationElement.Neume(
                                GregorianNeume(
                                    id = segmentFirst.id,
                                    syllableId = event.syllableId,
                                    lyric = if (segmentIndex == 0) neumeLyric else "",
                                    lyricStyle = if (segmentIndex == 0 && neumeLyric.isNotEmpty()) parsedLyricStyle else GregorianLyricStyle.Regular,
                                    startsWord = segmentIndex == 0 && !appliedLyric && startsWord,
                                    notes = segment,
                                    sourceRange = GregorianSourceRange(
                                        segment.first().sourceRange.lowerBound,
                                        segment.last().sourceRange.upperBound,
                                    ),
                                    centeredRange = if (segmentIndex == 0 && neumeLyric.isNotEmpty()) sung.centeredRange else null,
                                    spaceBefore = when {
                                        segmentIndex == 0 -> token.spaceBefore
                                        joinsPressus -> GregorianNeumeSpace.Joined
                                        else -> GregorianNeumeSpace.Automatic
                                    },
                                ),
                            )
                        }
                        appliedLyric = true
                    }
                }
            }
            if (standaloneMark != null && standaloneMark.beginsWhatFollows) {
                elements += GregorianNotationElement.LyricMark(standaloneMark)
            }
            for ((index, mark) in sung.trailing.withIndex()) {
                val lowerBound = offsets[open] - sung.trailing.size + index
                elements += GregorianNotationElement.LyricMark(
                    GregorianLyricMark(
                        text = standaloneLyricText(mark),
                        style = GregorianLyricStyle.Rubric,
                        startsWord = true,
                        sourceRange = GregorianSourceRange(lowerBound, lowerBound + 1),
                        scoreId = scoreId,
                    ),
                )
            }
            if (hasNotes) syllableIndex += 1

            cursor = close + 1
        }

        if (compiledEvents != null && eventIndex != compiledEvents.size) {
            throw GregorianScoreParserError.TimelineMismatch(compiledEvents.size, eventIndex)
        }
        return GregorianScore(elements)
    }

    private sealed interface ParsedToken {
        class Clef(val clef: GregorianClef) : ParsedToken
        class Accidental(val mark: GregorianAccidentalMark) : ParsedToken
        class Notes(val notes: List<ParsedNote>, val spaceBefore: GregorianNeumeSpace) : ParsedToken
        class Division(val division: GregorianDivision) : ParsedToken
        data object ForcedBreak : ParsedToken
    }

    private class ParsedNote(
        val pitch: Int,
        val shape: GregorianNoteShape,
        val accidental: GregorianAccidental?,
        val hasMora: Boolean,
        var hasEpisema: Boolean,
        val moraCount: Int,
        val moraPosition: GregorianEpisemaPosition?,
        var episemaPosition: GregorianEpisemaPosition?,
        /** The notes its episema covers, counting back from this one: as many as the GABC writes underscores (`kj__`). */
        val episemaReach: Int,
        val liquescence: GregorianLiquescence,
        val isCavum: Boolean,
        val hasIctus: Boolean,
        val ictusPosition: GregorianEpisemaPosition?,
        val repetitionCount: Int,
        val sourceRange: GregorianSourceRange,
    )

    /** Reads one group of notation; [at] gives the offset in the body of a position in the group. */
    private fun parseNotationGroup(characters: String, at: (Int) -> Int): List<ParsedToken> {
        val result = mutableListOf<ParsedToken>()
        val currentNotes = mutableListOf<ParsedNote>()
        var index = 0
        // The separator written since the last run of notes, which the next run takes as its space before: the first,
        // as Exsurge reads it, with `//` counted as one.
        var spaceBefore: GregorianNeumeSpace = GregorianNeumeSpace.Automatic

        fun flushNotes() {
            if (currentNotes.isNotEmpty()) {
                result += ParsedToken.Notes(currentNotes.toList(), spaceBefore)
                currentNotes.clear()
                spaceBefore = GregorianNeumeSpace.Automatic
            }
        }

        fun separate(space: GregorianNeumeSpace) {
            val current = spaceBefore
            spaceBefore = when {
                current == GregorianNeumeSpace.Automatic -> space
                current is GregorianNeumeSpace.Slashes && space is GregorianNeumeSpace.Slashes ->
                    GregorianNeumeSpace.Slashes(current.count + space.count)
                else -> current
            }
        }

        fun isClefAt(at: Int): Boolean {
            val character = characters[at]
            if (character != 'c' && character != 'f') return false
            if (at + 1 < characters.length && isNumber(characters[at + 1])) return true
            return character == 'c' && at + 2 < characters.length && characters[at + 1] == 'b' && isNumber(characters[at + 2])
        }

        // A bracketed sign (`[alt:...]`) is passed over; one left open runs to the end of the group.
        fun skipBracket(from: Int): Int {
            val close = characters.indexOf(']', from + 1)
            return if (close >= 0) close + 1 else characters.length
        }

        while (index < characters.length) {
            val character = characters[index]

            if (character == '[') {
                flushNotes()
                index = skipBracket(index)
                continue
            }
            if (character.isWhitespace() || character == '/' || character == '!') {
                flushNotes()
                separate(
                    when (character) {
                        '/' -> GregorianNeumeSpace.Slashes(1)
                        '!' -> GregorianNeumeSpace.Joined
                        else -> GregorianNeumeSpace.Space
                    },
                )
                index += 1
                continue
            }

            if (isClefAt(index)) {
                flushNotes()
                val isFlatCClef = characters[index + 1] == 'b'
                val lineIndex = index + if (isFlatCClef) 2 else 1
                val token = characters.substring(index, lineIndex + 1)
                val line = characters[lineIndex].takeIf { it in '0'..'9' }?.digitToInt()
                if (line == null || line !in 1..4) throw GregorianScoreParserError.InvalidClef(token, at(index))
                result += ParsedToken.Clef(
                    GregorianClef(if (character == 'c') GregorianClefKind.C else GregorianClefKind.F, line, flattensB = isFlatCClef),
                )
                index = lineIndex + 1
                continue
            }

            if (character == ':' || character == ';' || character == ',' || character == '`') {
                flushNotes()
                if (character == ':' && index + 1 < characters.length && characters[index + 1] == ':') {
                    result += ParsedToken.Division(GregorianDivision.Final)
                    index += 2
                } else {
                    result += ParsedToken.Division(
                        when (character) {
                            ',', '`' -> GregorianDivision.Minima
                            ';' -> GregorianDivision.Minor
                            else -> GregorianDivision.Major
                        },
                    )
                    index += 1
                }
                continue
            }

            if (character == 'z' || character == 'Z') {
                flushNotes()
                // `z0` writes a custos for the next note, as before a change of clef, and breaks no line: a staff's
                // end has its own.
                if (character == 'z' && index + 1 < characters.length && characters[index + 1] == '0') {
                    index += 2
                    continue
                }
                result += ParsedToken.ForcedBreak
                index += 1
                continue
            }

            val pitch = pitchValue(character)
            if (pitch == null) {
                if (isIgnorableControl(character)) {
                    index += 1
                    continue
                }
                throw GregorianScoreParserError.UnsupportedToken(character.toString(), at(index))
            }

            val noteStart = index
            if (index + 1 < characters.length) {
                val accidental = when (characters[index + 1]) {
                    'x' -> GregorianAccidental.Flat
                    'y' -> GregorianAccidental.Natural
                    '#' -> GregorianAccidental.Sharp
                    else -> null
                }
                if (accidental != null) {
                    flushNotes()
                    result += ParsedToken.Accidental(
                        GregorianAccidentalMark(accidental, pitch, GregorianSourceRange(at(noteStart), at(noteStart + 2))),
                    )
                    index += 2
                    continue
                }
            }
            index += 1
            val suffixBuilder = StringBuilder()
            while (index < characters.length) {
                val next = characters[index]
                if (next == '[') {
                    index = skipBracket(index)
                    continue
                }
                if (next.isWhitespace() || next in "/:;,`zZ" || pitchValue(next) != null || isClefAt(index)) break
                suffixBuilder.append(next)
                index += 1
            }
            val suffix = suffixBuilder.toString()

            val liquescence = when {
                '<' in suffix -> GregorianLiquescence.Ascending
                '>' in suffix -> GregorianLiquescence.Descending
                '~' in suffix -> GregorianLiquescence.Small
                else -> GregorianLiquescence.None
            }

            // A pitch letter whose signs end in `+` is a custos, as in Exsurge: it stands apart from the notes on
            // either side.
            val isCustos = suffix.endsWith("+")

            val shape = when {
                isCustos -> GregorianNoteShape.Custos
                'w' in suffix || 'W' in suffix -> GregorianNoteShape.Quilisma
                // A capital is an inclinatum, liquescent or not: `F~` is the diminished inclinatum that ends an ancus.
                character.isUpperCase() -> GregorianNoteShape.Inclinatum
                '<' in suffix || '>' in suffix || '~' in suffix -> GregorianNoteShape.Liquescent
                'v' in suffix || 'V' in suffix -> GregorianNoteShape.Virga
                'o' in suffix || 'O' in suffix -> GregorianNoteShape.Oriscus
                's' in suffix || 'S' in suffix -> GregorianNoteShape.Stropha
                else -> GregorianNoteShape.Punctum
            }

            val accidental = when {
                'x' in suffix -> GregorianAccidental.Flat
                'y' in suffix -> GregorianAccidental.Natural
                '#' in suffix -> GregorianAccidental.Sharp
                else -> null
            }

            suffix.firstOrNull { !isSupportedSuffix(it) }?.let {
                throw GregorianScoreParserError.UnsupportedToken(it.toString(), at(noteStart + 1))
            }

            if (isCustos) flushNotes()
            currentNotes += ParsedNote(
                pitch = pitch,
                shape = shape,
                accidental = accidental,
                hasMora = '.' in suffix,
                hasEpisema = '_' in suffix,
                moraCount = suffix.count { it == '.' },
                moraPosition = signPosition('.', suffix),
                episemaPosition = signPosition('_', suffix),
                episemaReach = suffix.count { it == '_' },
                liquescence = liquescence,
                // r0 is a hollow note within lines; r1 to r5 are signs written above the note (an accent, a
                // circle), not a hollow note.
                isCavum = isCavum(suffix),
                hasIctus = '\'' in suffix,
                ictusPosition = signPosition('\'', suffix),
                repetitionCount = if (isCustos) 1 else repeatedNoteCount(suffix),
                sourceRange = GregorianSourceRange(at(noteStart), at(index)),
            )
            if (isCustos || '!' in suffix) {
                flushNotes()
                if ('!' in suffix) separate(GregorianNeumeSpace.Joined)
            }
        }

        flushNotes()

        // An episema written with more than one underscore (`kj__`) reaches back over as many notes of the group.
        val groupNotes = result.flatMap { (it as? ParsedToken.Notes)?.notes ?: emptyList() }
        for ((order, note) in groupNotes.withIndex()) {
            for (back in 1 until note.episemaReach) {
                val earlier = groupNotes.getOrNull(order - back) ?: break
                earlier.hasEpisema = true
                if (earlier.episemaPosition == null) earlier.episemaPosition = note.episemaPosition
            }
        }
        return result
    }

    /** The side a sign's digit sets it on: `1` above, `0` below. */
    private fun signPosition(sign: Char, suffix: String): GregorianEpisemaPosition? {
        for (index in 0 until suffix.length - 1) {
            if (suffix[index] != sign) continue
            if (suffix[index + 1] == '1') return GregorianEpisemaPosition.Above
            if (suffix[index + 1] == '0') return GregorianEpisemaPosition.Below
        }
        return null
    }

    /** GABC's quick virga and stropha: each repeated sign is another note on the same pitch (`hvv` is two virgae, `hsss` three strophae). */
    private fun repeatedNoteCount(suffix: String): Int {
        var count = 1
        var shape: Char? = null
        for (sign in suffix) {
            if (sign != 's' && sign != 'v') continue
            if (shape == sign) count += 1
            shape = sign
        }
        return count
    }

    private fun isCavum(suffix: String): Boolean = suffix.indices.any { index ->
        if (suffix[index] != 'r' && suffix[index] != 'R') return@any false
        // A sign with no whole-number value leaves the r alone, a hollow note.
        val sign = suffix.getOrNull(index + 1)?.takeIf(::isNumber)?.let(Character::getNumericValue) ?: -1
        sign < 0 || sign == 0
    }

    /** A note's place on the staff by its letter, `a` (0) to `m` (12). */
    private fun pitchValue(character: Char): Int? {
        val lower = character.toString().lowercase()
        if (lower.length != 1) return null
        return if (lower[0] in 'a'..'m') lower[0] - 'a' else null
    }

    /** A numeral of any script, as Swift's `Character.isNumber` reads one. */
    private fun isNumber(character: Char): Boolean = when (Character.getType(character)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }

    private fun isSupportedSuffix(character: Char): Boolean =
        isNumber(character) || character in "._wW<>~xy#vVoOqQsSrR'!@-+*†("

    private fun isIgnorableControl(character: Char): Boolean = isNumber(character) || character in "'!@]-+_*†(."

    /** A group's lyric, and the letters a brace pair in it marks to be centred, as character offsets into the lyric. */
    private class CleanLyric(val text: String, val centeredRange: IntRange?)

    /** A sung syllable's lyric, and the rubrics written before and after it. */
    private class SungLyric(
        val leading: List<String>,
        val text: String,
        val centeredRange: IntRange?,
        val trailing: List<String>,
    ) {
        constructor(lyric: CleanLyric) : this(emptyList(), lyric.text, lyric.centeredRange, emptyList())
    }

    /** A group's lyric as sung, from its source without tags. */
    private fun cleanLyric(source: String): CleanLyric {
        // Psalm-tone sources use paired underscores for preparatory syllables and paired asterisks for accented
        // syllables. These delimiters describe pointing and style and are not lyric text. The paired patterns leave
        // a standalone liturgical asterisk unchanged.
        var text = preparatory.replace(source, "$1")
        text = accented.replace(text, "$1")
        // A tilde is a space that no line break may take.
        text = text.replace("~", " ").replace("\n", " ").split(spaces).filter { it.isNotEmpty() }.joinToString(" ").trim()
        // Braces name the letters to centre under the note, in place of the sung vowel: `{ia}.`, `p{a}u`.
        val characters = mutableListOf<String>()
        var braceStart: Int? = null
        var braceEnd: Int? = null
        for (character in graphemes(text)) {
            when (character) {
                "{" -> if (braceStart == null) braceStart = characters.size
                "}" -> if (braceStart != null && braceEnd == null) braceEnd = characters.size
                else -> characters += character
            }
        }
        val start = braceStart
        val end = braceEnd
        val centeredRange = if (start != null && end != null && end > start) start until end else null
        return CleanLyric(characters.joinToString(""), centeredRange)
    }

    /**
     * A sung syllable's lyric, without the rubrics written before or after its words: `V/. Dó`, `2. Pro`, `* ab`,
     * `hæc, †`. A lyric made of rubrics alone is left whole.
     */
    private fun sungLyric(lyric: CleanLyric): SungLyric {
        val words = lyric.text.split(" ").filter { it.isNotEmpty() }
        var first = 0
        var last = words.size
        while (first < last && isRubricMark(words[first])) first += 1
        while (last > first && isRubricMark(words[last - 1])) last -= 1
        if (first >= last || (first == 0 && last == words.size)) return SungLyric(lyric)
        val text = words.subList(first, last).joinToString(" ")
        val dropped = words.take(first).sumOf { graphemes(it).size + 1 }
        val textLength = graphemes(text).size
        val centeredRange = lyric.centeredRange?.let { range ->
            val lower = range.first - dropped
            val upper = range.last + 1 - dropped
            if (lower >= 0 && upper <= textLength) lower until upper else null
        }
        return SungLyric(words.take(first), text, centeredRange, words.drop(last))
    }

    /** Whether a word of a lyric is a rubric: a sign, a repeat mark, or a verse number. */
    private fun isRubricMark(word: String): Boolean = word in rubrics || verseNumber.matches(word)

    /**
     * Whether a rubric written alone heads the words after it: ℣., ℟., the cross signed at a canticle's first word, or a
     * verse number. The others close the words before them.
     */
    private fun headsWhatFollows(text: String): Boolean = text == "℣." || text == "℟." || text == "✠" || verseNumber.matches(text)

    /** A text's characters as a reader counts them, an accent with its letter, as Swift counts them. */
    private fun graphemes(text: String): List<String> {
        val breaks = BreakIterator.getCharacterInstance()
        breaks.setText(text)
        val clusters = mutableListOf<String>()
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            clusters += text.substring(start, end)
            start = end
            end = breaks.next()
        }
        return clusters
    }

    private fun lyricStyle(source: String): GregorianLyricStyle {
        if (accented.containsMatchIn(source)) return GregorianLyricStyle.Accented
        if (preparatory.containsMatchIn(source)) return GregorianLyricStyle.Preparatory
        return GregorianLyricStyle.Regular
    }

    private fun standaloneLyricText(lyric: String): String = when (lyric) {
        "V/." -> "℣."
        "R/." -> "℟."
        else -> lyric
    }

    private fun standaloneLyricStyle(lyric: String, parsedStyle: GregorianLyricStyle): GregorianLyricStyle {
        if (parsedStyle != GregorianLyricStyle.Regular) return parsedStyle
        return if (isRubricMark(lyric)) GregorianLyricStyle.Rubric else GregorianLyricStyle.Regular
    }

    private fun lyricStartsWord(source: String, isFirst: Boolean): Boolean {
        if (isFirst) return true
        return source.firstOrNull()?.isWhitespace() == true
    }
}

/**
 * Offsets in a text as Swift counts a String's: in characters as a reader counts them, a CR LF, or a letter with its
 * accent written after it, being one. A position within such a character is its start.
 */
private class CharacterOffsets(text: String) {
    // Most scores are plain ASCII without CR LF, where the two counts agree.
    private val offsets: IntArray? = if (text.all { it.code < 0x80 } && "\r\n" !in text) {
        null
    } else {
        IntArray(text.length + 1).also { offsets ->
            val breaks = BreakIterator.getCharacterInstance()
            breaks.setText(text)
            var count = 0
            var start = breaks.first()
            var end = breaks.next()
            while (end != BreakIterator.DONE) {
                for (index in start until end) offsets[index] = count
                count += 1
                start = end
                end = breaks.next()
            }
            offsets[text.length] = count
        }
    }

    operator fun get(index: Int): Int = offsets?.get(index) ?: index
}

public object GregorianNeumeClassifier {
    public fun classify(notes: List<GregorianNote>): GregorianNeumeForm {
        val first = notes.firstOrNull() ?: return GregorianNeumeForm.Punctum
        val pitches = notes.map { it.pitch }
        val directions = pitches.zipWithNext { from, to -> direction(from, to) }
        val allEqual = pitches.toSet().size == 1
        val allInclinata = notes.all { it.shape == GregorianNoteShape.Inclinatum }
        val allVirga = notes.all { it.shape == GregorianNoteShape.Virga }
        // A note written twice or thrice (`hvv`, `hsss`) counts as so many.
        val writtenCount = notes.sumOf { it.repetitionCount }

        if (allInclinata) return GregorianNeumeForm.PunctaInclinata
        if (writtenCount == 1) {
            return when (first.shape) {
                GregorianNoteShape.Virga -> GregorianNeumeForm.Virga
                GregorianNoteShape.Stropha -> GregorianNeumeForm.Apostropha
                GregorianNoteShape.Oriscus -> GregorianNeumeForm.Oriscus
                else -> GregorianNeumeForm.Punctum
            }
        }
        if (allEqual && allVirga) return if (writtenCount >= 3) GregorianNeumeForm.Trivirga else GregorianNeumeForm.Bivirga
        if (allEqual) return if (writtenCount >= 3) GregorianNeumeForm.Tristropha else GregorianNeumeForm.Distropha
        if (notes.size == 2) {
            if (directions[0] > 0) {
                return if (first.shape == GregorianNoteShape.Oriscus) GregorianNeumeForm.PesQuassus else GregorianNeumeForm.Podatus
            }
            return if (directions[0] < 0) GregorianNeumeForm.Clivis else GregorianNeumeForm.Punctum
        }

        // A pes and the diamonds that fall from it.
        if (directions.first() == 1 && notes.size >= 3 && notes.drop(2).all { it.shape == GregorianNoteShape.Inclinatum } &&
            directions.drop(1).all { it < 0 }
        ) {
            return GregorianNeumeForm.PesSubpunctis
        }

        val firstTwo = directions.take(2)
        val rest = directions.drop(2)
        if (firstTwo == listOf(1, 1)) {
            if (notes.drop(1).any { it.shape == GregorianNoteShape.Oriscus }) {
                return if (-1 in rest) GregorianNeumeForm.SalicusFlexus else GregorianNeumeForm.Salicus
            }
            return if (-1 in rest) GregorianNeumeForm.ScandicusFlexus else GregorianNeumeForm.Scandicus
        }
        if (firstTwo == listOf(-1, -1)) {
            return if (notes.last().liquescence != GregorianLiquescence.None) GregorianNeumeForm.Ancus else GregorianNeumeForm.Climacus
        }
        if (firstTwo == listOf(1, -1)) {
            return when {
                rest.take(2) == listOf(1, -1) -> GregorianNeumeForm.TorculusResupinusFlexus
                rest.firstOrNull() == 1 -> GregorianNeumeForm.TorculusResupinus
                else -> GregorianNeumeForm.Torculus
            }
        }
        if (firstTwo == listOf(-1, 1)) {
            return if (rest.firstOrNull() == -1) GregorianNeumeForm.PorrectusFlexus else GregorianNeumeForm.Porrectus
        }
        return GregorianNeumeForm.Punctum
    }
}

/** Whether a melody rises (1), falls (-1) or keeps its pitch (0) from one note to the next. */
private fun direction(from: Int, to: Int): Int = if (to > from) 1 else if (to < from) -1 else 0

/**
 * Splits a continuous GABC note run into the named forms accepted by the native compositor. This is the native
 * counterpart of Exsurge's `createNeumesFromNotes` state machine.
 */
public object GregorianNeumeSegmenter {
    public fun segment(notes: List<GregorianNote>): List<List<GregorianNote>> {
        // A note written twice or thrice (`hvv`, `hsss`) is a bivirga or a tristropha in itself, and a custos
        // belongs to no neume. Each stands alone, and the notes on either side group as they would without it.
        if (notes.none { it.standsAlone }) return segmentRun(notes)
        val result = mutableListOf<List<GregorianNote>>()
        val run = mutableListOf<GregorianNote>()
        for (note in notes) {
            if (note.standsAlone) {
                result += segmentRun(run.toList())
                run.clear()
                result += listOf(note)
            } else {
                run += note
            }
        }
        result += segmentRun(run.toList())
        return result
    }

    private fun segmentRun(notes: List<GregorianNote>): List<List<GregorianNote>> {
        if (notes.size <= 1) return if (notes.isEmpty()) emptyList() else listOf(notes)
        val result = mutableListOf<List<GregorianNote>>()
        var index = 0
        while (index < notes.size) {
            val remaining = notes.size - index
            if (remaining <= 1) {
                result += listOf(notes[index])
                break
            }
            val first = notes[index]
            val second = notes[index + 1]
            val firstDirection = direction(first.pitch, second.pitch)

            if (first.shape == GregorianNoteShape.Virga || first.shape == GregorianNoteShape.Stropha) {
                val count = repeatedShapeCount(notes, index, first.shape, first.pitch, maximum = 3)
                result += notes.subList(index, index + count)
                index += count
                continue
            }
            if (first.shape == GregorianNoteShape.Inclinatum) {
                var end = index + 1
                while (end < notes.size && notes[end].shape == GregorianNoteShape.Inclinatum) end += 1
                result += notes.subList(index, end)
                index = end
                continue
            }

            var length: Int
            if (firstDirection > 0) {
                if (remaining >= 3 && notes[index + 2].shape == GregorianNoteShape.Inclinatum &&
                    direction(second.pitch, notes[index + 2].pitch) < 0
                ) {
                    length = 3
                    while (index + length < notes.size && notes[index + length].shape == GregorianNoteShape.Inclinatum &&
                        direction(notes[index + length - 1].pitch, notes[index + length].pitch) < 0
                    ) {
                        length += 1
                    }
                } else if (remaining >= 3) {
                    val secondDirection = direction(second.pitch, notes[index + 2].pitch)
                    if (secondDirection > 0) {
                        length = 3
                        if (remaining >= 4 && direction(notes[index + 2].pitch, notes[index + 3].pitch) < 0) length = 4
                    } else if (secondDirection < 0) {
                        length = 3
                        if (remaining >= 4 && direction(notes[index + 2].pitch, notes[index + 3].pitch) > 0) {
                            length = 4
                            if (remaining >= 5 && direction(notes[index + 3].pitch, notes[index + 4].pitch) < 0) length = 5
                        }
                    } else {
                        length = 2
                    }
                } else {
                    length = 2
                }
            } else if (firstDirection < 0) {
                if (second.shape == GregorianNoteShape.Inclinatum) {
                    length = 2
                    while (index + length < notes.size && notes[index + length].shape == GregorianNoteShape.Inclinatum &&
                        direction(notes[index + length - 1].pitch, notes[index + length].pitch) < 0
                    ) {
                        length += 1
                    }
                } else if (remaining >= 3 && direction(second.pitch, notes[index + 2].pitch) < 0 &&
                    notes[index + 2].liquescence != GregorianLiquescence.None
                ) {
                    length = 3
                } else if (remaining >= 3 && direction(second.pitch, notes[index + 2].pitch) > 0) {
                    length = 3
                    if (remaining >= 4 && direction(notes[index + 2].pitch, notes[index + 3].pitch) < 0) length = 4
                } else {
                    length = 2
                }
            } else {
                length = unisonLength(notes, index)
            }
            result += notes.subList(index, index + length)
            index += length
        }
        return result
    }

    private fun repeatedShapeCount(notes: List<GregorianNote>, index: Int, shape: GregorianNoteShape, pitch: Int, maximum: Int): Int {
        var count = 0
        while (index + count < notes.size && count < maximum && notes[index + count].shape == shape &&
            notes[index + count].pitch == pitch
        ) {
            count += 1
        }
        return maxOf(1, count)
    }

    /**
     * How many plain notes repeated on one pitch make a neume, as Exsurge groups them: two a distropha and three a
     * tristropha only where the run ends with them. Where the melody goes on, the last of them begins the next neume,
     * a pressus (`ffg` is a punctum and a pes), and a dotted or diminished note stands alone.
     */
    private fun unisonLength(notes: List<GregorianNote>, index: Int): Int {
        val first = notes[index]
        val second = notes.getOrNull(index + 1)
        if (second == null || second.pitch != first.pitch || !isPlain(second) || first.moraCount > 0 ||
            first.liquescence == GregorianLiquescence.Small
        ) {
            return 1
        }
        val third = notes.getOrNull(index + 2) ?: return 2
        if (third.pitch != first.pitch) return 1
        if (second.moraCount > 0) return 2
        return if (index + 3 < notes.size) 2 else 3
    }

    /** A note of no shape of its own: a punctum, liquescent or not. */
    private fun isPlain(note: GregorianNote): Boolean =
        note.shape == GregorianNoteShape.Punctum || note.shape == GregorianNoteShape.Liquescent
}
