# gabc-parser

Parse [GABC](https://gregorio-project.github.io/gabc/) chant notation in
Swift, engrave it natively, and draw it in SwiftUI; or do the same in Kotlin,
and draw it with Jetpack Compose on Android (see
[Kotlin and Android](#kotlin-and-android)).

GABC is the plain-text notation of the Gregorio project, in which GregoBase
and most digital chant sources are written. This package reads a score into
a model of clefs, neumes, lyrics and divisions; lays it out on four-line
staffs at any width, drawing Exsurge's glyph outlines as vector paths; and
draws the result in SwiftUI. There is no web view, JavaScript or music font
in the rendering path.

## Libraries

| Library | What it gives you |
| --- | --- |
| `GABCParser` | `GregorianScoreParser`: GABC to a `GregorianScore`. Foundation only. |
| `GABCLayout` | `GregorianEngravingLayoutEngine`: a score to a `GregorianLayout` of placed glyphs, strokes and lyrics; glyph outlines as `CGPath`; hit-testing and highlights. |
| `GABCLyricFont` | EB Garamond, the lyrics' face, and `GregorianLyricFont.registerBundledFont()`, for a client that does not bundle the font itself. |
| `GABCSwiftUI` | `GregorianScoreView` and `GregorianScoreCanvas`. Registers EB Garamond before it lays out. |

Each library re-exports the one it builds on, so `import GABCSwiftUI` is all
a SwiftUI app needs. An app that bundles EB Garamond itself, and uses
`GABCLayout` without `GABCSwiftUI`, need not link `GABCLyricFont`.

Requires Swift 6 tools (Xcode 16 or later) and iOS 17 or macOS 14.

## Installation

Add the package in Xcode (File ▸ Add Package Dependencies…, with
`https://github.com/MCMXMCM/gabc-parser.git`) or in `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/MCMXMCM/gabc-parser.git", from: "0.2.0")
],
targets: [
    .target(
        name: "MyApp",
        dependencies: [.product(name: "GABCSwiftUI", package: "gabc-parser")]
    )
]
```

## Drawing a score

```swift
import GABCSwiftUI
import SwiftUI

struct OpeningVersicleView: View {
    let gabc = """
        name: Deus in adiutorium;
        %%
        (c4) De(f)us(g) in(g) ad(g)ju(h)tó(g)ri(f)um(g) me(h)um(g) in(f)tén(g)de.(f.) (::)
        """

    @State private var sungNote: String?

    var body: some View {
        ScrollView {
            GregorianScoreView(gabc: gabc, highlightedEventID: sungNote) { neume in
                sungNote = neume.eventIDs.first
            }
            .padding()
        }
    }
}
```

The view lays the score out across the width it is offered, and is as tall
as its staffs. A tap gives the nearest neume; `highlightedEventID` lights a
note's whole neume and the lyric of its syllable. Notes are named `note-0`,
`note-1` and so on in the order written; pass `scoreID:` to prefix the names
of each score in a document. Pass the mode, `mode: "8g"`, and the first
syllable's capital becomes a large initial with the mode written above it.

Colors come from the environment. The default is warm black with red
rubrics, lightened in a dark appearance:

```swift
GregorianScoreView(gabc: gabc)
    .gregorianScoreStyle(GregorianScoreStyle(ink: .primary, rubric: .red, highlight: .orange))
```

`GregorianLayoutMetrics` sizes the staff (`notationScale`, 0.6 to 1.6) and
the text (`lyricScale`); `GregorianLayoutMetrics.lyricScale(for:)` follows
Dynamic Type. Lyrics are measured and drawn in EB Garamond, which
GABCSwiftUI registers from `GABCLyricFont`. Another font can be named with
`lyricFontName`; each placed lyric records the font it was measured in, and is
drawn in it. The face must be registered before a score is laid out.

### Long documents

`GregorianScoreView` lays out on the main actor. For many scores, prepare
them in the background (`GregorianPreparedScore` is `Sendable`) and draw them
with `GregorianScoreCanvas`:

```swift
let prepared = try await Task.detached {
    let score = try GregorianScoreParser.parse(gabc: gabc)
    return GregorianPreparedScore(score: score, width: width)
}.value

GregorianScoreCanvas(prepared)
```

### Core Graphics

Without SwiftUI, draw the layout yourself. Its origin is at the top left,
with y running down, in points:

```swift
import GABCLayout
import GABCLyricFont

// Unless the app registers EB Garamond itself.
GregorianLyricFont.registerBundledFont()
let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 600)
for stroke in layout.strokes {
    context.move(to: stroke.start)
    context.addLine(to: stroke.end)
    context.setLineWidth(stroke.lineWidth)
    context.strokePath()
}
for glyph in layout.glyphs {
    context.addPath(glyph.path)
    context.fillPath(using: .evenOdd)
}
// Each lyric: draw lyric.text in lyric.fontName at lyric.fontSize, its top
// left at lyric.origin. Rubrics have lyric.style == .rubric.
```

## Parsing

```swift
import GABCParser

let score = try GregorianScoreParser.parse(gabc: gabc)
for case let .neume(neume) in score.elements {
    print(neume.lyric, neume.form, neume.notes.map(\.pitch))
}
print(score.lyricText) // Deus in adjutórium meum inténde.
```

`score.elements` keeps clefs, accidentals, divisions, lyric-only marks (`*`,
℣., ℟.) and line breaks in order with the neumes. A note's `pitch` counts
staff steps from `a` (0) to `m` (12); its `relativePitch` counts them from `h`.

### Compiled timelines

A content pipeline that already names each note, for playback say, can pass
its timeline: conform its event type to `GregorianTimelineEvent` (`id`,
`syllableID`, `relativePitch`) and its timeline to `GregorianTimeline`, then
call `GregorianScoreParser.parse(gabc:timeline:)`. The timeline holds one
event for each pitch letter written; the parse fails if the notation writes
more or fewer. Without a timeline, the parser names the notes and syllables
itself, one syllable for each notation group that writes a note.

## Kotlin and Android

`kotlin/` holds a native Kotlin version of the package, built the same way and
kept to the same behaviour: a port of the Swift sources, carrying the Swift
tests, engraving from the same generated glyph catalog.

| Module | The Swift library | What it gives you |
| --- | --- | --- |
| `gabc-parser` | `GABCParser` | `GregorianScoreParser`: GABC to a `GregorianScore`. Plain Kotlin, the JDK only. |
| `gabc-layout` | `GABCLayout` | `GregorianEngravingLayoutEngine`: a score to a `GregorianLayout` of placed glyphs, strokes and lyrics, in points; glyph outlines as display lists; hit-testing and highlights. Plain Kotlin. |
| `gabc-lyric-font` | `GABCLyricFont` | EB Garamond for Android, and a measurer of lyrics in it, for an app that does not bundle the font itself. |
| `gabc-compose` | `GABCSwiftUI` | `GregorianScoreView` and `GregorianScoreCanvas` composables. |

Each module brings in the one it builds on. An app that bundles EB Garamond
itself, and draws the layout itself, needs only `gabc-layout`, with a
`GregorianLyricMeasurer` of its own. The layout's geometry is its own
(`GregorianPoint`, `GregorianSize`, `GregorianRect`), named as Compose names
its own; `gabc-compose` converts it (`toOffset()`, `toRect()`). A layout point
is drawn as a density-independent pixel. Names follow Kotlin's conventions:
`eventId` for `eventID`, `GregorianNoteShape.Virga` for `.virga`.

The parser and the layout need Java 17; the Android libraries, Android 8
(API 26).

### Installation

The libraries are built by [JitPack](https://jitpack.io) from this
repository's tags. In `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

and in the app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.MCMXMCM.gabc-parser:gabc-compose:0.3.0")
}
```

### Drawing a score

```kotlin
@Composable
fun OpeningVersicle() {
    val gabc = """name: Deus in adiutorium;
        %%
        (c4) De(f)us(g) in(g) ad(g)ju(h)tó(g)ri(f)um(g) me(h)um(g) in(f)tén(g)de.(f.) (::)"""
    var sungNote by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        GregorianScoreView(gabc, highlightedEventId = sungNote, onTapNeume = { neume ->
            sungNote = neume.eventIds.first()
        })
    }
}
```

The view lays the score out across the width it is offered and is as tall as
its staffs, as the SwiftUI view is. Colors come from
`GregorianScoreStyleProvider(GregorianScoreStyle(ink, rubric, highlight))`,
warm black and red by default and lightened in a dark theme.
`GregorianLayoutMetrics.lyricScale(fontScale)` follows the system's font size.
For long documents, prepare scores in the background
(`GregorianPreparedScore.prepare(context, score, width)`) and draw them with
`GregorianScoreCanvas`.

### Without Compose

```kotlin
val score = GregorianScoreParser.parse(gabc)
val layout = GregorianEngravingLayoutEngine(GregorianLyricFont.measurer(context))
    .layout(score, width = 600f)
for (glyph in layout.glyphs) {
    // glyph.path: the outline's display lists in layout coordinates. Fill
    // them with the even-odd rule. glyph.transform sets the catalog's own
    // outline (GregorianGlyphCatalog.definition(name).paths) in the frame.
}
// Each lyric: draw lyric.text in the face lyric.fontName names, at
// lyric.fontSize, its top left at lyric.origin.
```

On the JVM, without Android, give the engine a `GregorianLyricMeasurer` that
measures with the JDK's text layout or the server's own.

## GABC coverage

- The header up to `%%` is skipped; text without `%%` is read as notation.
  `%` comments are dropped.
- Clefs `c1`–`c4`, `f1`–`f4` and `cb1`–`cb4`; pitches `a`–`m`.
- Punctum, virga (`v`), punctum inclinatum (a capital letter), quilisma
  (`w`), oriscus (`o`), stropha (`s`), cavum (`r`, `r0`), liquescents (`~`,
  `<`, `>`), a written custos (`f+`), and repeated virgae and strophae
  (`hvv`, `hsss`).
- Mora (`.`, `..`), horizontal episema (`_`; below the note with `_0`),
  vertical episema (`'`), and accidentals (`ix`, `iy`, `i#`).
- Divisions `,` `` ` `` `;` `:` `::`, the line break `z`, and the neume
  separators `/` and `!`.
- In lyrics, tags and braces are dropped; psalm-tone pointing marks
  preparatory (`_cu_`, italic) and accented (`*Ja*`, bold) syllables; `V/.`
  and `R/.` become ℣. and ℟.

Neumes are grouped and named as Exsurge groups them: podatus, clivis,
torculus, porrectus, scandicus, climacus, salicus, pes subpunctis and the
rest. `[…]` annotations and the signs this engraver does not draw (`@`, `-`,
`q`, `r1`–`r5`, digits) are skipped. Anything else in a notation group fails
the parse with `GregorianScoreParserError.unsupportedToken` and its offset.

## Development

```sh
swift test
cd kotlin && ./gradlew test                   # the parser, the layout, the drawing
cd kotlin && ./gradlew connectedAndroidTest   # the font and the views, on a device
```

The Kotlin tests read the Swift tests' fixtures and measure lyrics in the
font `GABCLyricFont` carries.

The glyph catalog is generated from Exsurge's SVG glyph outlines, for Swift
and Kotlin alike; to regenerate both, check Exsurge out at the revision named
in `THIRD_PARTY_NOTICES.md` and run
`make generate-glyphs EXSURGE_GLYPHS=/path/to/exsurge/src/Exsurge.Glyphs.js`.
That revision is no longer published, but `src/Exsurge.Glyphs.js` has not
changed since `eb78bf2` (June 2026), which reproduces the catalog byte for
byte.

## License

MIT; see `LICENSE`. The glyph geometry is derived from Exsurge (MIT) and the
EB Garamond bundled in `GABCLyricFont` and `gabc-lyric-font` is under the SIL
Open Font License 1.1; see `THIRD_PARTY_NOTICES.md`.
