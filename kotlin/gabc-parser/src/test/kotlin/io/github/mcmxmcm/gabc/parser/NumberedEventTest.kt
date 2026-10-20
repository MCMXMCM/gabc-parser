package io.github.mcmxmcm.gabc.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

/**
 * Parsing without a compiled timeline: the parser names each note and syllable itself, as the content compiler
 * names a timeline's events.
 */
class NumberedEventTest {
    @Test
    fun namesNotesInOrderAndGivesEachNotatedGroupASyllable() {
        // A forced break and a clef write no note, and take no syllable.
        val score = GregorianScoreParser.parse(
            "name: clefs; %% (c3) Al(dfe)le(g) (z) (f3) lú(hi)ia.(g.) (::)",
            "clefs",
        )

        assertEquals((0 until 7).map { "clefs-note-$it" }, score.eventIds)
        assertEquals(
            listOf("clefs-syllable-0", "clefs-syllable-1", "clefs-syllable-2", "clefs-syllable-3"),
            score.neumes.map { it.syllableId },
        )
        assertEquals(listOf("Al", "le", "lú", "ia."), score.neumes.map { it.lyric })
    }

    @Test
    fun namesAreUnprefixedWithoutAScoreIdentifier() {
        val score = GregorianScoreParser.parse("%% (c4) Ky(fg)ri(h) (::)")

        assertEquals(listOf("note-0", "note-1", "note-2"), score.eventIds)
        assertEquals(listOf("note-0", "note-2"), score.neumes.map { it.id })
        assertEquals(listOf("syllable-0", "syllable-1"), score.neumes.map { it.syllableId })
    }

    @Test
    fun lyricMarksAreNamedAfterTheirScore() {
        // Two scores of one document keep the names of their marks apart, as they keep the names of their notes.
        val gabc = "%% (c4) *() a(g) (::)"
        fun marks(score: GregorianScore) = score.elements.mapNotNull { (it as? GregorianNotationElement.LyricMark)?.mark?.id }

        assertEquals(listOf("one-lyric-mark-5-7"), marks(GregorianScoreParser.parse(gabc, "one")))
        assertEquals(listOf("two-lyric-mark-5-7"), marks(GregorianScoreParser.parse(gabc, "two")))
        assertEquals(listOf("lyric-mark-5-7"), marks(GregorianScoreParser.parse(gabc)))
    }

    @Test
    fun relativePitchCountsStaffStepsFromH() {
        val score = GregorianScoreParser.parse("%% (c4) a(h) b(a) c(m) d(G) (::)")

        assertEquals(
            listOf(0, -7, 5, -1),
            score.neumes.flatMap { it.notes }.map { it.relativePitch },
        )
        assertEquals(listOf(7, 0, 12, 6), score.neumes.flatMap { it.notes }.map { it.pitch })
    }

    @Test
    fun melismaGroupWithoutLyricIsASyllableOfItsOwn() {
        // As the compiler names it, the melisma written after "Ky" in a group of its own is a syllable of its own,
        // and carries no lyric.
        val score = GregorianScoreParser.parse(
            "%% (c4) Ky(f)(gh)ri(g)e(h) (::)",
            "k",
        )

        assertEquals(listOf("Ky", "", "ri", "e"), score.neumes.map { it.lyric })
        assertEquals(
            listOf("k-syllable-0", "k-syllable-1", "k-syllable-2", "k-syllable-3"),
            score.neumes.map { it.syllableId },
        )
        assertEquals("Kyrie", score.lyricText)
    }

    @Test
    fun writtenCustosIsAnEventAndAccidentalIsNot() {
        val custos = GregorianScoreParser.parse(
            "%% (c3) tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)",
        )
        assertEquals(5, custos.eventIds.size)
        assertEquals(listOf(1, 1, 0, 1, 1), custos.neumes.map { it.writtenNoteCount })
        assertEquals("syllable-2", custos.neumes[2].syllableId)

        val accidental = GregorianScoreParser.parse("%% (c4) Red(h)e(h)mí(ixhi)sti(h) (::)")
        assertEquals(5, accidental.eventIds.size)
        assertEquals(
            listOf(
                GregorianNeumeForm.Punctum,
                GregorianNeumeForm.Punctum,
                GregorianNeumeForm.Podatus,
                GregorianNeumeForm.Punctum,
            ),
            accidental.neumes.map { it.form },
        )
    }

    @Test
    fun sharedFixturesNumberAsTheirCompiledTimelines() {
        for (fixture in SharedFixture.all()) {
            val numbered = GregorianScoreParser.parse(
                fixture.gabc,
                fixture.scoreId,
            )
            assertEquals(
                fixture.name,
                (0 until fixture.eventCount).map { "${fixture.scoreId}-note-$it" },
                numbered.eventIds,
            )
            assertEquals(fixture.name, fixture.neumeSizes, numbered.neumes.map { it.notes.size })

            // The timeline the compiler gives these scores names their notes and syllables the same: parsed against
            // it, the score is the same.
            val pitches = numbered.neumes.flatMap { it.notes }.map { it.relativePitch }
            val events = mutableListOf<GregorianEvent>()
            for ((syllableIndex, neumeSize) in fixture.neumeSizes.withIndex()) {
                repeat(neumeSize) {
                    events += GregorianEvent(
                        id = "${fixture.scoreId}-note-${events.size}",
                        syllableId = "${fixture.scoreId}-syllable-$syllableIndex",
                        relativePitch = pitches[events.size],
                    )
                }
            }
            val compiled = GregorianScoreParser.parse(
                fixture.gabc,
                GregorianEventTimeline(events),
            )
            assertEquals(fixture.name, numbered, compiled)
        }
    }

    @Test
    fun notationWithoutAHeaderIsReadAsTheBody() {
        val body = "(c4) Ky(f)ri(g)e(h) e(f)le(g)i(h)son.(g.) (::)"
        val withHeader = GregorianScoreParser.parse("name: Kyrie; mode: 6;\n%%$body")
        val withoutHeader = GregorianScoreParser.parse(body)

        assertEquals(withHeader, withoutHeader)
        assertEquals("Kyrie eleison.", withoutHeader.lyricText)
    }

    @Test
    fun emptyNotationIsAnEmptyScore() {
        assertEquals(GregorianScore(emptyList()), GregorianScoreParser.parse(""))
        assertEquals(GregorianScore(emptyList()), GregorianScoreParser.parse("name: empty;\n%%\n"))
    }

    @Test
    fun malformedNotationStillFails() {
        val unterminated = assertThrows(GregorianScoreParserError::class.java) {
            GregorianScoreParser.parse("%% (c4) Ky(f")
        }
        if (unterminated !is GregorianScoreParserError.UnterminatedNotation) {
            fail("Unexpected error: $unterminated")
        }
        val unsupported = assertThrows(GregorianScoreParserError::class.java) {
            GregorianScoreParser.parse("%% (c4) Ky(f?)")
        }
        if (unsupported !is GregorianScoreParserError.UnsupportedToken || unsupported.token != "?") {
            fail("Unexpected error: $unsupported")
        }
        val invalidClef = assertThrows(GregorianScoreParserError::class.java) {
            GregorianScoreParser.parse("%% (c5) Ky(f)")
        }
        if (invalidClef !is GregorianScoreParserError.InvalidClef || invalidClef.token != "c5") {
            fail("Unexpected error: $invalidClef")
        }
    }

    @Test
    fun anyTimelineTypeCanNameTheNotes() {
        data class Note(
            override val id: String,
            override val syllableId: String,
            override val relativePitch: Int,
        ) : GregorianTimelineEvent

        data class Timeline(
            override val events: List<Note>,
        ) : GregorianTimeline<Note>

        val score = GregorianScoreParser.parse(
            "%% (c4) Ky(fg)ri(h) (::)",
            Timeline(
                events = listOf(
                    Note(id = "a", syllableId = "ky", relativePitch = -2),
                    Note(id = "b", syllableId = "ky", relativePitch = -1),
                    Note(id = "c", syllableId = "ri", relativePitch = 0),
                ),
            ),
        )

        assertEquals(listOf("a", "b", "c"), score.eventIds)
        assertEquals(listOf("ky", "ri"), score.neumes.map { it.syllableId })
    }
}
