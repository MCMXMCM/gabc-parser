/// The identity a compiled timeline gives one written note: the name that
/// ties the note to playback and highlighting, and the syllable it is sung on.
///
/// A content pipeline that compiles its own timelines can conform its event
/// type and hand the timeline to
/// ``GregorianScoreParser/parse(gabc:timeline:)``. Without one, the parser
/// numbers the notes itself; see ``GregorianScoreParser/parse(gabc:scoreID:)``.
public protocol GregorianTimelineEvent {
    /// The note's identifier, unique within its score.
    var id: String { get }
    /// The syllable the note is sung on. The notes of one notation group
    /// that carries lyric text must share it.
    var syllableID: String { get }
    /// The note's staff step from the GABC pitch `h`: `h` is 0, `g` is -1
    /// and `i` is 1.
    var relativePitch: Int { get }
}

/// A score's events: one for each pitch letter written in its notation, in
/// the order written. A custos (`f+`) and a note written several times over
/// (`hvv`, `hsss`) are one event each; a clef, a division or a standalone
/// accidental (`ix`) is none.
public protocol GregorianTimeline {
    associatedtype Event: GregorianTimelineEvent

    var events: [Event] { get }
}

/// An event built in code.
public struct GregorianEvent: GregorianTimelineEvent, Codable, Hashable, Sendable, Identifiable {
    public let id: String
    public let syllableID: String
    public let relativePitch: Int

    public init(id: String, syllableID: String, relativePitch: Int) {
        self.id = id
        self.syllableID = syllableID
        self.relativePitch = relativePitch
    }
}

/// A timeline built in code.
public struct GregorianEventTimeline: GregorianTimeline, Codable, Hashable, Sendable {
    public let events: [GregorianEvent]

    public init(events: [GregorianEvent]) {
        self.events = events
    }
}
