package com.cem.rock

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val ItemHeight = 64.dp
private const val LOOPS = 400

/** A looping number wheel in the scratched face. Snaps to the middle row and ticks on every step. */
@Composable
fun Wheel(count: Int, initial: Int, ink: Color, onChange: (Int) -> Unit) {
    val start = count * (LOOPS / 2) + initial
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val haptics = LocalHapticFeedback.current

    // Index of the row nearest to the centre line.
    val center by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: start
        }
    }
    LaunchedEffect(center) {
        onChange(center % count)
        if (center != start) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(Modifier.width(96.dp).height(ItemHeight * 3), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = ItemHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count * LOOPS) { i ->
                val far = abs(i - center).coerceAtMost(2)
                Box(Modifier.fillMaxWidth().height(ItemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        "%02d".format(i % count),
                        style = TextStyle(fontFamily = Stone, fontSize = 50.sp),
                        color = ink,
                        modifier = Modifier.graphicsLayer {
                            alpha = when (far) { 0 -> 1f; 1 -> 0.28f; else -> 0.1f }
                            val sc = if (far == 0) 1f else 0.82f
                            scaleX = sc; scaleY = sc
                        },
                    )
                }
            }
        }
    }
}
