package com.naman.rupeeflow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.ui.theme.AppTheme

enum class MascotPose {
    HAPPY,        // Holding golden Rupee coin with a happy smile
    SAVING,       // Hugging a cute savings coin pouch
    THINKING,     // Tilted ear, paw on chin for empty / filter states
    CELEBRATION,  // Both paws raised with cheerful confetti
    SLEEPING,     // Peaceful snooze with closed eyes and z's
    TRAVEL,       // Traveling bunny with a cute mini backpack
    FRIENDLY      // Waving a paw warmly for Split Pro and friends
}

/**
 * Original RupeeFlow mascot: "Peachy the Bunny".
 * Rendered 100% offline using Jetpack Compose drawing primitives with crisp scaling,
 * zero network dependencies, and subtle breathing/floating micro-animations.
 */
@Composable
fun RupeeBunnyMascot(
    pose: MascotPose,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    animated: Boolean = true
) {
    // Subtle breathing or floating animation
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_anim")
    val floatOffset by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "float"
        )
    } else {
        rememberUpdatedState(0f)
    }

    Box(
        modifier = modifier
            .size(size)
            .offset(y = floatOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = this.size.width
            val canvasH = this.size.height
            val scale = canvasW / 120f

            drawBunny(pose = pose, scale = scale)
        }
    }
}

private fun DrawScope.drawBunny(pose: MascotPose, scale: Float) {
    val bodyCream = Color(0xFFFFFDF8)
    val outlineBrown = Color(0xFF4A3028)
    val innerEarPeach = Color(0xFFFFAD91)
    val blushPink = Color(0xFFF5A6B8)
    val coinGold = Color(0xFFFFD98E)
    val deepPeach = Color(0xFFF58B72)
    val mintGreen = Color(0xFFB9E5D2)
    val softLavender = Color(0xFFDCCBFA)

    val strokeWidth = 2.2f * scale
    val mainStroke = Stroke(width = strokeWidth)

    // Center coordinates
    val centerX = size.width / 2f
    val headCenterY = size.height * 0.52f
    val headRadiusX = 32f * scale
    val headRadiusY = 28f * scale

    // 1. Draw Ears
    when (pose) {
        MascotPose.THINKING -> {
            // Left ear tilted curiously
            drawEar(
                baseX = centerX - 14f * scale,
                baseY = headCenterY - 22f * scale,
                tiltAngle = -22f,
                scale = scale,
                bodyColor = bodyCream,
                innerColor = innerEarPeach,
                stroke = mainStroke
            )
            // Right ear upright
            drawEar(
                baseX = centerX + 14f * scale,
                baseY = headCenterY - 24f * scale,
                tiltAngle = 10f,
                scale = scale,
                bodyColor = bodyCream,
                innerColor = innerEarPeach,
                stroke = mainStroke
            )
        }
        MascotPose.SLEEPING -> {
            // Drooped soft ears
            drawEar(
                baseX = centerX - 16f * scale,
                baseY = headCenterY - 18f * scale,
                tiltAngle = -48f,
                scale = scale * 0.9f,
                bodyColor = bodyCream,
                innerColor = innerEarPeach,
                stroke = mainStroke
            )
            drawEar(
                baseX = centerX + 16f * scale,
                baseY = headCenterY - 18f * scale,
                tiltAngle = 48f,
                scale = scale * 0.9f,
                bodyColor = bodyCream,
                innerColor = innerEarPeach,
                stroke = mainStroke
            )
        }
        MascotPose.CELEBRATION -> {
            // Perked excited ears
            drawEar(
                baseX = centerX - 15f * scale,
                baseY = headCenterY - 25f * scale,
                tiltAngle = -12f,
                scale = scale * 1.05f,
                bodyColor = bodyCream,
                innerColor = innerEarPeach,
                stroke = mainStroke
            )
            drawEar(
                baseX = centerX + 15f * scale,
                baseY = headCenterY - 25f * scale,
                tiltAngle = 12f,
                scale = scale * 1.05f,
                bodyColor = bodyCream,
                innerColor = innerEarPeach,
                stroke = mainStroke
            )
        }
        else -> {
            // Default cheerful upright ears
            drawEar(
                baseX = centerX - 14f * scale,
                baseY = headCenterY - 24f * scale,
                tiltAngle = -8f,
                scale = scale,
                bodyColor = bodyCream,
                innerColor = innerEarPeach,
                stroke = mainStroke
            )
            drawEar(
                baseX = centerX + 14f * scale,
                baseY = headCenterY - 24f * scale,
                tiltAngle = 8f,
                scale = scale,
                bodyColor = bodyCream,
                innerColor = innerEarPeach,
                stroke = mainStroke
            )
        }
    }

    // 2. Travel Backpack (Behind body if TRAVEL pose)
    if (pose == MascotPose.TRAVEL) {
        val packSize = 24f * scale
        drawRoundRect(
            color = mintGreen,
            topLeft = Offset(centerX - 38f * scale, headCenterY + 2f * scale),
            size = Size(packSize, packSize * 1.3f),
            cornerRadius = CornerRadius(6f * scale, 6f * scale)
        )
        drawRoundRect(
            color = outlineBrown,
            topLeft = Offset(centerX - 38f * scale, headCenterY + 2f * scale),
            size = Size(packSize, packSize * 1.3f),
            cornerRadius = CornerRadius(6f * scale, 6f * scale),
            style = mainStroke
        )
    }

    // 3. Body
    val bodyY = headCenterY + 16f * scale
    val bodyWidth = 42f * scale
    val bodyHeight = 34f * scale
    drawOval(
        color = bodyCream,
        topLeft = Offset(centerX - bodyWidth / 2f, bodyY),
        size = Size(bodyWidth, bodyHeight)
    )
    drawOval(
        color = outlineBrown,
        topLeft = Offset(centerX - bodyWidth / 2f, bodyY),
        size = Size(bodyWidth, bodyHeight),
        style = mainStroke
    )

    // 4. Head
    drawOval(
        color = bodyCream,
        topLeft = Offset(centerX - headRadiusX, headCenterY - headRadiusY),
        size = Size(headRadiusX * 2f, headRadiusY * 2f)
    )
    drawOval(
        color = outlineBrown,
        topLeft = Offset(centerX - headRadiusX, headCenterY - headRadiusY),
        size = Size(headRadiusX * 2f, headRadiusY * 2f),
        style = mainStroke
    )

    // 5. Rosy Cheeks
    val cheekRadius = 6f * scale
    val cheekY = headCenterY + 6f * scale
    drawCircle(
        color = blushPink.copy(alpha = 0.7f),
        radius = cheekRadius,
        center = Offset(centerX - 18f * scale, cheekY)
    )
    drawCircle(
        color = blushPink.copy(alpha = 0.7f),
        radius = cheekRadius,
        center = Offset(centerX + 18f * scale, cheekY)
    )

    // 6. Eyes
    val eyeY = headCenterY - 1f * scale
    val eyeSpacing = 13f * scale
    when (pose) {
        MascotPose.SLEEPING -> {
            // Crescent sleeping eyes
            drawArc(
                color = outlineBrown,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(centerX - eyeSpacing - 5f * scale, eyeY - 2f * scale),
                size = Size(10f * scale, 7f * scale),
                style = mainStroke
            )
            drawArc(
                color = outlineBrown,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(centerX + eyeSpacing - 5f * scale, eyeY - 2f * scale),
                size = Size(10f * scale, 7f * scale),
                style = mainStroke
            )
        }
        MascotPose.CELEBRATION -> {
            // Happy closed eyes (^ ^)
            val path1 = Path().apply {
                moveTo(centerX - eyeSpacing - 5f * scale, eyeY + 2f * scale)
                lineTo(centerX - eyeSpacing, eyeY - 4f * scale)
                lineTo(centerX - eyeSpacing + 5f * scale, eyeY + 2f * scale)
            }
            val path2 = Path().apply {
                moveTo(centerX + eyeSpacing - 5f * scale, eyeY + 2f * scale)
                lineTo(centerX + eyeSpacing, eyeY - 4f * scale)
                lineTo(centerX + eyeSpacing + 5f * scale, eyeY + 2f * scale)
            }
            drawPath(path1, outlineBrown, style = mainStroke)
            drawPath(path2, outlineBrown, style = mainStroke)
        }
        else -> {
            // Large cute round eyes with white highlights
            val eyeRadius = 3.6f * scale
            drawCircle(
                color = outlineBrown,
                radius = eyeRadius,
                center = Offset(centerX - eyeSpacing, eyeY)
            )
            drawCircle(
                color = Color.White,
                radius = 1.3f * scale,
                center = Offset(centerX - eyeSpacing + 1f * scale, eyeY - 1.2f * scale)
            )

            drawCircle(
                color = outlineBrown,
                radius = eyeRadius,
                center = Offset(centerX + eyeSpacing, eyeY)
            )
            drawCircle(
                color = Color.White,
                radius = 1.3f * scale,
                center = Offset(centerX + eyeSpacing + 1f * scale, eyeY - 1.2f * scale)
            )
        }
    }

    // 7. Tiny Nose & Mouth
    val noseY = headCenterY + 4f * scale
    drawCircle(
        color = deepPeach,
        radius = 2.4f * scale,
        center = Offset(centerX, noseY)
    )

    // Sweet mouth curves
    val mouthY = noseY + 2.5f * scale
    val mouthPath = Path().apply {
        moveTo(centerX - 4f * scale, mouthY)
        quadraticTo(centerX - 2f * scale, mouthY + 3.2f * scale, centerX, mouthY + 1f * scale)
        quadraticTo(centerX + 2f * scale, mouthY + 3.2f * scale, centerX + 4f * scale, mouthY)
    }
    drawPath(mouthPath, outlineBrown, style = Stroke(width = 1.8f * scale))

    // 8. Paws and Pose Accessories
    when (pose) {
        MascotPose.HAPPY -> {
            // Holding a shiny golden Rupee coin
            val coinCenterX = centerX + 16f * scale
            val coinCenterY = bodyY + 12f * scale
            val coinRadius = 11f * scale

            drawCircle(color = coinGold, radius = coinRadius, center = Offset(coinCenterX, coinCenterY))
            drawCircle(color = outlineBrown, radius = coinRadius, center = Offset(coinCenterX, coinCenterY), style = mainStroke)
            drawCircle(color = Color(0xFFF59E0B), radius = coinRadius * 0.75f, center = Offset(coinCenterX, coinCenterY), style = Stroke(1.2f * scale))

            // Bunny paws holding coin
            drawCircle(color = bodyCream, radius = 4.5f * scale, center = Offset(centerX - 6f * scale, bodyY + 12f * scale))
            drawCircle(color = outlineBrown, radius = 4.5f * scale, center = Offset(centerX - 6f * scale, bodyY + 12f * scale), style = mainStroke)

            drawCircle(color = bodyCream, radius = 4.5f * scale, center = Offset(coinCenterX - 5f * scale, coinCenterY))
            drawCircle(color = outlineBrown, radius = 4.5f * scale, center = Offset(coinCenterX - 5f * scale, coinCenterY), style = mainStroke)
        }

        MascotPose.SAVING -> {
            // Hugging a cute mint coin pouch
            val pouchCenter = Offset(centerX, bodyY + 16f * scale)
            drawCircle(color = mintGreen, radius = 13f * scale, center = pouchCenter)
            drawCircle(color = outlineBrown, radius = 13f * scale, center = pouchCenter, style = mainStroke)

            // Pouch tie
            drawCircle(color = deepPeach, radius = 3.5f * scale, center = Offset(centerX, bodyY + 4f * scale))

            // Two paws hugging pouch
            drawCircle(color = bodyCream, radius = 4.5f * scale, center = Offset(centerX - 12f * scale, bodyY + 14f * scale))
            drawCircle(color = outlineBrown, radius = 4.5f * scale, center = Offset(centerX - 12f * scale, bodyY + 14f * scale), style = mainStroke)

            drawCircle(color = bodyCream, radius = 4.5f * scale, center = Offset(centerX + 12f * scale, bodyY + 14f * scale))
            drawCircle(color = outlineBrown, radius = 4.5f * scale, center = Offset(centerX + 12f * scale, bodyY + 14f * scale), style = mainStroke)
        }

        MascotPose.THINKING -> {
            // One paw on chin
            drawCircle(color = bodyCream, radius = 5f * scale, center = Offset(centerX + 10f * scale, headCenterY + 14f * scale))
            drawCircle(color = outlineBrown, radius = 5f * scale, center = Offset(centerX + 10f * scale, headCenterY + 14f * scale), style = mainStroke)
        }

        MascotPose.CELEBRATION -> {
            // Both paws up in celebration!
            drawCircle(color = bodyCream, radius = 5f * scale, center = Offset(centerX - 24f * scale, headCenterY - 2f * scale))
            drawCircle(color = outlineBrown, radius = 5f * scale, center = Offset(centerX - 24f * scale, headCenterY - 2f * scale), style = mainStroke)

            drawCircle(color = bodyCream, radius = 5f * scale, center = Offset(centerX + 24f * scale, headCenterY - 2f * scale))
            drawCircle(color = outlineBrown, radius = 5f * scale, center = Offset(centerX + 24f * scale, headCenterY - 2f * scale), style = mainStroke)

            // Colorful floating confetti dots
            drawCircle(color = innerEarPeach, radius = 3f * scale, center = Offset(centerX - 35f * scale, headCenterY - 20f * scale))
            drawCircle(color = mintGreen, radius = 2.5f * scale, center = Offset(centerX + 36f * scale, headCenterY - 18f * scale))
            drawCircle(color = coinGold, radius = 3.2f * scale, center = Offset(centerX - 28f * scale, headCenterY + 28f * scale))
            drawCircle(color = softLavender, radius = 2.8f * scale, center = Offset(centerX + 32f * scale, headCenterY + 26f * scale))
            drawCircle(color = deepPeach, radius = 2.2f * scale, center = Offset(centerX + 2f * scale, headCenterY - 42f * scale))
        }

        MascotPose.FRIENDLY -> {
            // Waving right paw!
            drawCircle(color = bodyCream, radius = 5.5f * scale, center = Offset(centerX + 26f * scale, headCenterY + 2f * scale))
            drawCircle(color = outlineBrown, radius = 5.5f * scale, center = Offset(centerX + 26f * scale, headCenterY + 2f * scale), style = mainStroke)

            // Left paw rests on body
            drawCircle(color = bodyCream, radius = 4.5f * scale, center = Offset(centerX - 10f * scale, bodyY + 14f * scale))
            drawCircle(color = outlineBrown, radius = 4.5f * scale, center = Offset(centerX - 10f * scale, bodyY + 14f * scale), style = mainStroke)
        }

        MascotPose.SLEEPING -> {
            // Floating 'Z's
            drawTextZ(x = centerX + 26f * scale, y = headCenterY - 18f * scale, size = 10f * scale, color = deepPeach)
            drawTextZ(x = centerX + 36f * scale, y = headCenterY - 30f * scale, size = 14f * scale, color = deepPeach.copy(alpha = 0.7f))
        }

        MascotPose.TRAVEL -> {
            // Two paws resting cheerfully in front
            drawCircle(color = bodyCream, radius = 4.5f * scale, center = Offset(centerX - 8f * scale, bodyY + 12f * scale))
            drawCircle(color = outlineBrown, radius = 4.5f * scale, center = Offset(centerX - 8f * scale, bodyY + 12f * scale), style = mainStroke)

            drawCircle(color = bodyCream, radius = 4.5f * scale, center = Offset(centerX + 8f * scale, bodyY + 12f * scale))
            drawCircle(color = outlineBrown, radius = 4.5f * scale, center = Offset(centerX + 8f * scale, bodyY + 12f * scale), style = mainStroke)
        }
    }
}

private fun DrawScope.drawEar(
    baseX: Float,
    baseY: Float,
    tiltAngle: Float,
    scale: Float,
    bodyColor: Color,
    innerColor: Color,
    stroke: Stroke,
    strokeColor: Color = Color(0xFF4A3028)
) {
    val earLength = 34f * scale
    val earWidth = 14f * scale

    val rad = Math.toRadians(tiltAngle.toDouble()).toFloat()
    val tipX = baseX + (Math.sin(rad.toDouble()) * earLength).toFloat()
    val tipY = baseY - (Math.cos(rad.toDouble()) * earLength).toFloat()

    // Outer ear pill
    val outerPath = Path().apply {
        moveTo(baseX - earWidth / 2f, baseY)
        cubicTo(
            baseX - earWidth / 1.5f, baseY - earLength * 0.6f,
            tipX - earWidth / 3f, tipY - earWidth / 4f,
            tipX, tipY
        )
        cubicTo(
            tipX + earWidth / 3f, tipY - earWidth / 4f,
            baseX + earWidth / 1.5f, baseY - earLength * 0.6f,
            baseX + earWidth / 2f, baseY
        )
        close()
    }
    drawPath(outerPath, bodyColor)
    drawPath(outerPath, strokeColor, style = stroke)

    // Inner ear pad
    val innerLength = earLength * 0.68f
    val innerWidth = earWidth * 0.5f
    val innerTipX = baseX + (Math.sin(rad.toDouble()) * innerLength).toFloat()
    val innerTipY = baseY - (Math.cos(rad.toDouble()) * innerLength).toFloat()

    val innerPath = Path().apply {
        moveTo(baseX - innerWidth / 2f, baseY - 4f * scale)
        cubicTo(
            baseX - innerWidth / 1.8f, baseY - innerLength * 0.5f,
            innerTipX - innerWidth / 3f, innerTipY,
            innerTipX, innerTipY
        )
        cubicTo(
            innerTipX + innerWidth / 3f, innerTipY,
            baseX + innerWidth / 1.8f, baseY - innerLength * 0.5f,
            baseX + innerWidth / 2f, baseY - 4f * scale
        )
        close()
    }
    drawPath(innerPath, innerColor)
}

private fun DrawScope.drawTextZ(x: Float, y: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x + size, y)
        lineTo(x, y + size)
        lineTo(x + size, y + size)
    }
    drawPath(path, color, style = Stroke(width = size * 0.2f))
}

/**
 * Consistent Peachy Cozy empty state composable featuring the mascot.
 */
@Composable
fun MascotEmptyState(
    pose: MascotPose,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RupeeBunnyMascot(
            pose = pose,
            size = 110.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.primaryAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
