package io.github.mcmxmcm.gabc.parser

/**
 * The identity a compiled timeline gives one written note: the name that ties the note to playback and
 * highlighting, and the syllable it is sung on.
 *
 * A content pipeline that compiles its own timelines can implement this for its event type and hand the
 * timeline to [GregorianScoreParser.parse]. Without one, the parser numbers the notes itself.
 */
public interface GregorianTimelineEvent {
    /** The note's identifier, unique within its score. */
    public val id: String

    /** The syllable the note is sung on. The notes of one notation group that carries lyric text must share it. */
    public val syllableId: String

    /** The note's staff step from the GABC pitch `h`: `h` is 0, `g` is -1 and `i` is 1. */
    public val relativePitch: Int
}

/**
 * A score's events: one for each pitch letter written in its notation, in the order written. A custos (`f+`) and
 * a note written several times over (`hvv`, `hsss`) are one event each; a clef, a division or a standalone
 * accidental (`ix`) is none.
 */
public interface GregorianTimeline<out Event : GregorianTimelineEvent> {
    public val events: List<Event>
}

/** An event built in code. */
public data class GregorianEvent(
    override val id: String,
    override val syllableId: String,
    override val relativePitch: Int,
) : GregorianTimelineEvent

/** A timeline built in code. */
public data class GregorianEventTimeline(override val events: List<GregorianEvent>) : GregorianTimeline<GregorianEvent>
