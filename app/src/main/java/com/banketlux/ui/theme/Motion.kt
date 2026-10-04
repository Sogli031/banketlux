package com.banketlux.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/**
 * Material 3 kretanje. MotionScheme je u ovoj verziji biblioteke internal, pa su
 * tokeni ovde: opruge (spring) za komponente i emphasized easing za prelaze.
 * Sistemska skala trajanja animacija (i "Ukloni animacije") važi i za njih.
 */
object BanketMotion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Prostorno kretanje: pozicija, veličina, rotacija. Blagi odskok. */
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Efekti: providnost i boja. Bez odskoka. */
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
}

/** Redovi liste se uvlače, glatko menjaju mesto i nestaju kad se doda ili ukloni stavka. */
fun LazyItemScope.banketItemMotion(): Modifier = Modifier.animateItem(
    fadeInSpec = BanketMotion.effects(),
    placementSpec = BanketMotion.spatial(),
    fadeOutSpec = BanketMotion.effects()
)

/** Sadržaj se pri prvom prikazu otvori (visina + providnost), umesto da naglo iskoči. */
@Composable
fun BanketAppear(
    modifier: Modifier = Modifier,
    enter: EnterTransition = expandVertically(BanketMotion.spatial()) + fadeIn(BanketMotion.effects()),
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = enter,
        content = content
    )
}

/**
 * Iznos koji "odbroji" do ciljne vrednosti. Sa [fromZero] kreće od nule pri prvom prikazu;
 * inače prvi prikaz je odmah tačan, a animiraju se tek kasnije izmene.
 */
@Composable
fun rememberAnimatedAmount(target: Int, fromZero: Boolean = false): Int {
    val animatable = remember { Animatable(if (fromZero) 0f else target.toFloat()) }
    LaunchedEffect(target) {
        animatable.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(durationMillis = 700, easing = BanketMotion.EmphasizedDecelerate)
        )
    }
    return animatable.value.roundToInt()
}
