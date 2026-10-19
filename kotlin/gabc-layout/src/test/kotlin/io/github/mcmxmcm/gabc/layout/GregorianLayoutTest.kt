package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianEvent
import io.github.mcmxmcm.gabc.parser.GregorianEventTimeline
import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle
import io.github.mcmxmcm.gabc.parser.GregorianScore
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

// A port of the Swift package's Tests/GABCLayoutTests/GregorianLayoutTests.swift.
class GregorianLayoutTest {
    @Test
    fun testNotationAndLyricsScaleTogetherAcrossReaderRange() {
        val score = parsedFixture(noteCount = 1)
        val engine = GregorianEngravingLayoutEngine()
        val previousMinimum = engine.layout(
            score,
            width = 390f,
            metrics = GregorianLayoutMetrics(notationScale = 0.8),
        )
        val newMinimum = engine.layout(
            score,
            width = 390f,
            metrics = GregorianLayoutMetrics(
                notationScale = GregorianLayoutMetrics.MINIMUM_NOTATION_SCALE,
            ),
        )
        val clampedBelowMinimum = engine.layout(
            score,
            width = 390f,
            metrics = GregorianLayoutMetrics(notationScale = 0.0),
        )
        val defaultSize = engine.layout(
            score,
            width = 390f,
        )
        val maximumSize = engine.layout(
            score,
            width = 390f,
            metrics = GregorianLayoutMetrics(
                notationScale = GregorianLayoutMetrics.MAXIMUM_NOTATION_SCALE,
            ),
        )
        val previousClef = unwrap(previousMinimum.glyphs.firstOrNull())
        val minimumClef = unwrap(newMinimum.glyphs.firstOrNull())
        val clampedClef = unwrap(clampedBelowMinimum.glyphs.firstOrNull())
        val previousLyric = unwrap(previousMinimum.lyrics.firstOrNull())
        val minimumLyric = unwrap(newMinimum.lyrics.firstOrNull())
        val clampedLyric = unwrap(clampedBelowMinimum.lyrics.firstOrNull())
        val defaultClef = unwrap(defaultSize.glyphs.firstOrNull())
        val maximumClef = unwrap(maximumSize.glyphs.firstOrNull())
        val defaultLyric = unwrap(defaultSize.lyrics.firstOrNull())
        val maximumLyric = unwrap(maximumSize.lyrics.firstOrNull())

        assertEquals(
            previousClef.frame.width * 0.75f,
            minimumClef.frame.width,
            0.001f,
        )
        assertEquals(
            minimumClef.frame.width,
            clampedClef.frame.width,
            0.001f,
        )
        assertEquals(
            previousLyric.fontSize * 0.75f,
            minimumLyric.fontSize,
            0.001f,
        )
        assertEquals(
            minimumLyric.fontSize,
            clampedLyric.fontSize,
            0.001f,
        )
        assertEquals(
            defaultClef.frame.width
                * GregorianLayoutMetrics.MAXIMUM_NOTATION_SCALE.toFloat(),
            maximumClef.frame.width,
            0.001f,
        )
        assertEquals(
            defaultLyric.fontSize
                * GregorianLayoutMetrics.MAXIMUM_NOTATION_SCALE.toFloat(),
            maximumLyric.fontSize,
            0.001f,
        )
    }

    @Test
    fun testGeneratedCatalogContainsCanonicalExsurgeGeometry() {
        assertEquals(49, GregorianGlyphName.entries.size)
        val punctum = GregorianGlyphCatalog.definition(GregorianGlyphName.PunctumQuadratum)
        assertEquals(100f, punctum.bounds.width, 0.001f)
        assertEquals(123.438f, punctum.bounds.height, 0.001f)
        assertEquals(50f, punctum.origin.x, 0.001f)
        assertFalse(punctum.paths.flatten().isEmpty())

        for (name in GregorianGlyphName.entries.filter { it != GregorianGlyphName.None }) {
            assertFalse(
                "${name.name} should have generated vector commands",
                GregorianGlyphCatalog.definition(name).paths.flatten().isEmpty(),
            )
        }
    }

    @Test
    fun testNamedFormsSelectCanonicalCompositeGlyphs() {
        val events = listOf(
            GregorianEvent(id = "p0", syllableId = "s0", relativePitch = 0),
            GregorianEvent(id = "p1", syllableId = "s0", relativePitch = 1),
            GregorianEvent(id = "r0", syllableId = "s1", relativePitch = 2),
            GregorianEvent(id = "r1", syllableId = "s1", relativePitch = 1),
            GregorianEvent(id = "r2", syllableId = "s1", relativePitch = 2),
        )
        val score = GregorianScoreParser.parse(
            "name: composite; %% (c4) Po(fg) rec(hgh) (::)",
            GregorianEventTimeline(events),
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        val names = layout.glyphs.map { it.kind.catalogName }

        assertTrue(names.contains(GregorianGlyphName.PodatusLower))
        assertTrue(names.contains(GregorianGlyphName.PodatusUpper))
        assertTrue(names.contains(GregorianGlyphName.Porrectus1))
    }

    @Test
    fun testResponsiveLayoutProducesCustodesAndStableEventFrames() {
        val score = parsedFixture(noteCount = 12)
        val engine = GregorianEngravingLayoutEngine()
        val narrow = engine.layout(score, width = 280f)
        val wide = engine.layout(score, width = 760f)

        assertGreaterThan(narrow.staffs.size, wide.staffs.size)
        assertLessThanOrEqual(narrow.staffs.size, 3)
        assertEquals(score.eventIds, narrow.events.map { it.eventId })
        assertEquals(score.eventIds.size, narrow.events.map { it.eventId }.toSet().size)
        assertEquals(
            max(0, narrow.staffs.size - 1),
            narrow.glyphs.filter { it.kind == GregorianGlyphKind.Custos }.size,
        )
        val countsByLine = narrow.neumes.groupBy { it.lineIndex }
            .entries
            .sortedBy { it.key }
            .map { it.value.size }
        assertGreaterThanOrEqual(countsByLine.dropLast(1).minOrNull() ?: 0, 3)
        assertEquals(
            narrow.staffs.size,
            narrow.glyphs.filter { it.kind == GregorianGlyphKind.CClef }.size,
        )
    }

    @Test
    fun testMobileStaffsUseFullWidthAndCompactVerticalRhythm() {
        val noteCount = 40
        val timeline = GregorianEventTimeline(
            (0 until noteCount).map { index ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = 0,
                )
            },
        )
        val body = (0 until noteCount)
            .map { "la(f)" }
            .joinToString(" ")
        val score = GregorianScoreParser.parse(
            "name: compact-mobile; %% (c4) $body (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)

        assertGreaterThan(layout.staffs.size, 1)
        assertTrue(layout.initials.isEmpty())
        for (staff in layout.staffs) {
            assertEquals(8f, staff.frame.left, 0.01f)
            assertEquals(382f, staff.frame.right, 0.01f)
        }

        for (pair in layout.staffs.zip(layout.staffs.drop(1))) {
            val systemAdvance = pair.second.frame.top - pair.first.frame.top
            assertGreaterThan(systemAdvance, 79f)
            assertLessThan(systemAdvance, 82f)
        }
    }

    @Test
    fun testOpeningInitialWithConclusionSitsLeftOfTheFirstClefAndLeavesLaterStaffsFullWidth() {
        val syllables = listOf("Cla", "ma", "vé", "runt", "iu", "sti")
        val timeline = GregorianEventTimeline(
            syllables.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = index % 4,
                )
            },
        )
        val score = GregorianScoreParser.parse(
            "name: initial; %% (c4) Cla(f)ma(g)vé(h)runt(i) (z) iu(j)sti(i) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(
            score,
            width = 390f,
            openingLabel = "8g",
        )
        val initial = unwrap(layout.initials.firstOrNull())
        val firstLyric = unwrap(layout.lyrics.firstOrNull())

        assertEquals(1, layout.initials.size)
        assertEquals("C", initial.text)
        assertEquals(score.neumes[0].id, initial.neumeId)
        assertEquals("la", firstLyric.text)
        assertEquals("Cla", score.neumes[0].lyric)
        assertGreaterThan(layout.staffs[0].frame.left, 8f)
        assertLessThanOrEqual(
            initial.origin.x + initial.width,
            layout.staffs[0].frame.left,
        )
        assertEquals(8f, layout.staffs[1].frame.left, 0.01f)
        assertEquals(382f, layout.staffs[1].frame.right, 0.01f)
    }

    @Test
    fun testOpeningInitialPreservesLatinLigatureAsOneCharacter() {
        val timeline = GregorianEventTimeline(
            listOf(
                GregorianEvent(
                    id = "event-0",
                    syllableId = "syllable-0",
                    relativePitch = 0,
                ),
                GregorianEvent(
                    id = "event-1",
                    syllableId = "syllable-1",
                    relativePitch = 1,
                ),
            ),
        )
        val score = GregorianScoreParser.parse(
            "name: ligature; %% (c4) 1. Æ(f)tér(g) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(
            score,
            width = 390f,
            openingLabel = "1f",
        )

        assertEquals(listOf("Æ"), layout.initials.map { it.text })
        assertEquals(listOf("1F"), layout.initials.map { it.annotation })
        assertFalse(layout.lyrics.any { it.text == "Æ" })
    }

    @Test
    fun testOpeningWithoutConclusionKeepsCompleteLyricUnderClef() {
        val timeline = GregorianEventTimeline(
            listOf(
                GregorianEvent(
                    id = "event-0",
                    syllableId = "syllable-0",
                    relativePitch = 0,
                ),
                GregorianEvent(
                    id = "event-1",
                    syllableId = "syllable-1",
                    relativePitch = 1,
                ),
            ),
        )
        val score = GregorianScoreParser.parse(
            "name: no-conclusion; %% (c4) De(f)us(g) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(
            score,
            width = 390f,
            openingLabel = "6",
        )

        assertTrue(layout.initials.isEmpty())
        assertEquals("De", layout.lyrics.firstOrNull()?.text)
        assertEquals(8f, layout.staffs[0].frame.left, 0.01f)
    }

    @Test
    fun testOpeningLabelRequiresModeAndConclusion() {
        assertNull(GregorianEngravingLayoutEngine.openingLabel(null))
        assertNull(GregorianEngravingLayoutEngine.openingLabel("6"))
        assertNull(GregorianEngravingLayoutEngine.openingLabel("6-alt"))
        assertNull(GregorianEngravingLayoutEngine.openingLabel("in-directum"))
        assertEquals(
            "8G",
            GregorianEngravingLayoutEngine.openingLabel("8g"),
        )
        assertEquals(
            "7C2",
            GregorianEngravingLayoutEngine.openingLabel("7c2"),
        )
        assertEquals(
            "4-ALT-D",
            GregorianEngravingLayoutEngine.openingLabel("4-alt-d"),
        )
    }

    @Test
    fun testScoreModeAppearsAboveInitialAndOverridesVerseNumber() {
        val timeline = GregorianEventTimeline(
            listOf(
                GregorianEvent(
                    id = "event-0",
                    syllableId = "syllable-0",
                    relativePitch = 0,
                ),
                GregorianEvent(
                    id = "event-1",
                    syllableId = "syllable-1",
                    relativePitch = 1,
                ),
            ),
        )
        val score = GregorianScoreParser.parse(
            "name: psalm-tone; %% (c3) 1. Prín(f)cipes(g) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(
            score,
            width = 390f,
            openingLabel = "7c2",
        )
        val initial = unwrap(layout.initials.firstOrNull())

        assertEquals("P", initial.text)
        assertEquals("7C2", initial.annotation)
        assertNotNull(initial.annotationOrigin)
    }

    @Test
    fun testNeumeTargetsRemainUsableAndDoNotOverlap() {
        val score = parsedFixture(noteCount = 9)
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)

        for (neume in layout.neumes) {
            assertGreaterThanOrEqual(neume.hitFrame.width, 23.9f)
            assertGreaterThanOrEqual(neume.hitFrame.height, 43.9f)
            // Swift's `XCTAssertFalse(neume.inkFrame.isNull)`: where Swift's ink frame stays CGRect.null (nothing was
            // drawn), the Kotlin port gives GregorianRect.Zero.
            assertNotEquals(GregorianRect.Zero, neume.inkFrame)
        }
        for (line in layout.neumes.groupBy { it.lineIndex }.values) {
            val ordered = line.sortedBy { it.hitFrame.left }
            for (pair in ordered.zip(ordered.drop(1))) {
                assertLessThanOrEqual(pair.first.hitFrame.right, pair.second.hitFrame.left + 0.01f)
            }
        }
    }

    @Test
    fun testLyricsAreMeasuredAndDoNotOverlapOnEachLine() {
        val score = parsedFixture(noteCount = 9)
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        for (staff in layout.staffs) {
            val lyrics = layout.lyrics
                .filter { lyric -> lyric.origin.y >= staff.frame.top && lyric.origin.y < staff.frame.top + 94 }
                .sortedBy { it.origin.x }
            for (pair in lyrics.zip(lyrics.drop(1))) {
                assertLessThanOrEqual(pair.first.origin.x + pair.first.width, pair.second.origin.x + 0.5f)
            }
        }
    }

    @Test
    fun testAccentedLyricReservesItsBoldAdvanceBeforeFollowingSyllable() {
        val syllables = listOf("con", "fun", "*dán*", "tur")
        val timeline = GregorianEventTimeline(
            syllables.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = index,
                )
            },
        )
        val accentedScore = GregorianScoreParser.parse(
            "name: accented-spacing; %% (c4) con(f)fun(g)*dán*(h)tur(i) (::)",
            timeline,
        )
        val regularScore = GregorianScoreParser.parse(
            "name: regular-spacing; %% (c4) con(f)fun(g)dán(h)tur(i) (::)",
            timeline,
        )
        val engine = GregorianEngravingLayoutEngine()
        val accentedLayout = engine.layout(accentedScore, width = 760f)
        val regularLayout = engine.layout(regularScore, width = 760f)
        val accentedLyric = unwrap(
            accentedLayout.lyrics.firstOrNull { it.text == "dán" },
        )
        val regularLyric = unwrap(
            regularLayout.lyrics.firstOrNull { it.text == "dán" },
        )
        val followingLyric = unwrap(
            accentedLayout.lyrics.firstOrNull { it.text == "tur" },
        )

        assertEquals(GregorianLyricStyle.Accented, accentedLyric.style)
        assertGreaterThan(accentedLyric.width, regularLyric.width)
        assertLessThanOrEqual(
            accentedLyric.origin.x + accentedLyric.width,
            followingLyric.origin.x + 0.5f,
        )
    }

    @Test
    fun testSyllablesStayGroupedWithinWords() {
        val syllables = listOf("San", "cte", "no", "bis", "Spí", "ri", "tus")
        val timeline = GregorianEventTimeline(
            syllables.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = index % 4,
                )
            },
        )
        val score = GregorianScoreParser.parse(
            "name: word-spacing; %% (c4) San(f)cte(g) no(h)bis(i) Spí(j)ri(i)tus(h) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)
        val lyricsById = uniqueKeysWithValues(layout.lyrics.map { it.neumeId to it })
        val neumes = score.neumes

        fun gap(after: Int, before: Int): Float {
            val first = unwrap(lyricsById[neumes[after].id])
            val second = unwrap(lyricsById[neumes[before].id])
            return second.origin.x - (first.origin.x + first.width)
        }

        assertLessThanOrEqual(gap(after = 0, before = 1), 2f)
        assertGreaterThanOrEqual(gap(after = 1, before = 2), 4f)
        assertLessThanOrEqual(gap(after = 1, before = 2), 7f)
        assertLessThanOrEqual(gap(after = 2, before = 3), 2f)
        assertGreaterThanOrEqual(gap(after = 3, before = 4), 4f)
        assertLessThanOrEqual(gap(after = 3, before = 4), 7f)
        assertLessThanOrEqual(gap(after = 4, before = 5), 2f)
        assertLessThanOrEqual(gap(after = 5, before = 6), 2f)
    }

    @Test
    fun testAutomaticHyphensClarifyWideSyllableGapsWithinAWord() {
        val syllables = listOf("De", "us", "in")
        val timeline = GregorianEventTimeline(
            syllables.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = index,
                )
            },
        )
        val score = GregorianScoreParser.parse(
            "name: hyphens; %% (c3) De(h)us(h) in(h) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)
        val lyricTexts = layout.lyrics.map { it.text }

        assertTrue(lyricTexts.contains("-"))
        assertTrue(
            layout.lyrics.any {
                it.neumeId == "lyric-hyphen-event-0-event-1"
            },
        )
        val firstSyllable = unwrap(
            layout.lyrics.firstOrNull { it.neumeId == "event-0" },
        )
        val hyphen = unwrap(
            layout.lyrics.firstOrNull {
                it.neumeId == "lyric-hyphen-event-0-event-1"
            },
        )
        assertEquals(
            "The smaller hyphen should be vertically centered in the lyric line.",
            firstSyllable.origin.y
                + (firstSyllable.fontSize - hyphen.fontSize) * 1.23f / 2,
            hyphen.origin.y,
            0.001f,
        )
        assertFalse(
            "A GABC word space must not receive a lyric hyphen.",
            layout.lyrics.any {
                it.neumeId == "lyric-hyphen-event-1-event-2"
            },
        )
    }

    @Test
    fun testNearbyGABCFragmentsJoinWithoutAutomaticHyphens() {
        val fragments = listOf("Qui", "fe", "ci", "t cæ", "lu", "m e", "t te", "rram.")
        val timeline = GregorianEventTimeline(
            fragments.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = if (index == fragments.size - 1) -2 else 0,
                )
            },
        )
        val score = GregorianScoreParser.parse(
            "name: compact-fragments; %% (c3) R/.() Qui(h) fe(h)ci(h)t cæ(h)lu(h)m e(h)t te(h)rram.(f.) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)

        assertFalse(
            "Adjacent musical attachments should read as continuous words, including et rather than e-t.",
            layout.lyrics.any { it.text == "-" },
        )
        val fragmentBeforeT = unwrap(
            layout.lyrics.firstOrNull { it.neumeId == "event-5" },
        )
        val fragmentBeginningWithT = unwrap(
            layout.lyrics.firstOrNull { it.neumeId == "event-6" },
        )
        assertLessThanOrEqual(
            fragmentBeginningWithT.origin.x
                - (fragmentBeforeT.origin.x + fragmentBeforeT.width),
            2f,
            "The e and t in et should remain visually joined.",
        )
    }

    @Test
    fun testReferencePrecesUseConnectorsOnlyWhereMusicalSpacingNeedsThem() {
        val fragments = listOf(
            "De", "us", "in", "ad", "ju", "tó", "ri", "um", "me", "um",
            "in", "tén", "de.",
        )
        val timeline = GregorianEventTimeline(
            fragments.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = 0,
                )
            },
        )
        val score = GregorianScoreParser.parse(
            "name: reference-preces; %% (c3) V/.() De(h)us(h) ✠(,) in(h) ad(h)ju(h)tó(i)ri(h)um(h) me(h)um(h) in(h)tén(g)de.(h.) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)
        val actualHyphenIds = layout.lyrics.filter { it.text == "-" }.map { it.neumeId }.toSet()

        assertEquals(
            "Match Exsurge's De-us, adjutóri-um, and me-um connectors without hyphenating every syllable.",
            setOf(
                "lyric-hyphen-event-0-event-1",
                "lyric-hyphen-event-6-event-7",
                "lyric-hyphen-event-8-event-9",
            ),
            actualHyphenIds,
        )

        val adjutoriumFragments = listOf(
            "A", "dju", "tó", "ri", "u", "m nó", "strum", "i", "n nó",
            "mi", "ne", "Dó", "mi", "ni.",
        )
        val adjutoriumTimeline = GregorianEventTimeline(
            adjutoriumFragments.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "adjutorium-event-$index",
                    syllableId = "adjutorium-syllable-$index",
                    relativePitch = 0,
                )
            },
        )
        val adjutoriumScore = GregorianScoreParser.parse(
            "name: reference-adjutorium; %% (c3) V/.() A(h)dju(h)tó(h)ri(h)u(h)m nó(h)strum(h) ✠(,) i(h)n nó(h)mi(h)ne(h) Dó(h)mi(h)ni.(f.) (::)",
            adjutoriumTimeline,
        )
        val adjutoriumLayout = GregorianEngravingLayoutEngine().layout(
            adjutoriumScore,
            width = 760f,
        )

        assertEquals(
            "Match Exsurge's single adjutóri-um connector.",
            setOf("lyric-hyphen-adjutorium-event-3-adjutorium-event-4"),
            adjutoriumLayout.lyrics.filter { it.text == "-" }.map { it.neumeId }.toSet(),
        )
    }

    @Test
    fun testReferenceDoxologyRetainsItsSelectiveConnectors() {
        val fragments = listOf(
            "Gló", "ri", "a", "Pa", "tri,", "et", "Fí", "li", "o,", "et",
            "Spi", "rí", "tu", "i", "San", "cto.", "Sic", "ut", "e", "rat",
            "in", "prin", "cí", "pi", "o,", "et", "nunc,",
        )
        val timeline = GregorianEventTimeline(
            fragments.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = 0,
                )
            },
        )
        val score = GregorianScoreParser.parse(
            "name: reference-doxology; %% (c3) Gló(h)ri(h)a(h) Pa(h)tri,(h) et(h) Fí(h)li(h)o,(h) et(h) Spi(h)rí(h)tu(h)i(h) San(g)cto.(h.) (:) Sic(h)ut(h) e(h)rat(h) in(h) prin(h)cí(h)pi(h)o,(h) et(h) nunc,(h) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)
        val actualHyphenIds = layout.lyrics.filter { it.text == "-" }.map { it.neumeId }.toSet()
        val expectedReferenceConnectors = setOf(
            "lyric-hyphen-event-1-event-2", // Glóri-a
            "lyric-hyphen-event-6-event-7", // Fí-li-o
            "lyric-hyphen-event-7-event-8",
            "lyric-hyphen-event-12-event-13", // Spíritu-i
            "lyric-hyphen-event-23-event-24", // princípi-o
        )

        assertTrue(
            "Missing reference connectors: ${expectedReferenceConnectors - actualHyphenIds}",
            actualHyphenIds.containsAll(expectedReferenceConnectors),
        )
        assertFalse(
            "Glóri should remain joined before the selective ri-a connector.",
            actualHyphenIds.contains("lyric-hyphen-event-0-event-1"),
        )
        assertFalse(
            "The compact e and rat fragments should read as erat, matching the reference engraving.",
            actualHyphenIds.contains("lyric-hyphen-event-18-event-19"),
        )
    }

    @Test
    fun testAutomaticHyphenMarksAWordContinuedOnTheNextStaff() {
        val syllables = listOf(
            "Su", "per", "ca", "li", "fra", "gi", "lis", "ti",
            "ce", "ex", "pi", "a", "li", "do", "cious",
        )
        val timeline = GregorianEventTimeline(
            syllables.mapIndexed { index, _ ->
                GregorianEvent(
                    id = "event-$index",
                    syllableId = "syllable-$index",
                    relativePitch = index % 5,
                )
            },
        )
        val body = syllables.zip("fghijifghijifgh".toList())
            .map { "${it.first}(${it.second})" }
            .joinToString("")
        val score = GregorianScoreParser.parse(
            "name: line-end-hyphen; %% (c4) $body (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 240f)
        val neumesById = uniqueKeysWithValues(layout.neumes.map { it.id to it })
        val splitPair = unwrap(
            score.neumes.zip(score.neumes.drop(1)).firstOrNull { (previous, next) ->
                val previousPlacement = neumesById[previous.id]
                val nextPlacement = neumesById[next.id]
                if (previousPlacement == null || nextPlacement == null) {
                    false
                } else {
                    previousPlacement.lineIndex != nextPlacement.lineIndex &&
                        !next.startsWord
                }
            },
        )

        val precedingLyric = unwrap(
            layout.lyrics.firstOrNull { it.neumeId == splitPair.first.id },
        )
        val hyphen = unwrap(
            layout.lyrics.firstOrNull {
                it.neumeId ==
                    "lyric-hyphen-${splitPair.first.id}-${splitPair.second.id}"
            },
        )
        assertEquals(
            "A line-end hyphen should be vertically centered in the lyric line.",
            precedingLyric.origin.y
                + (precedingLyric.fontSize - hyphen.fontSize) * 1.23f / 2,
            hyphen.origin.y,
            0.001f,
        )
    }

    @Test
    fun testLedgerLinesAppearForNotesOutsideTheStaff() {
        val timeline = GregorianEventTimeline(
            listOf(
                GregorianEvent(
                    id = "low",
                    syllableId = "s1",
                    relativePitch = -7,
                ),
                GregorianEvent(
                    id = "high",
                    syllableId = "s2",
                    relativePitch = 7,
                ),
            ),
        )
        val score = GregorianScoreParser.parse(
            "name: ledger; %% (c4) Low(a) high(m) (::)",
            timeline,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        assertTrue(
            layout.strokes.any { it.kind == GregorianStrokeKind.Ledger },
        )
    }

    @Test
    fun testWrittenCustosIsDrawnAsACustosAndOffersNoPlaceToBeginSinging() {
        val score = parsedCustosFixture(
            "tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)",
            custosIndex = 2,
            eventCount = 5,
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        val custosEventId = score.eventIds[2]

        // The event keeps its place in the engraving,
        assertEquals(score.eventIds, layout.events.map { it.eventId })
        // but is drawn as a custos, and not as a note the guide may light.
        val custodes = custosGlyphs(layout)
        assertEquals(1, custodes.size)
        val custos = unwrap(custodes.firstOrNull())
        assertNull(custos.eventId)
        assertFalse(layout.glyphs.any { it.eventId == custosEventId })
        assertFalse(layout.strokes.any { it.eventId == custosEventId })
        val placement = unwrap(layout.events.firstOrNull { it.eventId == custosEventId })
        assertTrue(custos.frame in placement.frame)

        // It stands at its own pitch: that of the note before it, here.
        val glyphScale = 1f / 16
        val note = unwrap(
            layout.glyphs.firstOrNull {
                it.eventId == score.eventIds[1] &&
                    it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.PunctumQuadratum)
            },
        )
        assertEquals(
            note.frame.top
                + GregorianGlyphCatalog.definition(GregorianGlyphName.PunctumQuadratum).origin.y * glyphScale,
            custos.frame.top
                + GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort).origin.y * glyphScale,
            0.001f,
        )
        assertGreaterThan(custos.frame.left, note.frame.right)

        // The cantor guide cannot begin from it.
        assertEquals(
            listOf(0, 1, 3, 4).map { score.eventIds[it] },
            layout.neumes.map { it.id },
        )
        assertNull(layout.neumeContaining(custosEventId))
        assertTrue(layout.neumes.all { it.noteCount == 1 })
    }

    @Test
    fun testStaffEndingWithAWrittenCustosIsNotGivenAnother() {
        val engine = GregorianEngravingLayoutEngine()
        val written = parsedCustosFixture(
            "tó(h)ris.(f.) (f+::z) (c4) O(h)rémus.(h.) (::)",
            custosIndex = 2,
            eventCount = 5,
        )
        val layout = engine.layout(written, width = 390f)
        assertEquals(2, layout.staffs.size)
        val custodes = custosGlyphs(layout)
        assertEquals(1, custodes.size)
        // The one custos is the written one, in its place after the note.
        val placement = unwrap(layout.events.firstOrNull { it.eventId == written.eventIds[2] })
        assertEquals(0, placement.lineIndex)
        assertTrue(unwrap(custodes.firstOrNull()).frame in placement.frame)

        // Without one, the staff is given its own as before.
        val unwritten = parsedCustosFixture(
            "tó(h)ris.(f.) (::z) (c4) O(h)rémus.(h.) (::)",
            custosIndex = null,
            eventCount = 4,
        )
        val plain = engine.layout(unwritten, width = 390f)
        assertEquals(2, plain.staffs.size)
        assertEquals(1, custosGlyphs(plain).size)
    }

    @Test
    fun testWrittenCustosStaysOnTheStaffOfTheNoteItFollows() {
        val syllables = (0 until 14).map { "syl$it(h)" }.joinToString(" ")
        val score = parsedCustosFixture(
            "$syllables end.(f.) (f+::) (c4) O(h)ré(g)mus.(h.) (::)",
            custosIndex = 15,
            eventCount = 19,
        )
        val engine = GregorianEngravingLayoutEngine()
        for (width in (240..640 step 5).map { it.toFloat() }) {
            val layout = engine.layout(score, width = width)
            val lines = uniqueKeysWithValues(
                layout.events.map { it.eventId to it.lineIndex },
            )
            assertEquals(
                "width $width",
                lines[score.eventIds[14]],
                lines[score.eventIds[15]],
            )
            assertEquals("width $width", score.eventIds, layout.events.map { it.eventId })
        }
    }

    @Test
    fun testLastInkOfAStaffStopsShortOfItsCustos() {
        // A psalm verse is short syllables, hyphens within its words and
        // dotted cadences: little of such a staff can be closed up to fit
        // it, and a dot reaches beyond the room its syllable takes.
        val score = parsedCustosFixture(
            listOf(
                "Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
                "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
                "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
                "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
                "in(h) o(f)re(h) me(g)o.(f.) (::)",
            ).joinToString(" "),
            custosIndex = null,
            eventCount = 46,
        )
        val engine = GregorianEngravingLayoutEngine()
        var custosCount = 0
        for (width in (240..800).map { it.toFloat() }) {
            val layout = engine.layout(score, width = width)
            assertEquals("width $width", score.eventIds, layout.events.map { it.eventId })
            val lines = uniqueKeysWithValues(
                layout.events.map { it.eventId to it.lineIndex },
            )
            for (custos in custosGlyphs(layout)) {
                val staff = unwrap(
                    layout.staffs.firstOrNull {
                        it.systemFrame.top <= custos.frame.center.y &&
                            custos.frame.center.y <= it.systemFrame.bottom
                    },
                    "width $width",
                )
                val glyphInk = layout.glyphs
                    .filter { glyph -> glyph.eventId?.let { lines[it] } == staff.index }
                    .map { it.frame.right }
                val strokeInk = layout.strokes
                    .filter { stroke -> stroke.eventId?.let { lines[it] } == staff.index }
                    .map { max(it.start.x, it.end.x) }
                val ink = unwrap((glyphInk + strokeInk).maxOrNull(), "width $width")
                assertLessThan(ink, custos.frame.left, "width $width, staff ${staff.index}")
                assertLessThanOrEqual(
                    custos.frame.right,
                    staff.frame.right,
                    "width $width, staff ${staff.index}",
                )
                custosCount += 1
            }
        }
        assertGreaterThan(custosCount, 0)
    }

    @Test
    fun testRepeatedNoteIsDrawnAsOftenAsItIsWritten() {
        val events = listOf("bi", "tri", "di", "men").mapIndexed { index, _ ->
            GregorianEvent(
                id = "event-$index",
                syllableId = "syllable-$index",
                relativePitch = 0,
            )
        }
        val score = GregorianScoreParser.parse(
            "name: repeated; %% (c4) bi(hvv) tri(hsss) di(hss.) men(g) (::)",
            GregorianEventTimeline(events),
        )
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        val glyphScale = 1f / 16
        val advance = 100 * glyphScale * 0.92f

        assertEquals(score.eventIds, layout.events.map { it.eventId })
        assertEquals(listOf(1, 1, 1, 1), layout.neumes.map { it.eventIds.size })
        assertEquals(listOf(2, 3, 2, 1), layout.neumes.map { it.noteCount })

        val expected: List<Pair<GregorianGlyphName, Int>> = listOf(
            GregorianGlyphName.VirgaShort to 2,
            GregorianGlyphName.Stropha to 3,
            GregorianGlyphName.Stropha to 2,
            GregorianGlyphName.PunctumQuadratum to 1,
        )
        for ((index, entry) in expected.withIndex()) {
            val (name, count) = entry
            val eventId = score.eventIds[index]
            val notes = layout.glyphs
                .filter { it.eventId == eventId && it.kind == GregorianGlyphKind.Catalog(name) }
                .sortedBy { it.frame.left }
            assertEquals(eventId, count, notes.size)
            for ((previous, next) in notes.zip(notes.drop(1))) {
                assertEquals(advance, next.frame.left - previous.frame.left, 0.001f)
                assertEquals(previous.frame.top, next.frame.top, 0.001f)
                assertEquals(previous.frame.size, next.frame.size)
            }
            // The event answers to all of them together.
            val placement = unwrap(layout.events.firstOrNull { it.eventId == eventId })
            assertTrue(notes.all { it.frame in placement.frame })
            val neume = unwrap(layout.neumeContaining(eventId))
            assertTrue(notes.all { it.frame in neume.inkFrame })
        }

        // The dot written after a repeated note follows the last of them.
        val dotted = score.eventIds[2]
        val mora = unwrap(
            layout.glyphs.firstOrNull { it.eventId == dotted && it.kind == GregorianGlyphKind.Mora },
        )
        val lastStropha = unwrap(
            layout.glyphs
                .filter { it.eventId == dotted && it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.Stropha) }
                .maxByOrNull { it.frame.right },
        )
        assertGreaterThan(mora.frame.left, lastStropha.frame.right)

        // A repeated note takes the room of as many notes written out.
        val writtenOut = GregorianScoreParser.parse(
            "name: repeated; %% (c4) bi(hvhv) men(g) (::)",
            GregorianEventTimeline(
                listOf("bi", "bi", "men").mapIndexed { index, syllable ->
                    GregorianEvent(
                        id = "event-$index",
                        syllableId = "syllable-$syllable",
                        relativePitch = 0,
                    )
                },
            ),
        )
        val abbreviated = GregorianScoreParser.parse(
            "name: repeated; %% (c4) bi(hvv) men(g) (::)",
            GregorianEventTimeline(
                listOf("bi", "men").mapIndexed { index, syllable ->
                    GregorianEvent(
                        id = "event-$index",
                        syllableId = "syllable-$syllable",
                        relativePitch = 0,
                    )
                },
            ),
        )
        val engine = GregorianEngravingLayoutEngine()
        val writtenOutVirgae = engine.layout(writtenOut, width = 390f).glyphs
            .filter { it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.VirgaShort) }
            .map { it.frame }
        val abbreviatedVirgae = engine.layout(abbreviated, width = 390f).glyphs
            .filter { it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.VirgaShort) }
            .map { it.frame }
        assertEquals(2, writtenOutVirgae.size)
        assertEquals(2, abbreviatedVirgae.size)
        for ((lhs, rhs) in writtenOutVirgae.zip(abbreviatedVirgae)) {
            assertEquals(rhs.left, lhs.left, 0.001f)
            assertEquals(rhs.top, lhs.top, 0.001f)
        }
    }

    private fun custosGlyphs(layout: GregorianLayout): List<GregorianPlacedGlyph> =
        layout.glyphs.filter { it.kind == GregorianGlyphKind.Custos }

    /**
     * A score in the third-line C clef whose events are one to a syllable,
     * except that the custos has a syllable to itself.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun parsedCustosFixture(
        body: String,
        custosIndex: Int?,
        eventCount: Int,
    ): GregorianScore {
        val events = (0 until eventCount).map { index ->
            GregorianEvent(
                id = "event-$index",
                syllableId = "syllable-$index",
                relativePitch = 0,
            )
        }
        return GregorianScoreParser.parse(
            "name: custos; %% (c3) $body",
            GregorianEventTimeline(events),
        )
    }

    private fun parsedFixture(noteCount: Int): GregorianScore {
        val syllables = (0 until noteCount).map { "s$it" }
        val body = syllables.mapIndexed { index, syllable ->
            "$syllable(${Char(102 + index % 7)})"
        }.joinToString(" ")
        val events = syllables.mapIndexed { index, _ ->
            GregorianEvent(
                id = "event-$index",
                syllableId = "syllable-$index",
                relativePitch = index % 7,
            )
        }
        return GregorianScoreParser.parse(
            "name: layout; %% (c4) $body (::)",
            GregorianEventTimeline(events),
        )
    }

    // XCTest's assertions that JUnit 4 lacks, with the values in their messages as XCTest gives them.

    /** XCTUnwrap: fails the test when the value is null, and returns it otherwise. */
    private fun <T : Any> unwrap(value: T?, message: String = ""): T {
        assertNotNull(listOf(message, "expected a value, found null").filter { it.isNotEmpty() }.joinToString(": "), value)
        return value!!
    }

    /** Swift's `Dictionary(uniqueKeysWithValues:)`, which traps on a key given twice. */
    private fun <K, V> uniqueKeysWithValues(pairs: List<Pair<K, V>>): Map<K, V> {
        val result = LinkedHashMap<K, V>()
        for ((key, value) in pairs) {
            assertFalse("Dictionary literal contains duplicate key $key", result.containsKey(key))
            result[key] = value
        }
        return result
    }

    private fun <T : Comparable<T>> assertGreaterThan(lhs: T, rhs: T, message: String = "") =
        assertTrue(describe(message, "($lhs) is not greater than ($rhs)"), lhs > rhs)

    private fun <T : Comparable<T>> assertGreaterThanOrEqual(lhs: T, rhs: T, message: String = "") =
        assertTrue(describe(message, "($lhs) is less than ($rhs)"), lhs >= rhs)

    private fun <T : Comparable<T>> assertLessThan(lhs: T, rhs: T, message: String = "") =
        assertTrue(describe(message, "($lhs) is not less than ($rhs)"), lhs < rhs)

    private fun <T : Comparable<T>> assertLessThanOrEqual(lhs: T, rhs: T, message: String = "") =
        assertTrue(describe(message, "($lhs) is greater than ($rhs)"), lhs <= rhs)

    private fun describe(message: String, failure: String) =
        if (message.isEmpty()) failure else "$failure - $message"
}
