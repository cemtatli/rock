package com.cem.rock

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** One of the hand-drawn icons from design/doodle.py, tinted to the current ink. */
@Composable
fun HandIcon(@DrawableRes res: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 26.dp, label: String? = null) {
    Icon(painterResource(res), contentDescription = label, tint = tint, modifier = modifier.size(size))
}

/** Smooth path through points (Catmull-Rom), the same trick doodle.py uses. */
private fun smoothPath(pts: List<Offset>, closed: Boolean = false): Path = Path().apply {
    if (pts.size < 2) return@apply
    val p = if (closed) pts + pts.take(3) else listOf(pts.first()) + pts + listOf(pts.last())
    val range = if (closed) 1..pts.size else 1 until p.size - 2
    moveTo(p[1].x, p[1].y)
    if (!closed) moveTo(pts.first().x, pts.first().y)
    for (i in range) {
        val (p0, p1, p2, p3) = listOf(p[i - 1], p[i], p[i + 1], p[i + 2])
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}

private fun DrawScope.pen(path: Path, color: Color, width: Dp) =
    drawPath(path, color, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

/** A pencil line across the width. Wobble is seeded (stable) and the path is cached per size. */
@Composable
fun Hairline(color: Color, modifier: Modifier = Modifier, alpha: Float = 0.3f, seed: Int = currentCompositeKeyHash) {
    Spacer(
        modifier.fillMaxWidth().height(4.dp).drawWithCache {
            val r = Random(seed)
            val n = 6
            val amp = 0.9.dp.toPx()
            val pts = (0..n).map { i ->
                Offset(size.width * i / n + if (i in 1 until n) r.nextFloat() * 8f - 4f else 0f, size.height / 2 + (r.nextFloat() * 2 - 1) * amp)
            }
            val path = smoothPath(pts)
            val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind { drawPath(path, color.copy(alpha = alpha), style = stroke) }
        },
    )
}

/** Tally marks, the oldest way to count: strokes in fives, the fifth slashing across. */
@Composable
fun Tally(count: Int, color: Color, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    val stepDp = 6.dp
    val gapDp = 10.dp
    // width = position of the last stroke + a little margin (mirrors the drawing loop below)
    var units = 0f; var rest = count
    while (rest > 0) {
        val g = rest.coerceAtMost(5)
        units += g.coerceAtMost(4) * stepDp.value
        rest -= g
        if (rest > 0) units += gapDp.value - stepDp.value
    }
    val width = if (count == 0) 0.dp else (units - stepDp.value + 6f + 3f).dp
    Spacer(
        modifier.size(width.coerceAtLeast(1.dp), height).drawWithCache {
            val r = Random(count * 31 + 7)
            val step = stepDp.toPx(); val gap = gapDp.toPx(); val h = size.height
            val stroke = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round)
            val paths = mutableListOf<Path>()
            var x = 3.dp.toPx()
            var left = count
            while (left > 0) {
                val inGroup = left.coerceAtMost(5)
                val startX = x
                for (i in 0 until inGroup.coerceAtMost(4)) {
                    val j = { (r.nextFloat() * 2 - 1) * 1.6f }
                    paths += Path().apply { moveTo(x + j(), h * 0.08f + j()); lineTo(x + j() * 1.4f, h * 0.92f + j()) }
                    x += step
                }
                if (inGroup == 5) {
                    paths += Path().apply { moveTo(startX - step * 0.6f, h * 0.75f); lineTo(x - step * 0.3f, h * 0.25f) }
                }
                x += gap - step
                left -= inGroup
            }
            onDrawBehind { paths.forEach { drawPath(it, color, style = stroke) } }
        },
    )
}

/** A wobbly, slightly overshooting outline drawn around the element, like a pen box around a word. */
fun Modifier.handBox(color: Color, seed: Int = 1, width: Dp = 2.2.dp, fill: Color? = null): Modifier = drawBehind {
    val r = Random(seed)
    val j = 1.6.dp.toPx()
    val inset = 2.dp.toPx()
    val w = size.width - inset * 2
    val h = size.height - inset * 2
    // walk the perimeter of a soft rectangle, overshoot past the start
    val corners = listOf(
        Offset(0.06f, 0f), Offset(0.5f, -0.02f), Offset(0.94f, 0.01f), Offset(1.01f, 0.5f),
        Offset(0.95f, 1.0f), Offset(0.5f, 1.03f), Offset(0.05f, 0.99f), Offset(-0.01f, 0.5f),
        Offset(0.07f, 0.02f), Offset(0.35f, 0.0f),
    )
    val pts = corners.map { c ->
        Offset(inset + c.x * w + (r.nextFloat() * 2 - 1) * j, inset + c.y * h + (r.nextFloat() * 2 - 1) * j)
    }
    val path = smoothPath(pts)
    if (fill != null) drawPath(smoothPath(pts.dropLast(1), closed = true), fill)
    pen(path, color, width)
}

/**
 * Typewriter text with worn keys: every letter sits a little crooked.
 * [shown] letters are inked; the rest are laid out but invisible, so typing never shifts layout.
 */
@Composable
fun CrookedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    shown: Int = text.length,
    tilt: Float = 3.5f,
    lift: Dp = 1.6.dp,
) {
    val marks = remember(text) {
        val r = Random(text.hashCode())
        text.map { (r.nextFloat() * 2 - 1) * tilt to (r.nextFloat() * 2 - 1) }
    }
    // read as one word, not letter by letter
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, c ->
            val (rot, dy) = marks[i]
            Text(
                c.toString(),
                style = style,
                color = if (i < shown) color else Color.Transparent,
                modifier = Modifier.graphicsLayer {
                    rotationZ = rot
                    translationY = dy * lift.toPx()
                },
            )
        }
    }
}

/**
 * A chiselled stone outline: straight-ish facets, chipped corners, no overshoot.
 * [chip] is how deep the edge wanders; small for buttons, larger for slabs.
 */
fun Modifier.stone(edge: Color, seed: Int = 1, fill: Color? = null, chip: Dp = 3.dp, width: Dp = 2.dp): Modifier = drawBehind {
    val r = Random(seed)
    val c = chip.toPx()
    val w = size.width
    val h = size.height
    fun j() = (r.nextFloat() * 2 - 1) * c
    // walk the rectangle clockwise; corners are cut, sides get a couple of facets
    val pts = mutableListOf<Offset>()
    val cut = c * 2.6f
    fun side(a: Offset, b: Offset, facets: Int) {
        for (i in 0 until facets) {
            val t = i / facets.toFloat()
            pts += Offset(a.x + (b.x - a.x) * t + if (i == 0) 0f else j() * 0.6f, a.y + (b.y - a.y) * t + if (i == 0) 0f else j() * 0.6f)
        }
    }
    val inset = width.toPx()
    val l = inset + c * 0.5f; val tY = inset + c * 0.5f; val rX = w - inset - c * 0.5f; val bY = h - inset - c * 0.5f
    val f = (w / 70.dp.toPx()).toInt().coerceIn(2, 5)
    val fv = (h / 70.dp.toPx()).toInt().coerceIn(1, 5)
    side(Offset(l + cut + j(), tY + j() * 0.5f), Offset(rX - cut, tY), f)
    side(Offset(rX + j() * 0.5f, tY + cut + j()), Offset(rX, bY - cut), fv)
    side(Offset(rX - cut + j(), bY + j() * 0.5f), Offset(l + cut, bY), f)
    side(Offset(l + j() * 0.5f, bY - cut + j()), Offset(l, tY + cut), fv)
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    if (fill != null) drawPath(path, fill)
    drawPath(path, edge, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
