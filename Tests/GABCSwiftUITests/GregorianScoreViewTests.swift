#if os(macOS)
import AppKit
import GABCSwiftUI
import SwiftUI
import XCTest

final class GregorianScoreViewTests: XCTestCase {
    @MainActor
    func testScoreViewTakesTheHeightOfItsStaffsAtTheWidthItIsOffered() async throws {
        let gabc = "%% (c4) " + Array(repeating: "Al(f)le(gh)lú(h)ia.(g.) (;)", count: 6)
            .joined(separator: " ") + " (::)"
        let expected = GregorianPreparedScore(
            score: try GregorianScoreParser.parse(gabc: gabc),
            width: 320
        ).layout
        XCTAssertGreaterThan(expected.staffs.count, 1)

        let host = NSHostingView(rootView: GregorianScoreView(gabc: gabc).frame(width: 320))
        let window = NSWindow(
            contentRect: CGRect(x: 0, y: 0, width: 320, height: 800),
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        window.contentView = host

        // The view learns its width from the first layout pass, and lays
        // the score out in the next.
        for _ in 0..<50 where abs(host.fittingSize.height - expected.size.height) > 0.5 {
            host.layoutSubtreeIfNeeded()
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertEqual(host.fittingSize.height, expected.size.height, accuracy: 0.5)
    }
}
#endif
