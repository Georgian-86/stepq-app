package com.stepq.app.ui.components

import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens

/**
 * False when the phone's "Remove animations" setting is on (animator scale 0), and in
 * screenshot tests. Looping motion (shimmer, Piko's bounce) and celebrations then stay still,
 * and things appear in their final place.
 */
val LocalMotionEnabled = compositionLocalOf { true }

@Composable
fun systemMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) > 0f
    }
}

// ---------------------------------------------------------------------------------------
// Skeletons: grey shapes with a soft light sweep, in the layout of what is loading.
// ---------------------------------------------------------------------------------------

/** A light band sweeping left to right over a placeholder. */
fun Modifier.shimmer(): Modifier = composed {
    val base = StepqColors.Mint
    val highlight = Color.White.copy(alpha = 0.85f)
    if (!LocalMotionEnabled.current) return@composed background(base)
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    background(base).background(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, highlight, Color.Transparent),
            start = androidx.compose.ui.geometry.Offset(x * 600f - 300f, 0f),
            end = androidx.compose.ui.geometry.Offset(x * 600f + 300f, 200f),
        ),
    )
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(StepqDimens.RadiusS)) {
    Box(modifier.clip(shape).shimmer())
}

/** A list row placeholder: avatar circle, two lines of text, a number on the right. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(40.dp), CircleShape)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(14.dp))
            Spacer(Modifier.height(6.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(11.dp))
        }
        SkeletonBlock(Modifier.width(48.dp).height(16.dp))
    }
}

/**
 * Loading state for list screens (Friends, Groups, Challenges, QRuns, search): walking Piko, the
 * app's loader. Bigger lists get a bigger Piko.
 */
@Composable
fun SkeletonList(rows: Int = 6, withCard: Boolean = true, modifier: Modifier = Modifier) {
    PikoLoader(modifier.padding(vertical = 12.dp), pikoSize = if (rows >= 4 || withCard) 110.dp else 80.dp)
}

// ---------------------------------------------------------------------------------------
// Motion helpers
// ---------------------------------------------------------------------------------------

/** Steps that count up to their new value instead of jumping. */
@Composable
fun AnimatedSteps(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = if (motion) tween(900, easing = FastOutSlowInEasing) else tween(0),
        label = "steps",
    )
    Text(formatSteps(shown), style = style, color = color, modifier = modifier)
}

/** 0..1 progress that eases to its new value: rings and bars fill instead of snapping. */
@Composable
fun animatedProgress(target: Float, durationMs: Int = 1000, delayMs: Int = 0): Float {
    val motion = LocalMotionEnabled.current
    val anim = remember { Animatable(if (motion) 0f else target) }
    LaunchedEffect(target, motion) {
        if (motion) anim.animateTo(target, tween(durationMs, delayMs, FastOutSlowInEasing)) else anim.snapTo(target)
    }
    return anim.value
}

/** Fades and slides a piece of content in when it first appears; [index] staggers lists. */
fun Modifier.appear(index: Int = 0, offset: Dp = 16.dp, delayMs: Int = 0): Modifier = composed {
    val motion = LocalMotionEnabled.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) progress.animateTo(1f, tween(380, delayMillis = delayMs + (index.coerceAtMost(10)) * 45, easing = FastOutSlowInEasing))
    }
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { offset.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * px
    }
}

/** Shrinks slightly while pressed, with a springy release: tactile buttons and cards. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Piko's idle bounce: a gentle up-and-down with a little squash. */
fun Modifier.bob(amplitude: Dp = 5.dp, periodMs: Int = 1400): Modifier = composed {
    if (!LocalMotionEnabled.current) return@composed this
    val t by rememberInfiniteTransition(label = "bob").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bobT",
    )
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { amplitude.toPx() }
    graphicsLayer {
        translationY = -t * px
        scaleY = 1f + t * 0.03f
        scaleX = 1f - t * 0.015f
    }
}

/** Pops in from small with a spring, e.g. Piko on "You're all set". */
fun Modifier.popIn(delayMs: Int = 0): Modifier = composed {
    val motion = LocalMotionEnabled.current
    val s = remember { Animatable(if (motion) 0.4f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            kotlinx.coroutines.delay(delayMs.toLong())
            s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
    graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = ((s.value - 0.4f) / 0.6f).coerceIn(0f, 1f) }
}

/**
 * Zooms in past full size and settles back, each time the composable appears. Used for Piko
 * whenever Home opens.
 */
fun Modifier.zoomPulse(peak: Float = 1.22f): Modifier = composed {
    val motion = LocalMotionEnabled.current
    val s = remember { Animatable(if (motion) 0.55f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            s.animateTo(peak, tween(300, easing = FastOutSlowInEasing))
            s.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    graphicsLayer { scaleX = s.value; scaleY = s.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
}

// ---------------------------------------------------------------------------------------
// Lottie
// ---------------------------------------------------------------------------------------

/** Plays a Lottie file from res/raw once; nothing is drawn when motion is off or it's done. */
@Composable
fun LottieOnce(@RawRes res: Int, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    if (!LocalMotionEnabled.current) return
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val progress by animateLottieCompositionAsState(composition, iterations = 1)
    LaunchedEffect(progress) { if (progress >= 1f) onDone() }
    if (progress < 1f) LottieAnimation(composition, { progress }, modifier)
}

/** Keeps a spacer the size of the animation so layout doesn't jump when it finishes. */
@Composable
fun SuccessCheck(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    if (!motion) return
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(com.stepq.app.R.raw.lottie_success))
    val progress by animateLottieCompositionAsState(composition, iterations = 1)
    LottieAnimation(composition, { progress }, modifier.size(size))
}


/**
 * Full-screen green circle that grows from the bottom (where the Log in / Sign up button is)
 * until it covers everything, holds a beat with Piko, then fades to show the next screen.
 */
@Composable
fun SuccessReveal(onDone: () -> Unit) {
    val grow = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, tween(520, easing = Emphasized))
        kotlinx.coroutines.delay(260)
        fade.animateTo(0f, tween(380, easing = LinearEasing))
        onDone()
    }
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer { alpha = fade.value },
    ) {
        val origin = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.9f)
        val far = kotlin.math.hypot(size.width / 2, size.height * 0.9f) * 1.05f
        drawCircle(StepqColors.Leaf, radius = 26.dp.toPx() + (far - 26.dp.toPx()) * grow.value, center = origin)
    }
    if (grow.value > 0.85f) {
        Box(Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { alpha = fade.value }, contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.stepq.app.R.drawable.piko_cheer),
                contentDescription = null,
                modifier = Modifier.size(150.dp).popIn(),
            )
        }
    }
}
