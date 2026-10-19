package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianScore
import io.github.mcmxmcm.gabc.parser.GregorianTimeline

/** The neume whose target holds a point, or the nearest such neume where targets meet. A custos is no target. */
public fun GregorianLayout.neumeAt(location: GregorianPoint): GregorianNeumePlacement? =
    neumes
        .filter { location in it.hitFrame }
        .minByOrNull { squaredDistance(location, it.hitFrame) }

private fun squaredDistance(point: GregorianPoint, frame: GregorianRect): Float {
    val deltaX = point.x - frame.center.x
    val deltaY = point.y - frame.center.y
    return deltaX * deltaX + deltaY * deltaY
}

/** What to light while one note is sung: the whole neume that holds it, and every neume of the syllable it is sung on, with that syllable's lyric. */
public class GregorianScoreHighlight private constructor(
    /** The neume that holds the note. */
    public val neume: GregorianNeumePlacement,
    /** The events whose ink is lit: those of the neume. */
    public val eventIds: Set<String>,
    /** The neumes of the note's syllable, whose lyrics are lit. */
    public val syllableNeumeIds: Set<String>,
) {
    override fun equals(other: Any?): Boolean =
        other is GregorianScoreHighlight && neume == other.neume && eventIds == other.eventIds && syllableNeumeIds == other.syllableNeumeIds

    override fun hashCode(): Int = (neume.hashCode() * 31 + eventIds.hashCode()) * 31 + syllableNeumeIds.hashCode()

    public companion object {
        /** The highlight for a note, with syllables as the score assigns them; null for a note the layout does not draw. */
        public fun of(eventId: String, score: GregorianScore, layout: GregorianLayout): GregorianScoreHighlight? {
            val syllableId = score.neumes.firstOrNull { eventId in it.eventIds }?.syllableId ?: return null
            val syllableEventIds = score.neumes.filter { it.syllableId == syllableId }.flatMap { it.eventIds }.toSet()
            return of(eventId, syllableEventIds, layout)
        }

        /** The highlight for a note, with syllables as a compiled timeline assigns them; null for a note the layout does not draw. */
        public fun of(eventId: String, timeline: GregorianTimeline<*>, layout: GregorianLayout): GregorianScoreHighlight? {
            val event = timeline.events.firstOrNull { it.id == eventId } ?: return null
            val syllableEventIds = timeline.events.filter { it.syllableId == event.syllableId }.map { it.id }.toSet()
            return of(eventId, syllableEventIds, layout)
        }

        private fun of(eventId: String, syllableEventIds: Set<String>, layout: GregorianLayout): GregorianScoreHighlight? {
            val neume = layout.neumeContaining(eventId) ?: return null
            return GregorianScoreHighlight(
                neume = neume,
                eventIds = neume.eventIds.toSet(),
                syllableNeumeIds = layout.neumes.filter { placement -> placement.eventIds.any { it in syllableEventIds } }.map { it.id }.toSet(),
            )
        }
    }
}
