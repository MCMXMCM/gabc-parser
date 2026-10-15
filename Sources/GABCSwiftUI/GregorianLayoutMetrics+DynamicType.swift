import GABCLayout
import SwiftUI

extension GregorianLayoutMetrics {
    /// A lyric scale that follows Dynamic Type, from 0.88 at the smallest
    /// size to 2.05 at the largest accessibility size; 1 at the default.
    public static func lyricScale(for dynamicTypeSize: DynamicTypeSize) -> Double {
        switch dynamicTypeSize {
        case .xSmall: 0.88
        case .small: 0.94
        case .medium: 0.98
        case .large: 1
        case .xLarge: 1.08
        case .xxLarge: 1.16
        case .xxxLarge: 1.25
        case .accessibility1: 1.35
        case .accessibility2: 1.5
        case .accessibility3: 1.65
        case .accessibility4: 1.85
        case .accessibility5: 2.05
        @unknown default: 1
        }
    }
}
