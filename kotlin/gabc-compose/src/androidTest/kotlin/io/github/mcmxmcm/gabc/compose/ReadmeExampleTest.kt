package io.github.mcmxmcm.gabc.compose

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import io.github.mcmxmcm.gabc.parser.GregorianNotationElement
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// The examples in README.md, kept compiling and true.

private const val GABC = """name: Deus in adiutorium;
%%
(c4) De(f)us(g) in(g) ad(g)ju(h)tó(g)ri(f)um(g) me(h)um(g) in(f)tén(g)de.(f.) (::)"""

@Composable
private fun OpeningVersicle() {
    var sungNote by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        GregorianScoreView(GABC, highlightedEventId = sungNote, onTapNeume = { neume -> sungNote = neume.eventIds.first() })
    }
}

class ReadmeExampleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun drawingExamples() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prepared = runBlocking {
            withContext(Dispatchers.Default) {
                val score = GregorianScoreParser.parse(GABC)
                GregorianPreparedScore.prepare(context, score, width = 390f)
            }
        }
        compose.setContent {
            Column {
                OpeningVersicle()
                GregorianScoreStyleProvider(GregorianScoreStyle(ink = Color.DarkGray, rubric = Color.Red, highlight = Color(0xFFFF9800))) {
                    GregorianScoreView(GABC)
                }
                GregorianScoreCanvas(prepared)
            }
        }
        compose.waitForIdle()
        assertEquals(390f, prepared.layout.size.width)
    }

    @Test fun parsingExample() {
        val score = GregorianScoreParser.parse(GABC)
        val printed = score.elements.filterIsInstance<GregorianNotationElement.Neume>().map { (neume) ->
            "${neume.lyric} ${neume.form} ${neume.notes.map { it.pitch }}"
        }
        assertEquals("De Punctum [5]", printed.first())
        assertEquals("Deus in adjutórium meum inténde.", score.lyricText)
    }
}
