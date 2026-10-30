# gabc-parser

Parse [GABC](https://gregorio-project.github.io/gabc/) chant notation,
engrave it natively, and draw it: in Swift with SwiftUI on iOS and macOS, in
Kotlin with Jetpack Compose on Android, and in TypeScript as SVG on the web.

GABC is the plain-text notation of the Gregorio project, in which GregoBase
and most digital chant sources are written. Each library reads a score into a
model of clefs, neumes, lyrics and divisions; lays it out on four-line staffs
at any width, drawing Exsurge's glyph outlines as vector paths; and draws the
result. There is no music font in the rendering path, and on iOS and Android
no web view.

The Swift, Kotlin and TypeScript libraries are one design in three languages.
They are kept to the same behaviour, run the same tests against the same
fixtures, and engrave from one generated glyph catalog, so a score is laid
out alike on an iPhone, an Android phone and a web page.

| | Swift | Kotlin | TypeScript |
| --- | --- | --- | --- |
| Parsing | `GABCParser` | `gabc-parser` | `gabc-parser` |
| Engraving | `GABCLayout` | `gabc-layout` | `gabc-parser` |
| The lyrics' face, EB Garamond | `GABCLyricFont` | `gabc-lyric-font` | the page's own |
| Drawing | `GABCSwiftUI` (SwiftUI) | `gabc-compose` (Jetpack Compose) | `gabc-parser` (SVG) |
| Requires | Swift 6 tools (Xcode 16 or later); iOS 17 or macOS 14 | Java 17; Android 8 (API 26) for the font and the views | ES2022; a canvas to measure lyrics |
| Installed with | Swift Package Manager | Gradle, from [JitPack](https://jitpack.io) | npm, from this repository |

- [Swift](#swift)
- [Kotlin](#kotlin)
- [TypeScript](#typescript)
- [GABC coverage](#gabc-coverage), the same in all three

## Swift

| Library | What it gives you |
| --- | --- |
| `GABCParser` | `GregorianScoreParser`: GABC to a `GregorianScore`. Foundation only. |
| `GABCLayout` | `GregorianEngravingLayoutEngine`: a score to a `GregorianLayout` of placed glyphs, strokes and lyrics; glyph outlines as `CGPath`; hit-testing and highlights. |
| `GABCLyricFont` | EB Garamond, the lyrics' face, roman and italic, and `GregorianLyricFont.registerBundledFont()`, for a client that does not bundle the font itself. |
| `GABCSwiftUI` | `GregorianScoreView` and `GregorianScoreCanvas`. Registers EB Garamond before it lays out. |

Each library re-exports the one it builds on, so `import GABCSwiftUI` is all
a SwiftUI app needs. An app that bundles EB Garamond itself, and uses
`GABCLayout` without `GABCSwiftUI`, need not link `GABCLyricFont`.

### Installation

Add the package in Xcode (File ▸ Add Package Dependencies…, with
`https://github.com/MCMXMCM/gabc-parser.git`) or in `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/MCMXMCM/gabc-parser.git", from: "0.8.1")
],
targets: [
    .target(
        name: "MyApp",
        dependencies: [.product(name: "GABCSwiftUI", package: "gabc-parser")]
    )
]
```

### Drawing a score

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
syllable's capital becomes a large bold initial on the words' baseline, with
the mode centred above it and the rest of its word small, however the GABC
writes it (`PU(ei)ER(i.)` is P, uer). A piece with no psalm tone, a hymn, a
responsory or a part of the Mass, takes one with `opensPiece: true`, its mode
alone above it (`mode: "8"`).

Colors come from the environment. The default is warm black with red
rubrics, lightened in a dark appearance:

```swift
GregorianScoreView(gabc: gabc)
    .gregorianScoreStyle(GregorianScoreStyle(ink: .primary, rubric: .red, highlight: .orange))
```

`GregorianLayoutMetrics` sizes the staff (`notationScale`, 0.6 to 1.6) and
the text (`lyricScale`); `GregorianLayoutMetrics.lyricScale(for:)` follows
Dynamic Type. Its `displayScale`, the pixels in a point of the screen the
score is drawn on, makes the staff space a whole number of pixels and every
staff line, ledger line, stem, episema and bar whole pixels wide, two at
least, set where its edges fall between pixels, so that they draw crisply;
the view gives it the screen's scale. Without one, as for print, they are set
where they fall. Lyrics are measured and drawn in EB Garamond, which
GABCSwiftUI registers from `GABCLyricFont`. Another font can be named with
`lyricFontName`; each placed lyric records the font it was measured in, and is
drawn in it. The face must be registered before a score is laid out.

#### Long documents

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

#### Core Graphics

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

### Parsing

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

#### Compiled timelines

A content pipeline that already names each note, for playback say, can pass
its timeline: conform its event type to `GregorianTimelineEvent` (`id`,
`syllableID`, `relativePitch`) and its timeline to `GregorianTimeline`, then
call `GregorianScoreParser.parse(gabc:timeline:)`. The timeline holds one
event for each pitch letter written; the parse fails if the notation writes
more or fewer. Without a timeline, the parser names the notes and syllables
itself, one syllable for each notation group that writes a note.

## Kotlin

| Module | The Swift library | What it gives you |
| --- | --- | --- |
| `gabc-parser` | `GABCParser` | `GregorianScoreParser`: GABC to a `GregorianScore`. Plain Kotlin, the JDK only. |
| `gabc-layout` | `GABCLayout` | `GregorianEngravingLayoutEngine`: a score to a `GregorianLayout` of placed glyphs, strokes and lyrics, in points; glyph outlines as display lists; hit-testing and highlights. Plain Kotlin. |
| `gabc-lyric-font` | `GABCLyricFont` | EB Garamond for Android, and a measurer of lyrics in it, for an app that does not bundle the font itself. |
| `gabc-compose` | `GABCSwiftUI` | `GregorianScoreView` and `GregorianScoreCanvas` composables. |

Each module brings in the one it builds on. An app that bundles EB Garamond
itself, and draws the layout itself, needs only `gabc-layout`, with a
`GregorianLyricMeasurer` of its own. The parser and the layout are plain
Kotlin, so a server or desktop app on the JVM can parse and engrave without
Android. The layout's geometry is its own (`GregorianPoint`, `GregorianSize`,
`GregorianRect`), named as Compose names its own; `gabc-compose` converts it
(`toOffset()`, `toRect()`). A layout point is drawn as a density-independent
pixel. Names follow Kotlin's conventions: `eventId` for `eventID`,
`GregorianNoteShape.Virga` for `.virga`.

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
    implementation("com.github.MCMXMCM.gabc-parser:gabc-compose:0.8.1")
}
```

Depend on `gabc-parser` or `gabc-layout` instead for the parser or the
engraver alone.

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
its staffs, as the SwiftUI view is; a tap, a highlight, `scoreId`, `mode` and
`opensPiece` work as they do there, and the staff is snapped to the screen's
pixels by its density. Colors come from
`GregorianScoreStyleProvider(GregorianScoreStyle(ink, rubric, highlight))`,
warm black and red by default and lightened in a dark theme.
`GregorianLayoutMetrics.lyricScale(fontScale)` follows the system's font size.
For long documents, prepare scores in the background
(`GregorianPreparedScore.prepare(context, score, width)`) and draw them with
`GregorianScoreCanvas`.

#### Without Compose

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

### Parsing

```kotlin
val score = GregorianScoreParser.parse(gabc)
for (element in score.elements) {
    if (element is GregorianNotationElement.Neume) {
        val neume = element.neume
        println("${neume.lyric} ${neume.form} ${neume.notes.map { it.pitch }}")
    }
}
println(score.lyricText) // Deus in adjutórium meum inténde.
```

The model is the Swift one. A compiled timeline is a `GregorianTimeline` of
`GregorianTimelineEvent`s (`id`, `syllableId`, `relativePitch`), passed to
`GregorianScoreParser.parse(gabc, timeline)`.

## TypeScript

One package, `gabc-parser`, holds the parser, the engraver and a drawing as
SVG. It is ported from the Kotlin libraries line for line and held to their
tests, and it is ES modules (ES2022) with type declarations. The engraver
measures lyrics through a `GregorianLyricMeasurer`: in a browser, a canvas
(`createCanvasLyricMeasurer()`), in the fonts the page has loaded.

### Installation

npm installs the package from this repository's tags, building it as it
installs:

```sh
npm install github:MCMXMCM/gabc-parser#0.8.1
```

### Drawing a score

```ts
import {
  GregorianEngravingLayoutEngine,
  GregorianScoreParser,
  createCanvasLyricMeasurer,
  gregorianScoreSVG,
} from "gabc-parser";

// The lyrics are measured in the page's fonts: load EB Garamond first, with
// its bold for accented syllables and its italic for preparatory ones.
await Promise.all(["21px", "bold 21px", "italic 21px"].map((size) => document.fonts.load(`${size} 'EB Garamond'`)));
const score = GregorianScoreParser.parse(gabc);
const engine = new GregorianEngravingLayoutEngine(createCanvasLyricMeasurer());
const layout = engine.layout(score, element.clientWidth, {
  metrics: { displayScale: devicePixelRatio },
  openingLabel: "8g",
});
element.innerHTML = gregorianScoreSVG(layout, { rubric: "#b3261e" });
```

The drawing has a group for each staff and, within it, for each neume; its
elements carry `data-staff`, `data-neume` and `data-event`, and each neume's
group lists its notes in `data-events`, so that a page can light the note being
sung, or print a staff by itself. Notes, staff and words take the page's text
colour unless `ink` is given. A face other than EB Garamond is named by
`lyricFontName` and mapped to its CSS family by `families`.
`neumeAt(layout, point)` finds the neume a tap falls on, and
`GregorianScoreHighlight.of(eventId, score, layout)` what to light while a note
is sung, as in the other languages; `glyphPath(glyph)` and
`glyphPathData(glyph)` give a glyph's outlines for drawing it another way.

### Parsing

```ts
const score = GregorianScoreParser.parse(gabc);
for (const element of score.elements) {
  if (element.kind === "neume") {
    console.log(element.neume.lyric, element.neume.form, element.neume.notes.map((note) => note.pitch));
  }
}
console.log(score.lyricText); // Deus in adjutórium meum inténde.
```

The model is the Swift and Kotlin one in TypeScript's terms: enumerations are
string unions in the Swift spelling (`"virga"`, `"pesQuassus"`), a score's
elements a union keyed by `kind`, and what Swift leaves `nil` is `null`. A
compiled timeline is passed as `parse(gabc, { timeline })`, and a score
identifier as `parse(gabc, { scoreId })`. Errors are
`GregorianScoreParserError`s whose `reason` says what failed, and where.

## GABC coverage

The three languages read the same notation, and fail on the same tokens.

- The header up to `%%` is skipped; text without `%%` is read as notation.
  `%` comments are dropped.
- Clefs `c1`–`c4`, `f1`–`f4` and `cb1`–`cb4`; pitches `a`–`m`.
- Punctum, virga (`v`), punctum inclinatum (a capital letter, liquescent with
  `~`, `<` or `>`), quilisma (`w`), oriscus (`o`), stropha (`s`), cavum (`r`,
  `r0`), liquescents (`~`, `<`, `>`), a written custos (`f+`), and repeated
  virgae and strophae (`hvv`, `hsss`).
- Mora (`.`; `..` dots a neume's last two notes), horizontal episema (`_`;
  `__` and `___` reach back over the notes before), vertical episema (`'`),
  each set above with `1` or below with `0` after it (`.1`, `_0`, `'1`), and
  accidentals (`ix`, `iy`, `i#`).
- Divisions `,` `` ` `` `;` `:` `::`, the line breaks `z` and `Z`, and the
  neume separators: `/` a quarter staff space, `//` and a space within the
  group half of one, `!` none. `z0`, a custos for the next note, breaks no
  line: a staff's end has its own custos.
- In lyrics, tags are dropped, a tilde is a space, and braces name the
  letters to centre under the note (`{ia}.`); psalm-tone pointing marks
  preparatory (`_cu_`, italic) and accented (`*Ja*`, bold) syllables. `V/.`
  and `R/.` become ℣. and ℟.; they, the asterisks, crosses, repeat signs
  (`ij.`, `iij.`) and verse numbers are rubrics, whether written alone or
  with a sung syllable (`V/. Dó`, `2. Pro`, `hæc, †`), and a rubric ends the
  word before it. One written before a syllable heads it; ℣., ℟., ✠ and verse
  numbers written alone head what follows, and stand after the bar they are
  written with (`um.(f.) R/.(::)In(f)`); the others close the words before
  them.

Words are set as the chant books set them. Each syllable's vowel stands under
its first note (past a consonantal i or u: `ius`, `qui`); the syllables of a
word are either joined, the notes between them closing up by as much as 30%
of their spacing to let them meet, or parted by one hyphen set just after the
first, in the lyrics' own type; a word keeps at least a word's space from the
next; and a word divided at a staff's end carries its hyphen there. The words
stand as far below a staff's lowest ink, its notes and their signs, as they
stand below a note on the bottom line, and no lower.

Staffs are broken and justified as the chant books break them. A paragraph's
breaks are chosen together, as TeX and Gregorio choose them, by how far each
staff must be stretched or closed up and by what each break costs: a staff
best ends at a bar, then at a word; a word is divided at a cost, beside a
syllable of one letter only as a last resort; a melisma at a bar, or where
four of its notes or more stand on either side; and a last staff that holds
one syllable is avoided. No staff begins with a bar, an asterisk or a rubric
that closes the words before it, or ends with one that heads the words after
it, and none ends between an accidental and its note. Every staff but a
paragraph's last is justified to the full measure at its words, bars, rubrics
and clefs, never within a word; a bar stands midway between its notes, and the
custos at the staff's end.

Neumes are grouped and named as Exsurge groups them: podatus, clivis,
torculus, porrectus, scandicus, climacus, ancus, salicus, pes subpunctis and
the rest; notes repeated on one pitch make a distropha or a tristropha only
where the run ends with them, and otherwise begin the next neume, a pressus
whose punctum abuts it (`hhg`). They are drawn as Exsurge draws them, from
its glyphs: notes joined by a stem where they leap, a clivis and a porrectus
hung from a stem on the left, a virga's stem inside its head, a pes's upper
note set over the lower, a porrectus's last note over the end of its
oblique, and a climacus's diamonds stepped down unjoined. A dot is set at the
Liber's size just after its note, in the space above a note on a line; an
episema keeps off the staff's lines and runs on over the next note's; an
ictus stands a step beyond its note, below unless the neume or the GABC sets
it above; and a quarter bar crosses the top line. `[…]` annotations and the signs this engraver does not draw (`@`,
`-`, `q`, `r1`–`r5`, digits) are skipped. Anything else in a notation group
fails the parse with an unsupported-token error and its offset.

## Development

```sh
swift test
cd kotlin && ./gradlew test                   # the parser, the layout, the drawing
cd kotlin && ./gradlew connectedAndroidTest   # the font and the views, on a device
npm install && npm test                       # the TypeScript port
```

The Kotlin and TypeScript tests read the Swift tests' fixtures and measure
lyrics in the font `GABCLyricFont` carries, with the JDK's text layout and
with fontkit. A change to one language is made in the others as well, with
its tests.

The glyph catalog is generated from Exsurge's SVG glyph outlines, for Swift,
Kotlin and TypeScript alike; to regenerate them, check Exsurge out at the
revision named in `THIRD_PARTY_NOTICES.md` and run
`make generate-glyphs EXSURGE_GLYPHS=/path/to/exsurge/src/Exsurge.Glyphs.js`.
That revision is no longer published, but `src/Exsurge.Glyphs.js` has not
changed since `eb78bf2` (June 2026), which reproduces the catalog byte for
byte.

## License

MIT; see `LICENSE`. The glyph geometry is derived from Exsurge (MIT) and the
EB Garamond bundled in `GABCLyricFont` and `gabc-lyric-font` is under the SIL
Open Font License 1.1; see `THIRD_PARTY_NOTICES.md`.
