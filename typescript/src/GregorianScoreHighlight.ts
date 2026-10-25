import type { GregorianPoint, GregorianRect } from "./GregorianGeometry.js";
import type { GregorianLayout, GregorianNeumePlacement } from "./GregorianLayout.js";
import type { GregorianScore } from "./GregorianNotation.js";
import type { GregorianTimeline } from "./GregorianTimeline.js";

/** The neume whose target holds a point, or the nearest such neume where targets meet. A custos is no target. */
export function neumeAt(layout: GregorianLayout, location: GregorianPoint): GregorianNeumePlacement | null {
  let nearest: GregorianNeumePlacement | null = null;
  let nearestDistance = Infinity;
  for (const neume of layout.neumes) {
    if (!neume.hitFrame.containsPoint(location)) continue;
    const distance = squaredDistance(location, neume.hitFrame);
    if (distance < nearestDistance) {
      nearest = neume;
      nearestDistance = distance;
    }
  }
  return nearest;
}

function squaredDistance(point: GregorianPoint, frame: GregorianRect): number {
  const deltaX = point.x - frame.center.x;
  const deltaY = point.y - frame.center.y;
  return deltaX * deltaX + deltaY * deltaY;
}

/** What to light while one note is sung: the whole neume that holds it, and every neume of the syllable it is sung on, with that syllable's lyric. */
export class GregorianScoreHighlight {
  private constructor(
    /** The neume that holds the note. */
    readonly neume: GregorianNeumePlacement,
    /** The events whose ink is lit: those of the neume. */
    readonly eventIds: ReadonlySet<string>,
    /** The neumes of the note's syllable, whose lyrics are lit. */
    readonly syllableNeumeIds: ReadonlySet<string>,
  ) {}

  /**
   * The highlight for a note, with syllables as the score assigns them, or as a compiled timeline does; null for a
   * note the layout does not draw.
   */
  static of(eventId: string, source: GregorianScore | GregorianTimeline, layout: GregorianLayout): GregorianScoreHighlight | null {
    let syllableEventIds: Set<string>;
    if ("elements" in source) {
      const syllableId = source.neumes.find((neume) => neume.eventIds.includes(eventId))?.syllableId;
      if (syllableId == null) return null;
      syllableEventIds = new Set(source.neumes.filter((neume) => neume.syllableId === syllableId).flatMap((neume) => neume.eventIds));
    } else {
      const event = source.events.find((candidate) => candidate.id === eventId);
      if (!event) return null;
      syllableEventIds = new Set(source.events.filter((candidate) => candidate.syllableId === event.syllableId).map((candidate) => candidate.id));
    }
    const neume = layout.neumeContaining(eventId);
    if (!neume) return null;
    return new GregorianScoreHighlight(
      neume,
      new Set(neume.eventIds),
      new Set(layout.neumes.filter((placement) => placement.eventIds.some((id) => syllableEventIds.has(id))).map((placement) => placement.id)),
    );
  }
}
