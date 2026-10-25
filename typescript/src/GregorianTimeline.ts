/**
 * The identity a compiled timeline gives one written note: the name that ties the note to playback and
 * highlighting, and the syllable it is sung on.
 *
 * A content pipeline that compiles its own timelines can give its events this shape and hand the timeline to
 * `GregorianScoreParser.parse`. Without one, the parser numbers the notes itself.
 */
export interface GregorianTimelineEvent {
  /** The note's identifier, unique within its score. */
  readonly id: string;
  /** The syllable the note is sung on. The notes of one notation group that carries lyric text must share it. */
  readonly syllableId: string;
  /** The note's staff step from the GABC pitch `h`: `h` is 0, `g` is -1 and `i` is 1. */
  readonly relativePitch: number;
}

/**
 * A score's events: one for each pitch letter written in its notation, in the order written. A custos (`f+`) and a
 * note written several times over (`hvv`, `hsss`) are one event each; a clef, a division or a standalone accidental
 * (`ix`) is none.
 */
export interface GregorianTimeline<Event extends GregorianTimelineEvent = GregorianTimelineEvent> {
  readonly events: readonly Event[];
}
