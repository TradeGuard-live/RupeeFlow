package com.naman.rupeeflow.ui.screens.savings

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.ui.theme.AppTheme

/**
 * Cute Bunny saving coins mascot illustration.
 * Features cute rabbit ears, rosy cheeks, and shining rupee coin.
 */
@Composable
fun BunnySavingCoinsIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bunny_bounce")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(
        modifier = modifier
            .size(100.dp)
            .padding(4.dp)
    ) {
        val w = size.width
        val h = size.height

        val peach = Color(0xFFFFAD91)
        val deepPeach = Color(0xFFF58B72)
        val softPink = Color(0xFFF5A6B8)
        val mint = Color(0xFFB9E5D2)
        val coinGold = Color(0xFFFFD166)
        val darkEye = Color(0xFF4A3028)
        val white = Color.White

        // Soft mint glow circle in background
        drawCircle(
            color = mint.copy(alpha = 0.35f),
            radius = w * 0.45f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        val bunnyY = bounceOffset

        // Left ear
        drawRoundRect(
            color = white,
            topLeft = Offset(w * 0.30f, h * 0.10f + bunnyY),
            size = Size(w * 0.14f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )
        drawRoundRect(
            color = softPink,
            topLeft = Offset(w * 0.33f, h * 0.16f + bunnyY),
            size = Size(w * 0.08f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Right ear
        drawRoundRect(
            color = white,
            topLeft = Offset(w * 0.56f, h * 0.10f + bunnyY),
            size = Size(w * 0.14f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )
        drawRoundRect(
            color = softPink,
            topLeft = Offset(w * 0.59f, h * 0.16f + bunnyY),
            size = Size(w * 0.08f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Bunny Head
        drawCircle(
            color = white,
            radius = w * 0.26f,
            center = Offset(w * 0.50f, h * 0.52f + bunnyY)
        )

        // Cute Eyes
        drawCircle(
            color = darkEye,
            radius = w * 0.035f,
            center = Offset(w * 0.42f, h * 0.50f + bunnyY)
        )
        drawCircle(
            color = white,
            radius = w * 0.012f,
            center = Offset(w * 0.43f, h * 0.49f + bunnyY)
        )

        drawCircle(
            color = darkEye,
            radius = w * 0.035f,
            center = Offset(w * 0.58f, h * 0.50f + bunnyY)
        )
        drawCircle(
            color = white,
            radius = w * 0.012f,
            center = Offset(w * 0.59f, h * 0.49f + bunnyY)
        )

        // Cheeks
        drawCircle(
            color = softPink.copy(alpha = 0.6f),
            radius = w * 0.04f,
            center = Offset(w * 0.36f, h * 0.56f + bunnyY)
        )
        drawCircle(
            color = softPink.copy(alpha = 0.6f),
            radius = w * 0.04f,
            center = Offset(w * 0.64f, h * 0.56f + bunnyY)
        )

        // Nose
        drawCircle(
            color = deepPeach,
            radius = w * 0.02f,
            center = Offset(w * 0.50f, h * 0.54f + bunnyY)
        )

        // Rupee Coin Held in front
        drawCircle(
            color = coinGold,
            radius = w * 0.16f,
            center = Offset(w * 0.50f, h * 0.74f)
        )
        drawCircle(
            color = Color(0xFFF4A261),
            radius = w * 0.16f,
            center = Offset(w * 0.50f, h * 0.74f),
            style = Stroke(width = 2.5f)
        )
        // Little Rupee Sign Lines
        drawLine(
            color = darkEye,
            start = Offset(w * 0.45f, h * 0.69f),
            end = Offset(w * 0.55f, h * 0.69f),
            strokeWidth = 2.5f
        )
        drawLine(
            color = darkEye,
            start = Offset(w * 0.45f, h * 0.73f),
            end = Offset(w * 0.55f, h * 0.73f),
            strokeWidth = 2.5f
        )
        drawLine(
            color = darkEye,
            start = Offset(w * 0.50f, h * 0.73f),
            end = Offset(w * 0.50f, h * 0.79f),
            strokeWidth = 2.5f
        )
    }
}

/**
 * Cute Piggy Bank Illustration.
 */
@Composable
fun PiggyBankIllustration(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(100.dp)
            .padding(4.dp)
    ) {
        val w = size.width
        val h = size.height

        val peach = Color(0xFFFFAD91)
        val deepPeach = Color(0xFFF58B72)
        val softPink = Color(0xFFF5A6B8)
        val darkEye = Color(0xFF4A3028)
        val coinGold = Color(0xFFFFD166)

        // Background soft circle
        drawCircle(
            color = peach.copy(alpha = 0.25f),
            radius = w * 0.44f,
            center = Offset(w * 0.50f, h * 0.50f)
        )

        // Coin dropping into piggy bank
        drawCircle(
            color = coinGold,
            radius = w * 0.11f,
            center = Offset(w * 0.50f, h * 0.18f)
        )
        drawCircle(
            color = Color(0xFFE76F51),
            radius = w * 0.11f,
            center = Offset(w * 0.50f, h * 0.18f),
            style = Stroke(width = 2f)
        )

        // Piggy Ears
        val leftEar = Path().apply {
            moveTo(w * 0.28f, h * 0.38f)
            lineTo(w * 0.22f, h * 0.24f)
            lineTo(w * 0.38f, h * 0.32f)
            close()
        }
        drawPath(leftEar, deepPeach)

        // Piggy Body
        drawRoundRect(
            color = softPink,
            topLeft = Offset(w * 0.20f, h * 0.34f),
            size = Size(w * 0.60f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
        )

        // Coin Slot
        drawRoundRect(
            color = deepPeach,
            topLeft = Offset(w * 0.44f, h * 0.34f),
            size = Size(w * 0.12f, h * 0.035f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Cute Snout
        drawRoundRect(
            color = peach,
            topLeft = Offset(w * 0.65f, h * 0.50f),
            size = Size(w * 0.20f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
        drawCircle(
            color = darkEye,
            radius = w * 0.02f,
            center = Offset(w * 0.72f, h * 0.59f)
        )
        drawCircle(
            color = darkEye,
            radius = w * 0.02f,
            center = Offset(w * 0.80f, h * 0.59f)
        )

        // Eye
        drawCircle(
            color = darkEye,
            radius = w * 0.035f,
            center = Offset(w * 0.54f, h * 0.48f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.012f,
            center = Offset(w * 0.55f, h * 0.47f)
        )

        // Cheeks
        drawCircle(
            color = deepPeach.copy(alpha = 0.5f),
            radius = w * 0.04f,
            center = Offset(w * 0.48f, h * 0.58f)
        )

        // Little Feet
        drawRoundRect(
            color = deepPeach,
            topLeft = Offset(w * 0.28f, h * 0.78f),
            size = Size(w * 0.10f, h * 0.10f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = deepPeach,
            topLeft = Offset(w * 0.54f, h * 0.78f),
            size = Size(w * 0.10f, h * 0.10f),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }
}

/**
 * Cute Goal Celebration Illustration.
 */
@Composable
fun GoalCelebrationIllustration(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(100.dp)
            .padding(4.dp)
    ) {
        val w = size.width
        val h = size.height

        val gold = Color(0xFFFFD166)
        val deepGold = Color(0xFFF4A261)
        val mint = Color(0xFFB9E5D2)
        val peach = Color(0xFFFFAD91)
        val pink = Color(0xFFF5A6B8)

        // Festive Confetti Dots
        drawCircle(color = pink, radius = w * 0.03f, center = Offset(w * 0.15f, h * 0.20f))
        drawCircle(color = mint, radius = w * 0.025f, center = Offset(w * 0.85f, h * 0.25f))
        drawCircle(color = peach, radius = w * 0.03f, center = Offset(w * 0.22f, h * 0.80f))
        drawCircle(color = gold, radius = w * 0.025f, center = Offset(w * 0.78f, h * 0.75f))

        // Trophy Cup Body
        val cupPath = Path().apply {
            moveTo(w * 0.30f, h * 0.25f)
            lineTo(w * 0.70f, h * 0.25f)
            lineTo(w * 0.65f, h * 0.55f)
            quadraticTo(w * 0.50f, h * 0.70f, w * 0.35f, h * 0.55f)
            close()
        }
        drawPath(cupPath, gold)

        // Trophy Handles
        drawRoundRect(
            color = deepGold,
            topLeft = Offset(w * 0.20f, h * 0.30f),
            size = Size(w * 0.12f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = 3.5f)
        )
        drawRoundRect(
            color = deepGold,
            topLeft = Offset(w * 0.68f, h * 0.30f),
            size = Size(w * 0.12f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = 3.5f)
        )

        // Star in Trophy
        drawCircle(color = Color.White, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.42f))

        // Trophy Stem & Base
        drawRoundRect(
            color = deepGold,
            topLeft = Offset(w * 0.46f, h * 0.66f),
            size = Size(w * 0.08f, h * 0.12f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = deepGold,
            topLeft = Offset(w * 0.32f, h * 0.76f),
            size = Size(w * 0.36f, h * 0.10f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
    }
}

/**
 * Cute Empty Savings History Illustration.
 */
@Composable
fun EmptySavingsHistoryIllustration(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(90.dp)
            .padding(4.dp)
    ) {
        val w = size.width
        val h = size.height

        val mint = Color(0xFFB9E5D2)
        val peach = Color(0xFFFFAD91)
        val darkEye = Color(0xFF4A3028)

        // Background soft aura
        drawCircle(
            color = mint.copy(alpha = 0.3f),
            radius = w * 0.45f,
            center = Offset(w * 0.50f, h * 0.50f)
        )

        // Cute Jar
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.30f, h * 0.30f),
            size = Size(w * 0.40f, h * 0.50f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
        drawRoundRect(
            color = peach,
            topLeft = Offset(w * 0.30f, h * 0.30f),
            size = Size(w * 0.40f, h * 0.50f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(width = 3f)
        )

        // Jar Lid
        drawRoundRect(
            color = peach,
            topLeft = Offset(w * 0.33f, h * 0.24f),
            size = Size(w * 0.34f, h * 0.08f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Cheerful face on the jar
        drawCircle(color = darkEye, radius = w * 0.025f, center = Offset(w * 0.43f, h * 0.50f))
        drawCircle(color = darkEye, radius = w * 0.025f, center = Offset(w * 0.57f, h * 0.50f))

        // Cute smile
        drawArc(
            color = darkEye,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.46f, h * 0.54f),
            size = Size(w * 0.08f, h * 0.06f),
            style = Stroke(width = 2f)
        )
    }
}
