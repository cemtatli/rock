package com.cem.rock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

// Two greys only: pale stone paper and graphite. Locked swaps them.
val Sand = Color(0xFFE6E5E2)
val Paint = Color(0xFF0E0E0D)

val Stone = FontFamily(Font(R.font.stone))   // Mansalva: scratched with a stick. One face for everything.

@Composable
fun RockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sand, onPrimary = Paint,
            background = Paint, onBackground = Sand,
            surface = Paint, onSurface = Sand,
        ),
        content = content,
    )
}

object Type {
    val Huge = TextStyle(fontFamily = Stone, fontSize = 60.sp, lineHeight = 66.sp)
    val Mark = TextStyle(fontFamily = Stone, fontSize = 32.sp, lineHeight = 38.sp)
    val Body = TextStyle(fontFamily = Stone, fontSize = 23.sp, lineHeight = 29.sp)
    val Small = TextStyle(fontFamily = Stone, fontSize = 18.sp, lineHeight = 23.sp)
}

/** The hand-drawn rock, in the current ink. Each tag read lands as a small thud. */
@Composable
fun Rock(pulse: Int, ink: Color, modifier: Modifier = Modifier, width: Dp = 250.dp) {
    val squash = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        squash.animateTo(0.86f, tween(70))
        squash.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
    }
    Box(modifier.width(width), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.rock_doodle),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ink),
            modifier = Modifier
                .width(width)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.86f)
                    scaleY = squash.value
                    scaleX = 2f - squash.value
                },
        )
    }
}

/** A campfire drawn three times and flipped through, so the lines flicker like flame. */
@Composable
fun Fire(ink: Color, modifier: Modifier = Modifier, width: Dp = 200.dp) {
    val frames = listOf(R.drawable.fire_1, R.drawable.fire_2, R.drawable.fire_3)
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(140); frame = (frame + 1) % frames.size }
    }
    Image(
        painterResource(frames[frame]),
        contentDescription = null,
        colorFilter = ColorFilter.tint(ink),
        modifier = modifier.width(width),
    )
}

@Composable
fun LineRow(
    label: String,
    value: String,
    fg: Color,
    enabled: Boolean = true,
    valueColor: Color = fg.copy(alpha = 0.5f),
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 18.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.Body, color = fg)
        Spacer(Modifier.weight(1f))
        Text(value, style = Type.Body, color = valueColor)
    }
}


/**
 * Press and hold to confirm. The stone fills while you hold; letting go early rewinds.
 * [tally] shows remaining tries as notches.
 */
@Composable
fun HoldToConfirm(
    label: String,
    fg: Color,
    enabled: Boolean,
    holdMs: Int = 3000,
    tally: Int? = null,
    onDone: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .stone(fg.copy(alpha = 0.5f), seed = 9, chip = 3.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        val left = ((1f - progress.value) * holdMs).toInt()
                        progress.animateTo(1f, tween(left, easing = LinearEasing))
                        onDone()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (job.isActive) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(300)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .padding(4.dp)
                .background(fg.copy(alpha = 0.15f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.Body, color = fg)
            if (tally != null && tally > 0) { HGap(12); Tally(tally, fg) }
        }
    }
}

@Composable
fun Header(
    title: String,
    fg: Color,
    @androidx.annotation.DrawableRes icon: Int,
    iconLabel: String,
    iconFirst: Boolean = false,
    nudge: Boolean = false, // something needs attention behind this icon
    onIcon: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val button = @Composable {
            // a nudged icon rocks a little every few seconds and wears a pebble
            val rock = remember { Animatable(0f) }
            if (nudge) LaunchedEffect(Unit) {
                while (true) {
                    delay(2600)
                    for (a in listOf(-14f, 11f, -7f, 4f, 0f)) rock.animateTo(a, tween(90))
                }
            }
            Box(Modifier.size(48.dp).tap(onClick = onIcon), contentAlignment = Alignment.Center) {
                HandIcon(icon, fg, size = 34.dp, label = iconLabel, modifier = Modifier.graphicsLayer { rotationZ = if (nudge) rock.value else 0f })
                if (nudge) {
                    Image(
                        painterResource(R.drawable.ic_pebble),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(fg),
                        modifier = Modifier.align(Alignment.TopEnd).size(14.dp),
                    )
                }
            }
        }
        if (iconFirst) { button(); HGap(4) }
        CrookedText(title, Type.Mark, fg, tilt = 3f)
        Spacer(Modifier.weight(1f))
        if (!iconFirst) button()
    }
}

/** A word with a wobbly pen box around it: the only kind of button a cave has. */
@Composable
fun Mark(text: String, ink: Color, seed: Int = 3, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .tap(enabled = enabled, onClick = onClick)
            .stone(ink, seed = seed + text.hashCode(), chip = 4.dp)
            .padding(horizontal = 26.dp, vertical = 12.dp),
    ) {
        Text(text, style = Type.Body, color = ink)
    }
}

/** Click without ripple; settles to 0.96 while held for tactile feedback. */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(source, null, enabled = enabled, onClick = onClick)
}

/**
 * Text that types itself out when it changes, with crooked typewriter letters.
 * The first value shows at once.
 */
@Composable
fun TypeText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, tilt: Float = 2.5f) {
    var shown by remember { mutableIntStateOf(text.length) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(text) {
        if (first) { first = false; shown = text.length; return@LaunchedEffect }
        shown = 0
        while (shown < text.length) { delay(24); shown++ }
    }
    // one Row per line; keep the running count so lines type in order
    Column(modifier.clearAndSetSemantics { contentDescription = text }, horizontalAlignment = Alignment.CenterHorizontally) {
        var offset = 0
        text.split("\n").forEach { line ->
            CrookedText(line, style, color, shown = (shown - offset).coerceAtLeast(0), tilt = tilt, lift = 1.dp)
            offset += line.length + 1
        }
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun Centered(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { content() }
