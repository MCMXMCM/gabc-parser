import CoreGraphics
import GABCParser

extension GregorianLayout {
    /// The neume whose target holds a point, or the nearest such neume where
    /// targets meet. A custos is no target.
    public func neume(at location: CGPoint) -> GregorianNeumePlacement? {
        neumes
            .filter { $0.hitFrame.contains(location) }
            .min {
                Self.squaredDistance(from: location, to: $0.hitFrame)
                    < Self.squaredDistance(from: location, to: $1.hitFrame)
            }
    }

    private static func squaredDistance(
        from point: CGPoint,
        to frame: CGRect
    ) -> CGFloat {
        let deltaX = point.x - frame.midX
        let deltaY = point.y - frame.midY
        return deltaX * deltaX + deltaY * deltaY
    }
}

/// What to light while one note is sung: the whole neume that holds it, and
/// every neume of the syllable it is sung on, with that syllable's lyric.
public struct GregorianScoreHighlight: Equatable, Sendable {
    /// The neume that holds the note.
    public let neume: GregorianNeumePlacement
    /// The events whose ink is lit: those of the neume.
    public let eventIDs: Set<String>
    /// The neumes of the note's syllable, whose lyrics are lit.
    public let syllableNeumeIDs: Set<String>

    /// The highlight for a note, with syllables as the score assigns them.
    public init?(eventID: String, score: GregorianScore, layout: GregorianLayout) {
        guard let syllableID = score.neumes.first(where: {
            $0.eventIDs.contains(eventID)
        })?.syllableID else {
            return nil
        }
        self.init(
            eventID: eventID,
            syllableEventIDs: Set(
                score.neumes.lazy
                    .filter { $0.syllableID == syllableID }
                    .flatMap(\.eventIDs)
            ),
            layout: layout
        )
    }

    /// The highlight for a note, with syllables as a compiled timeline
    /// assigns them.
    public init?<Timeline: GregorianTimeline>(
        eventID: String,
        timeline: Timeline,
        layout: GregorianLayout
    ) {
        guard let event = timeline.events.first(where: { $0.id == eventID }) else {
            return nil
        }
        self.init(
            eventID: eventID,
            syllableEventIDs: Set(
                timeline.events.lazy
                    .filter { $0.syllableID == event.syllableID }
                    .map(\.id)
            ),
            layout: layout
        )
    }

    private init?(
        eventID: String,
        syllableEventIDs: Set<String>,
        layout: GregorianLayout
    ) {
        guard let neume = layout.neume(containing: eventID) else { return nil }
        self.neume = neume
        eventIDs = Set(neume.eventIDs)
        syllableNeumeIDs = Set(
            layout.neumes.lazy
                .filter { placement in
                    !syllableEventIDs.isDisjoint(with: placement.eventIDs)
                }
                .map(\.id)
        )
    }
}
