package io.github.mcmxmcm.gabc.layout

/**
 * Names from Exsurge's canonical Gregorian SVG glyph set.
 *
 * The generated catalog deliberately contains geometry rather than font characters so engraving is deterministic
 * across platforms and font versions. Each name is Exsurge's own.
 */
public enum class GregorianGlyphName {
    None,
    AcuteAccent,
    GraveAccent,
    Circle,
    Semicircle,
    ReversedSemicircle,
    Stropha,
    BeginningAscLiquescent,
    BeginningDesLiquescent,
    CustosDescLong,
    CustosDescShort,
    CustosLong,
    CustosShort,
    DoClef,
    FaClef,
    TrebleClef,
    TrebleClefSmall,
    ChiRhoClef,
    ChiRhoClefSans,
    Flat,
    Mora,
    Natural,
    Sharp,
    OriscusAsc,
    OriscusDes,
    OriscusLiquescent,
    PodatusLower,
    PodatusLowerShort,
    PodatusUpper,
    PodatusUpperShort,
    Porrectus1,
    Porrectus2,
    Porrectus3,
    Porrectus4,
    PunctumCavum,
    PunctumQuadratum,
    PunctumQuadratumLiquescent,
    PunctumQuadratumAscLiquescent,
    PunctumQuadratumDesLiquescent,
    PunctumInclinatum,
    PunctumInclinatumLiquescent,
    Quilisma,
    TerminatingAscLiquescent,
    TerminatingDesLiquescent,
    VerticalEpisemaAbove,
    VerticalEpisemaBelow,
    VirgaLong,
    VirgaShort,
    Virgula,
}

public enum class GregorianGlyphAlignment { Left, Center, Right, Undefined }

/** A platform-neutral display-list command. SVG parsing and arc flattening happen in the checked-in generator, never in the app. */
public sealed interface GregorianPathCommand {
    public data class Move(val point: GregorianPoint) : GregorianPathCommand
    public data class Line(val point: GregorianPoint) : GregorianPathCommand
    public data class Quadratic(val control: GregorianPoint, val end: GregorianPoint) : GregorianPathCommand
    public data class Cubic(val control1: GregorianPoint, val control2: GregorianPoint, val end: GregorianPoint) : GregorianPathCommand
    public data object Close : GregorianPathCommand
}

/**
 * A glyph of the catalog: its outlines, the box they fill, the point it is set by, and how it aligns. The outlines
 * are in the catalog's units, about the glyph's origin.
 */
public class GregorianGlyphDefinition internal constructor(
    outlines: List<String>,
    public val bounds: GregorianRect,
    public val origin: GregorianPoint,
    public val alignment: GregorianGlyphAlignment,
) {
    /** The outlines as display lists, one for each of Exsurge's paths; read from the catalog the first time they're asked for. */
    public val paths: List<List<GregorianPathCommand>> by lazy { outlines.map(::readOutline) }

    private companion object {
        /** Reads one outline as the generator writes it: `M x y`, `L x y`, `Q cx cy x y`, `C c1x c1y c2x c2y x y` and `Z`. */
        fun readOutline(outline: String): List<GregorianPathCommand> {
            val words = outline.split(' ').filter { it.isNotEmpty() }
            val commands = mutableListOf<GregorianPathCommand>()
            var index = 0
            while (index < words.size) {
                val command = words[index][0]
                // A command's first number follows its letter without a space; the rest follow it, a word each.
                val numbers = ArrayList<Float>(6)
                if (words[index].length > 1) numbers += words[index].substring(1).toFloat()
                index += 1
                val count = when (command) {
                    'M', 'L' -> 2
                    'Q' -> 4
                    'C' -> 6
                    'Z' -> 0
                    else -> error("Unknown glyph command $command in the catalog")
                }
                while (numbers.size < count) numbers += words[index++].toFloat()
                fun point(at: Int) = GregorianPoint(numbers[at], numbers[at + 1])
                commands += when (command) {
                    'M' -> GregorianPathCommand.Move(point(0))
                    'L' -> GregorianPathCommand.Line(point(0))
                    'Q' -> GregorianPathCommand.Quadratic(point(0), point(2))
                    'C' -> GregorianPathCommand.Cubic(point(0), point(2), point(4))
                    else -> GregorianPathCommand.Close
                }
            }
            return commands
        }
    }
}

public object GregorianGlyphCatalog {
    public fun definition(name: GregorianGlyphName): GregorianGlyphDefinition =
        generatedGlyphDefinitions[name] ?: generatedGlyphDefinitions.getValue(GregorianGlyphName.None)
}
