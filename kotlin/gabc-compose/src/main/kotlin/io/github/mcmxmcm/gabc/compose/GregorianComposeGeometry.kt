package io.github.mcmxmcm.gabc.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.mcmxmcm.gabc.layout.GregorianPoint
import io.github.mcmxmcm.gabc.layout.GregorianRect
import io.github.mcmxmcm.gabc.layout.GregorianSize

// The layout's geometry as Compose's. A layout point is drawn as a density-independent pixel, as iOS draws it as a
// point.

public fun GregorianPoint.toOffset(): Offset = Offset(x, y)

public fun GregorianSize.toSize(): Size = Size(width, height)

public fun GregorianRect.toRect(): Rect = Rect(left, top, right, bottom)

public fun Offset.toGregorianPoint(): GregorianPoint = GregorianPoint(x, y)
