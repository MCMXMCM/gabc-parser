import Foundation
import XCTest

/// Scores whose events and neumes a content compiler and the native
/// engraver agree on: each with the number of its events, and the number of
/// notes in each of its neumes.
struct SharedFixture: Decodable {
    let name: String
    let scoreID: String
    let gabc: String
    let eventCount: Int
    let neumeSizes: [Int]

    static func all() throws -> [SharedFixture] {
        let url = try XCTUnwrap(
            Bundle.module.url(
                forResource: "gabc-engraving-fixtures",
                withExtension: "json",
                subdirectory: "Fixtures"
            )
        )
        return try JSONDecoder().decode(
            [SharedFixture].self,
            from: Data(contentsOf: url)
        )
    }
}
