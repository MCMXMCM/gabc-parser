import GABCLayout
import SwiftUI

/// Draws a prepared score at its laid-out size.
///
/// Each staff system is drawn in a `Canvas` of its own, so that systems
/// scrolled out of view can be culled independently. VoiceOver reads the
/// score neume by neume; when `onTapNeume` is given, each neume is a button.
public struct GregorianScoreCanvas: View {
    private let prepared: GregorianPreparedScore
    private let highlightedEventID: String?
    private let onTapNeume: ((GregorianNeumePlacement) -> Void)?

    @Environment(\.gregorianScoreStyle) private var style

    /// - Parameters:
    ///   - highlightedEventID: A note to light, with the rest of its neume
    ///     and the lyric of its syllable.
    ///   - onTapNeume: Called with the neume nearest a tap. A custos is never
    ///     offered.
    public init(
        _ prepared: GregorianPreparedScore,
        highlightedEventID: String? = nil,
        onTapNeume: ((GregorianNeumePlacement) -> Void)? = nil
    ) {
        self.prepared = prepared
        self.highlightedEventID = highlightedEventID
        self.onTapNeume = onTapNeume
    }

    public var body: some View {
        let layout = prepared.layout
        let highlight = highlightedEventID.flatMap {
            GregorianScoreHighlight(eventID: $0, score: prepared.score, layout: layout)
        }

        ZStack(alignment: .topLeading) {
            ForEach(Array(prepared.drawing.tiles.enumerated()), id: \.offset) { _, tile in
                scoreTile(tile)
            }

            ZStack(alignment: .topLeading) {
                if let highlight {
                    highlightLayer(highlight, layout: layout)
                        .id(highlight.neume.id)
                        .transition(
                            .asymmetric(
                                insertion: .identity,
                                removal: .opacity
                            )
                        )
                }
            }
            .frame(
                width: layout.size.width,
                height: layout.size.height,
                alignment: .topLeading
            )
            .animation(.easeOut(duration: 0.4), value: highlight?.neume.id)
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            if let onTapNeume {
                Color.clear
                    .contentShape(Rectangle())
                    .simultaneousGesture(
                        SpatialTapGesture()
                            .onEnded { value in
                                if let neume = layout.neume(at: value.location) {
                                    onTapNeume(neume)
                                }
                            }
                    )
                    .accessibilityHidden(true)
            }
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
        .accessibilityElement(children: .contain)
        .accessibilityChildren {
            ZStack(alignment: .topLeading) {
                ForEach(Array(layout.neumes.enumerated()), id: \.element.id) { index, neume in
                    accessibilityElement(for: neume)
                        .frame(width: neume.hitFrame.width, height: neume.hitFrame.height)
                        .position(x: neume.hitFrame.midX, y: neume.hitFrame.midY)
                        .accessibilityIdentifier(neume.id)
                        .accessibilitySortPriority(Double(layout.neumes.count - index))
                }
            }
            .frame(width: layout.size.width, height: layout.size.height)
        }
    }

    @ViewBuilder
    private func accessibilityElement(for neume: GregorianNeumePlacement) -> some View {
        if let onTapNeume {
            Button {
                onTapNeume(neume)
            } label: {
                Text(Self.accessibilityLabel(for: neume))
            }
        } else {
            Text(Self.accessibilityLabel(for: neume))
        }
    }

    private func highlightLayer(
        _ highlight: GregorianScoreHighlight,
        layout: GregorianLayout
    ) -> some View {
        let color = style.highlight
        return Canvas { context, _ in
            for stroke in layout.strokes {
                guard let eventID = stroke.eventID,
                      highlight.eventIDs.contains(eventID) else {
                    continue
                }
                var path = Path()
                path.move(to: stroke.start)
                path.addLine(to: stroke.end)
                context.stroke(
                    path,
                    with: .color(color),
                    lineWidth: stroke.lineWidth
                )
            }

            for glyph in layout.glyphs {
                guard let eventID = glyph.eventID,
                      highlight.eventIDs.contains(eventID) else {
                    continue
                }
                let path = GregorianGlyphLibrary.path(for: glyph)
                context.fill(
                    path,
                    with: .color(color),
                    style: FillStyle(eoFill: true)
                )
                context.stroke(
                    path,
                    with: .color(color),
                    lineWidth: 1.1
                )
            }

            for lyric in layout.lyrics
                where highlight.syllableNeumeIDs.contains(lyric.neumeID) {
                context.draw(
                    Self.lyricText(lyric).foregroundStyle(color),
                    at: lyric.origin,
                    anchor: .topLeading
                )
            }

            for initial in layout.initials
                where highlight.syllableNeumeIDs.contains(initial.neumeID) {
                context.draw(
                    Self.initialText(initial).foregroundStyle(color),
                    at: initial.origin,
                    anchor: .topLeading
                )
            }
        }
        .frame(
            width: layout.size.width,
            height: layout.size.height,
            alignment: .topLeading
        )
    }

    private func scoreTile(_ tile: GregorianScoreDrawing.Tile) -> some View {
        let style = style
        return Canvas(rendersAsynchronously: false) { context, _ in
            context.translateBy(x: -tile.frame.minX, y: -tile.frame.minY)

            for batch in tile.strokeBatches {
                context.stroke(
                    batch.path,
                    with: .color(style.ink),
                    lineWidth: batch.lineWidth
                )
            }

            for path in tile.glyphPaths {
                context.fill(
                    path,
                    with: .color(style.ink),
                    style: FillStyle(eoFill: true)
                )
            }

            for lyric in tile.lyrics {
                let color = lyric.style == .rubric ? style.rubric : style.ink
                context.draw(
                    Self.lyricText(lyric).foregroundStyle(color),
                    at: lyric.origin,
                    anchor: .topLeading
                )
            }

            for initial in tile.initials {
                if let annotation = initial.annotation,
                   let annotationOrigin = initial.annotationOrigin {
                    context.draw(
                        Self.styled(
                            Text(annotation).font(.custom(initial.fontName, fixedSize: initial.annotationFontSize)),
                            initial.style
                        )
                        .foregroundStyle(style.ink),
                        at: annotationOrigin,
                        anchor: .topLeading
                    )
                }
                context.draw(
                    Self.initialText(initial).foregroundStyle(style.ink),
                    at: initial.origin,
                    anchor: .topLeading
                )
            }
        }
        .frame(width: tile.frame.width, height: tile.frame.height)
        .position(x: tile.frame.midX, y: tile.frame.midY)
        .accessibilityHidden(true)
    }

    // The layout measured each lyric at this size in this font: draw it so,
    // without Dynamic Type scaling it again.
    private static func lyricText(_ lyric: GregorianPlacedLyric) -> Text {
        styled(Text(lyric.text).font(.custom(lyric.fontName, fixedSize: lyric.fontSize)), lyric.style)
    }

    private static func initialText(_ initial: GregorianPlacedInitial) -> Text {
        styled(Text(initial.text).font(.custom(initial.fontName, fixedSize: initial.fontSize)), initial.style)
    }

    private static func styled(_ text: Text, _ style: GregorianLyricStyle) -> Text {
        switch style {
        case .regular, .rubric:
            text
        case .preparatory:
            text.italic()
        case .accented:
            text.bold()
        }
    }

    private static func accessibilityLabel(for neume: GregorianNeumePlacement) -> String {
        let lyric = neume.lyric.isEmpty ? "Chant" : neume.lyric
        let count = neume.noteCount
        return count == 1 ? "\(lyric), one-note neume" : "\(lyric), \(count)-note neume"
    }
}
