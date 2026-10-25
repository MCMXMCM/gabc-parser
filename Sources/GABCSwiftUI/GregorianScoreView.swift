import GABCLayout
import SwiftUI

/// Parses, engraves and draws a score across the width it is offered, as
/// tall as its staffs need.
///
/// The view lays the score out on the main actor when it first learns its
/// width, and again when the width or the score changes. For long documents,
/// prepare ``GregorianPreparedScore`` values away from the main actor and
/// draw them with ``GregorianScoreCanvas``.
public struct GregorianScoreView: View {
    private enum Source: Hashable {
        case gabc(String, scoreID: String?)
        case score(GregorianScore)
    }

    private struct Inputs: Hashable {
        let source: Source
        let metrics: GregorianLayoutMetrics
        let mode: String?
        let opensPiece: Bool
    }

    private struct Request: Equatable {
        let inputs: Inputs
        let width: CGFloat
        let displayScale: CGFloat
    }

    private struct Preparation {
        let request: Request
        let result: Result<GregorianPreparedScore, any Error>
    }

    /// The score parsed from a source, kept while only the width or the
    /// metrics change.
    private struct Parse {
        let source: Source
        let result: Result<GregorianScore, any Error>
    }

    private let inputs: Inputs
    private let highlightedEventID: String?
    private let onTapNeume: ((GregorianNeumePlacement) -> Void)?

    @State private var width: CGFloat?
    @State private var parse: Parse?
    @State private var preparation: Preparation?
    @Environment(\.displayScale) private var displayScale

    /// - Parameters:
    ///   - gabc: A GABC file, or its notation alone.
    ///   - scoreID: A prefix for the names the parser gives the notes; see
    ///     ``GregorianScoreParser/parse(gabc:scoreID:)``.
    ///   - metrics: The score's sizes. Without a display scale, the view
    ///     snaps the staff to the pixels of the screen it is drawn on.
    ///   - mode: The score's mode, shown above a large initial when it names
    ///     a conclusion (`8g`, `7c2`), or when the score opens a piece.
    ///   - opensPiece: Whether the score opens a piece, a hymn, a
    ///     responsory or a part of the Mass, which takes an initial.
    ///   - highlightedEventID: A note to light, with the rest of its neume
    ///     and the lyric of its syllable.
    ///   - onTapNeume: Called with the neume nearest a tap.
    public init(
        gabc: String,
        scoreID: String? = nil,
        metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
        mode: String? = nil,
        opensPiece: Bool = false,
        highlightedEventID: String? = nil,
        onTapNeume: ((GregorianNeumePlacement) -> Void)? = nil
    ) {
        inputs = Inputs(
            source: .gabc(gabc, scoreID: scoreID),
            metrics: metrics,
            mode: mode,
            opensPiece: opensPiece
        )
        self.highlightedEventID = highlightedEventID
        self.onTapNeume = onTapNeume
    }

    /// - Parameters:
    ///   - metrics: The score's sizes. Without a display scale, the view
    ///     snaps the staff to the pixels of the screen it is drawn on.
    ///   - mode: The score's mode, shown above a large initial when it names
    ///     a conclusion (`8g`, `7c2`), or when the score opens a piece.
    ///   - opensPiece: Whether the score opens a piece, a hymn, a
    ///     responsory or a part of the Mass, which takes an initial.
    ///   - highlightedEventID: A note to light, with the rest of its neume
    ///     and the lyric of its syllable.
    ///   - onTapNeume: Called with the neume nearest a tap.
    public init(
        score: GregorianScore,
        metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
        mode: String? = nil,
        opensPiece: Bool = false,
        highlightedEventID: String? = nil,
        onTapNeume: ((GregorianNeumePlacement) -> Void)? = nil
    ) {
        inputs = Inputs(source: .score(score), metrics: metrics, mode: mode, opensPiece: opensPiece)
        self.highlightedEventID = highlightedEventID
        self.onTapNeume = onTapNeume
    }

    public var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.width
            } action: { newWidth in
                width = newWidth
                prepare()
            }
            .onChange(of: inputs) {
                prepare()
            }
            .onChange(of: displayScale) {
                prepare()
            }
    }

    @ViewBuilder
    private var content: some View {
        switch preparation?.result {
        case let .success(prepared):
            GregorianScoreCanvas(
                prepared,
                highlightedEventID: highlightedEventID,
                onTapNeume: onTapNeume
            )
        case let .failure(error):
            ContentUnavailableView {
                Label("Notation unavailable", systemImage: "music.note")
            } description: {
                Text(error.localizedDescription)
            }
        case nil:
            Color.clear.frame(height: 0)
        }
    }

    private func prepare() {
        guard let width, width > 0 else { return }
        let request = Request(inputs: inputs, width: width, displayScale: displayScale)
        guard preparation?.request != request else { return }
        let inputs = inputs
        let parsed: Parse
        if let parse, parse.source == inputs.source {
            parsed = parse
        } else {
            parsed = Parse(
                source: inputs.source,
                result: Result {
                    switch inputs.source {
                    case let .gabc(gabc, scoreID):
                        try GregorianScoreParser.parse(gabc: gabc, scoreID: scoreID)
                    case let .score(score):
                        score
                    }
                }
            )
            parse = parsed
        }
        var metrics = inputs.metrics
        if metrics.displayScale == nil {
            metrics.displayScale = displayScale
        }
        preparation = Preparation(
            request: request,
            result: parsed.result.map { score in
                GregorianPreparedScore(
                    score: score,
                    width: width,
                    metrics: metrics,
                    mode: inputs.mode,
                    opensPiece: inputs.opensPiece
                )
            }
        )
    }
}
