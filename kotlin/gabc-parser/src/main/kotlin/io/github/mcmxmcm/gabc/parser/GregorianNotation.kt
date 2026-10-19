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
    val episemaPosition: GregorianEpisemaPosition = GregorianEpisemaPosition.Above,
    val liquescence: GregorianLiquescence = GregorianLiquescence.None,
    val isCavum: Boolean = false,
    val hasIctus: Boolean = false,
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

public data class GregorianNeume(
    val id: String,
    val syllableId: String,
    val lyric: String,
    val lyricStyle: GregorianLyricStyle = GregorianLyricStyle.Regular,
    val startsWord: Boolean,
    val notes: List<GregorianNote>,
    val form: GregorianNeumeForm = GregorianNeumeClassifier.classify(notes),
    val sourceRange: GregorianSourceRange,
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
) {
    val id: String get() = "lyric-mark-${sourceRange.lowerBound}-${sourceRange.upperBound}"
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
            return Regex("$UNICODE_SPACE+([,.;:!?])").replace(result.toString(), "$1")
        }
}

/**
 * Unicode's spaces, as Swift's `isWhitespace` and Foundation's regular expressions read them, as a character class:
 * the JDK's `\s` is ASCII alone, and Android's regular expressions don't take the flag that widens it.
 */
internal const val UNICODE_SPACE: String =
    "[\t\n\u000B\u000C\r \u0085\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]"

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
        parse(gabc, EventSource.Numbered(scoreId?.let { "$it-" } ?: ""))

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
        class Numbered(val prefix: String) : EventSource
    }

    private fun parse(gabc: String, source: EventSource): GregorianScore {
        val bodyStart = gabc.indexOf("%%").let { if (it < 0) 0 else it + 2 }
        // In GABC, a single percent sign begins a comment through the end of its line. The newline stays, so the
        // lyric fragments around it can't be joined.
        val body = comment.replace(gabc.substring(bodyStart), "")
        val offsets = CharacterOffsets(body)
        val compiledEvents = (source as? EventSource.Compiled)?.events
        val eventPrefix = (source as? EventSource.Numbered)?.prefix ?: ""
        val elements = mutableListOf<GregorianNotationElement>()
        var cursor = 0
        var eventIndex = 0
        // Numbered events give each group that writes a note a syllable.
        var syllableIndex = 0
        var sourceOffset = 0
        // A word begins where a space stands before a group, and a clef or a bar that stands there alone does not
        // use the beginning up: in "mus.(h.) (:)De(g)us" the bar ends one word and "De" begins the next, though no
        // space follows the bar. Notes without words are the melisma of the syllable before them, and leave it a
        // word unfinished.
        var wordStartIsPending = false

        while (true) {
            val open = body.indexOf('(', cursor)
            if (open < 0) break
            val lyricSource = body.substring(cursor, open)
            val close = body.indexOf(')', open)
            if (close < 0) throw GregorianScoreParserError.UnterminatedNotation(offsets[open])

            val notationStart = open + 1
            val notation = body.substring(notationStart, close)
            val groupOffset = notationStart
            val lyric = cleanLyric(lyricSource)
            val parsedLyricStyle = lyricStyle(lyricSource)
            val sourceStartsWord = lyricStartsWord(lyricSource, isFirst = elements.isEmpty())
            val startsWord = sourceStartsWord || (wordStartIsPending && lyric.isNotEmpty())

            val parsed = parseNotationGroup(notation) { offsets[groupOffset + it] }
            val hasNotes = parsed.any { it is ParsedToken.Notes }
            if (lyric.isNotEmpty() || hasNotes) {
                wordStartIsPending = false
            } else if (sourceStartsWord && parsed.isNotEmpty()) {
                wordStartIsPending = true
            }
            if (lyric.isNotEmpty() && !hasNotes) {
                val displayLyric = standaloneLyricText(lyric)
                elements += GregorianNotationElement.LyricMark(
                    GregorianLyricMark(
                        text = displayLyric,
                        style = standaloneLyricStyle(displayLyric, parsedLyricStyle),
                        startsWord = startsWord,
                        sourceRange = GregorianSourceRange(offsets[cursor], offsets[open]),
                    ),
                )
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
                                episemaPosition = note.episemaPosition,
                                liquescence = note.liquescence,
                                isCavum = note.isCavum,
                                hasIctus = note.hasIctus,
                                repetitionCount = note.repetitionCount,
                                sourceRange = note.sourceRange,
                            )
                            eventIndex += 1
                        }

                        val first = mappedNotes.firstOrNull() ?: continue
                        val event = mappedEvents.first()
                        val neumeLyric = if (appliedLyric) "" else lyric
                        if (neumeLyric.isNotEmpty() && compiledEvents != null &&
                            mappedNotes.any { mapped -> compiledEvents.firstOrNull { it.id == mapped.id }?.syllableId != event.syllableId }
                        ) {
                            throw GregorianScoreParserError.EventSyllableMismatch(first.id)
                        }
                        val segments = GregorianNeumeSegmenter.segment(mappedNotes)
                        for ((segmentIndex, segment) in segments.withIndex()) {
                            val segmentFirst = segment.firstOrNull() ?: continue
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
                                ),
                            )
                        }
                        appliedLyric = true
                    }
                }
            }
            if (hasNotes) syllableIndex += 1

            cursor = close + 1
            sourceOffset = offsets[cursor]
        }

        if (compiledEvents != null) {
            if (eventIndex != compiledEvents.size) throw GregorianScoreParserError.TimelineMismatch(compiledEvents.size, eventIndex)
            if (elements.isEmpty() && compiledEvents.isNotEmpty()) throw GregorianScoreParserError.UnsupportedToken("empty score", sourceOffset)
        }
        return GregorianScore(elements)
    }

    private sealed interface ParsedToken {
        class Clef(val clef: GregorianClef) : ParsedToken
        class Accidental(val mark: GregorianAccidentalMark) : ParsedToken
        class Notes(val notes: List<ParsedNote>) : ParsedToken
        class Division(val division: GregorianDivision) : ParsedToken
        data object ForcedBreak : ParsedToken
    }

    private class ParsedNote(
        val pitch: Int,
        val shape: GregorianNoteShape,
        val accidental: GregorianAccidental?,
        val hasMora: Boolean,
        val hasEpisema: Boolean,
        val moraCount: Int,
        val episemaPosition: GregorianEpisemaPosition,
        val liquescence: GregorianLiquescence,
        val isCavum: Boolean,
        val hasIctus: Boolean,
        val repetitionCount: Int,
        val sourceRange: GregorianSourceRange,
    )

    /** Reads one group of notation; [at] gives the offset in the body of a position in the group. */
    private fun parseNotationGroup(characters: String, at: (Int) -> Int): List<ParsedToken> {
        val result = mutableListOf<ParsedToken>()
        val currentNotes = mutableListOf<ParsedNote>()
        var index = 0

        fun flushNotes() {
            if (currentNotes.isNotEmpty()) {
                result += ParsedToken.Notes(currentNotes.toList())
                currentNotes.clear()
            }
        }

        fun isClefAt(at: Int): Boolean {
            val character = characters[at]
            if (character != 'c' && character != 'f') return false
            if (at + 1 < characters.length && isNumber(characters[at + 1])) return true
            return character == 'c' && at + 2 < characters.length && characters[at + 1] == 'b' && isNumber(characters[at + 2])
        }

        while (index < characters.length) {
            val character = characters[index]

            if (character == '[') {
                flushNotes()
                val close = characters.indexOf(']', index + 1)
                index = if (close >= 0) close + 1 else characters.length
                continue
            }
            if (character.isWhitespace() || character == '/') {
                flushNotes()
                index += 1
                continue
            }
            if (character == '!') {
                flushNotes()
                index += 1
                continue
            }

            val isFlatCClef = character == 'c' && index + 2 < characters.length &&
                characters[index + 1] == 'b' && isNumber(characters[index + 2])
            if ((character == 'c' || character == 'f') && index + 1 < characters.length &&
                (isNumber(characters[index + 1]) || isFlatCClef)
            ) {
                flushNotes()
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
                    val close = characters.indexOf(']', index + 1)
                    index = if (close >= 0) close + 1 else characters.length
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
                '<' in suffix || '>' in suffix || '~' in suffix -> GregorianNoteShape.Liquescent
                'v' in suffix || 'V' in suffix -> GregorianNoteShape.Virga
                character.isUpperCase() -> GregorianNoteShape.Inclinatum
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
                episemaPosition = if ("_0" in suffix) GregorianEpisemaPosition.Below else GregorianEpisemaPosition.Above,
                liquescence = liquescence,
                // r0 is a hollow note within lines; r1 to r5 are signs written above the note (an accent, a
                // circle), not a hollow note.
                isCavum = isCavum(suffix),
                hasIctus = '\'' in suffix,
                repetitionCount = if (isCustos) 1 else repeatedNoteCount(suffix),
                sourceRange = GregorianSourceRange(at(noteStart), at(index)),
            )
            if (isCustos || '!' in suffix) flushNotes()
        }

        flushNotes()
        return result
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

    private fun cleanLyric(source: String): String {
        var text = tag.replace(source, "").replace("{", "").replace("}", "")
        // Psalm-tone sources use paired underscores for preparatory syllables and paired asterisks for accented
        // syllables. These delimiters describe pointing and style and are not lyric text. The paired patterns leave
        // a standalone liturgical asterisk unchanged.
        text = preparatory.replace(text, "$1")
        text = accented.replace(text, "$1")
        return text.replace("\n", " ").split(spaces).filter { it.isNotEmpty() }.joinToString(" ").trim()
    }

    private fun lyricStyle(source: String): GregorianLyricStyle {
        val withoutTags = tag.replace(source, "")
        if (Regex("""\*[^*]+\*""").containsMatchIn(withoutTags)) return GregorianLyricStyle.Accented
        if (preparatory.containsMatchIn(withoutTags)) return GregorianLyricStyle.Preparatory
        return GregorianLyricStyle.Regular
    }

    private fun standaloneLyricText(lyric: String): String = when (lyric) {
        "V/." -> "℣."
        "R/." -> "℟."
        else -> lyric
    }

    private fun standaloneLyricStyle(lyric: String, parsedStyle: GregorianLyricStyle): GregorianLyricStyle {
        if (parsedStyle != GregorianLyricStyle.Regular) return parsedStyle
        return if (lyric in setOf("*", "†", "‡", "+", "℣.", "℟.")) GregorianLyricStyle.Rubric else GregorianLyricStyle.Regular
    }

    private fun lyricStartsWord(source: String, isFirst: Boolean): Boolean {
        if (isFirst) return true
        return tag.replace(source, "").firstOrNull()?.isWhitespace() == true
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
        val allStropha = notes.all { it.shape == GregorianNoteShape.Stropha }
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
        if (allEqual && allStropha) return if (writtenCount >= 3) GregorianNeumeForm.Tristropha else GregorianNeumeForm.Distropha
        if (allEqual && allVirga) return if (writtenCount >= 3) GregorianNeumeForm.Trivirga else GregorianNeumeForm.Bivirga
        if (allEqual) return if (writtenCount >= 3) GregorianNeumeForm.Tristropha else GregorianNeumeForm.Distropha
        if (notes.size == 2) {
            if (directions[0] > 0) {
                return if (first.shape == GregorianNoteShape.Oriscus) GregorianNeumeForm.PesQuassus else GregorianNeumeForm.Podatus
            }
            return if (directions[0] < 0) GregorianNeumeForm.Clivis else GregorianNeumeForm.Punctum
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
        if (directions.first() == 1 && directions.drop(1).all { it < 0 }) return GregorianNeumeForm.PesSubpunctis
        return GregorianNeumeForm.Punctum
    }

    private fun direction(from: Int, to: Int): Int = if (to > from) 1 else if (to < from) -1 else 0
}

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
                length = minOf(3, repeatedPitchCount(notes, index, first.pitch))
            }
            result += notes.subList(index, index + length)
            index += length
        }
        return result
    }

    private fun direction(from: Int, to: Int): Int = if (to > from) 1 else if (to < from) -1 else 0

    private fun repeatedShapeCount(notes: List<GregorianNote>, index: Int, shape: GregorianNoteShape, pitch: Int, maximum: Int): Int {
        var count = 0
        while (index + count < notes.size && count < maximum && notes[index + count].shape == shape &&
            notes[index + count].pitch == pitch
        ) {
            count += 1
        }
        return maxOf(1, count)
    }

    private fun repeatedPitchCount(notes: List<GregorianNote>, index: Int, pitch: Int): Int {
        var count = 0
        while (index + count < notes.size && notes[index + count].pitch == pitch) count += 1
        return maxOf(1, count)
    }
}
