package com.stepq.app.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stepq.app.ui.theme.StepqColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Material 3 "emphasized" easing: quick start, long gentle settle. */
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private fun <T> dur(ms: Int, motion: Boolean, easing: androidx.compose.animation.core.Easing = Emphasized, delay: Int = 0): FiniteAnimationSpec<T> =
    tween(if (motion) ms else 0, if (motion) delay else 0, easing)

/** Going deeper: the new page slides in from the right over the old one, which drifts left and fades. */
fun forwardTransition(motion: Boolean = true): ContentTransform =
    (slideInHorizontally(dur<IntOffset>(400, motion)) { it } + fadeIn(dur(220, motion, EmphasizedDecelerate))) togetherWith
        (slideOutHorizontally(dur<IntOffset>(400, motion)) { -it / 4 } + fadeOut(dur(250, motion, EmphasizedAccelerate)))

/** Going back: the reverse, so the page you leave slides off to the right. */
fun backTransition(motion: Boolean = true): ContentTransform =
    (slideInHorizontally(dur<IntOffset>(400, motion)) { -it / 4 } + fadeIn(dur(250, motion, EmphasizedDecelerate))) togetherWith
        (slideOutHorizontally(dur<IntOffset>(400, motion)) { it } + fadeOut(dur(220, motion, EmphasizedAccelerate, delay = 120)))

/** Siblings (tabs, segments): a short slide towards the one you picked, with a fade-through. */
fun lateralTransition(toRight: Boolean, motion: Boolean = true): ContentTransform {
    val d = if (toRight) 1 else -1
    return (slideInHorizontally(dur<IntOffset>(300, motion)) { d * it / 10 } + fadeIn(dur(220, motion, EmphasizedDecelerate, delay = 60))) togetherWith
        (slideOutHorizontally(dur<IntOffset>(300, motion)) { -d * it / 10 } + fadeOut(dur(110, motion, EmphasizedAccelerate)))
}

/** Bottom-tab switch: fade-through with a slight zoom, as Material recommends for top-level destinations. */
fun tabTransition(toRight: Boolean, motion: Boolean = true): ContentTransform {
    val d = if (toRight) 1 else -1
    return (fadeIn(dur(260, motion, EmphasizedDecelerate, delay = 70)) + scaleIn(dur(320, motion), initialScale = 0.96f) +
        slideInHorizontally(dur<IntOffset>(320, motion)) { d * it / 20 }) togetherWith
        (fadeOut(dur(100, motion, EmphasizedAccelerate)) + scaleOut(dur(200, motion), targetScale = 1.02f))
}

/**
 * Shows one page of a section and animates between pages: forward when the new page is deeper
 * ([depth] larger), back when it is shallower, a fade-through between siblings. While a page
 * deeper than the root is open, Android's back gesture shrinks it under your finger (predictive
 * back) and lets go into the back animation; [onBack] takes it one level up.
 */
@Composable
fun <T> PageHost(
    target: T,
    depth: (T) -> Int,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val motion = LocalMotionEnabled.current
    val scope = rememberCoroutineScope()
    val gesture = remember { Animatable(0f) }
    val currentTarget = androidx.compose.runtime.rememberUpdatedState(target)
    val swipeFromLeft = remember { booleanArrayOf(true) }
    // The page a back gesture was released on: it keeps its shrunken look while it slides away.
    val backFrom = remember { arrayOfNulls<Any?>(1) }
    AnimatedContent(
        targetState = target,
        modifier = modifier.background(StepqColors.Page),
        transitionSpec = {
            val from = depth(initialState)
            val to = depth(targetState)
            val spec = when {
                to > from -> forwardTransition(motion)
                to < from -> backTransition(motion)
                else -> lateralTransition(true, motion)
            }
            // The page on top draws over the one underneath while they move.
            spec.apply { targetContentZIndex = if (to >= from) 1f else 0f } using SizeTransform(clip = false)
        },
        label = "pages",
    ) { page ->
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val shrinks = (page == currentTarget.value && backFrom[0] == null) || page == backFrom[0]
                    val p = if (shrinks) gesture.value else 0f
                    if (p > 0f) {
                        val scale = 1f - 0.1f * p
                        scaleX = scale
                        scaleY = scale
                        translationX = (if (swipeFromLeft[0]) 1 else -1) * 24.dp.toPx() * p
                        transformOrigin = TransformOrigin(if (swipeFromLeft[0]) 1f else 0f, 0.5f)
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp * p)
                        clip = true
                    } else {
                        shape = RectangleShape
                        clip = false
                    }
                }
                .background(StepqColors.White),
        ) { content(page) }
    }
    // Registered after the page (and again whenever it changes) so it handles back first.
    if (onBack != null && depth(target) > 0) {
        key(target) {
            PredictiveBackHandler { events ->
                try {
                    events.collect { e ->
                        swipeFromLeft[0] = e.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT
                        gesture.snapTo(Emphasized.transform(e.progress))
                    }
                    backFrom[0] = target
                    onBack()
                    scope.launch {
                        kotlinx.coroutines.delay(450)
                        backFrom[0] = null
                        gesture.snapTo(0f)
                    }
                } catch (c: CancellationException) {
                    scope.launch { gesture.animateTo(0f, tween(200, easing = Emphasized)) }
                    throw c
                }
            }
        }
    }
}
