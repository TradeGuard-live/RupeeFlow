package com.naman.rupeeflow.ui.screens.splash

import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Shader
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.*

private object Ease {
    fun clamp(v: Float, min: Float, max: Float): Float =
        if (v < min) min else if (v > max) max else v

    fun norm(v: Float, a: Float, b: Float): Float =
        if (abs(b - a) < 1e-6f) 0f else clamp((v - a) / (b - a), 0f, 1f)

    fun lerp(a: Float, b: Float, t: Float): Float =
        a + (b - a) * t

    fun outCubic(t: Float): Float =
        1f - (1f - t) * (1f - t) * (1f - t)

    fun inOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    fun outBack(t: Float, s: Float = 1.70158f): Float {
        val c3 = s + 1f
        val tm1 = t - 1f
        return 1f + c3 * tm1 * tm1 * tm1 + s * tm1 * tm1
    }
}

private const val TOTAL_FRAMES = 41f
private const val STAGE_SPLIT_FRAME = 28f
private const val ANIM_DURATION_MS = 2928 // 41 frames at 14 FPS

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFinished by remember { mutableStateOf(false) }

    val finishSplashOnce = rememberUpdatedState {
        if (!isFinished) {
            isFinished = true
            SplashSessionManager.hasPlayedColdStartSplash = true
            onSplashFinished()
        }
    }

    // Back button is absorbed during splash
    BackHandler(enabled = true) {}

    val animFrame = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animFrame.animateTo(
            targetValue = TOTAL_FRAMES,
            animationSpec = tween(
                durationMillis = ANIM_DURATION_MS,
                easing = LinearEasing
            )
        )
        finishSplashOnce.value.invoke()
    }

    val paint = remember {
        Paint().apply {
            isAntiAlias = true
        }
    }

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1018))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to skip
                finishSplashOnce.value.invoke()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameFloat = animFrame.value
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val cx = w * 0.5f
            val cy = h * 0.5f
            val minDim = minOf(w, h)

            // 1. Ambient Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0x1C10B981), // Emerald glow
                        0.55f to Color(0x0C3882F6), // Blue glow
                        1.0f to Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = minDim * 0.55f
                ),
                center = Offset(cx, cy),
                radius = minDim * 0.55f
            )

            // 2. Responsive Sizing
            val iconSize = Ease.clamp(minDim * 0.21f, 60f * density, 120f * density)
            val fontSize = (iconSize * 0.60f).roundToInt().toFloat()
            val gap = (iconSize * 0.22f).roundToInt().toFloat()

            textPaint.textSize = fontSize
            val word = "RupeeFlow"
            val wordWidth = textPaint.measureText(word)
            val lockupWidth = iconSize + gap + wordWidth
            val leftLockupIconX = cx - lockupWidth * 0.5f + iconSize * 0.5f

            val nativeCanvas = drawContext.canvas.nativeCanvas

            if (frameFloat <= STAGE_SPLIT_FRAME) {
                val p1 = Ease.norm(frameFloat, 0f, STAGE_SPLIT_FRAME)
                val popScale = Ease.outBack(Ease.norm(p1, 0.0f, 0.42f), 1.85f)
                val strokeDraw = Ease.outCubic(Ease.norm(p1, 0.06f, 0.66f))
                val textIntro = Ease.norm(p1, 0.12f, 0.88f)
                val slideLeft = Ease.outCubic(Ease.norm(p1, 0.08f, 0.52f))

                val iconX = Ease.lerp(cx, leftLockupIconX, slideLeft)
                val textX = iconX + iconSize * 0.5f + gap

                // Draw Wordmark
                drawWordmark(
                    nativeCanvas = nativeCanvas,
                    startX = textX,
                    centerY = cy,
                    fontSize = fontSize,
                    introT = textIntro,
                    retractT = 0f,
                    clipLeftX = iconX,
                    textPaint = textPaint
                )

                // Draw RupeeFlow Icon
                drawRupeeFlowIcon(
                    nativeCanvas = nativeCanvas,
                    x = iconX,
                    y = cy,
                    size = iconSize * popScale,
                    drawProgress = strokeDraw,
                    paint = paint
                )

                // Draw Sparkle Accents
                drawSparkleAccents(
                    nativeCanvas = nativeCanvas,
                    cx = iconX,
                    cy = cy,
                    iconSize = iconSize,
                    frameFloat = frameFloat,
                    paint = paint
                )
            } else {
                val p2 = Ease.norm(frameFloat, STAGE_SPLIT_FRAME, TOTAL_FRAMES)
                val retractT = Ease.norm(p2, 0.0f, 0.72f)
                val centerGlide = Ease.inOutCubic(Ease.norm(p2, 0.12f, 0.88f))

                val iconX = Ease.lerp(leftLockupIconX, cx, centerGlide)
                val textX = iconX + iconSize * 0.5f + gap

                val beatEnv = sin(Ease.norm(p2, 0.18f, 1.0f) * Math.PI.toFloat())
                val elasticScale = 1f + beatEnv * 0.13f
                val rupeeBeat = 1f + beatEnv * 0.09f
                val baseGrow = Ease.lerp(1.0f, 1.12f, centerGlide)

                if (retractT < 0.98f) {
                    drawWordmark(
                        nativeCanvas = nativeCanvas,
                        startX = textX,
                        centerY = cy,
                        fontSize = fontSize,
                        introT = 1f,
                        retractT = retractT,
                        clipLeftX = iconX,
                        textPaint = textPaint
                    )
                }

                drawRupeeFlowIcon(
                    nativeCanvas = nativeCanvas,
                    x = iconX,
                    y = cy,
                    size = iconSize * baseGrow,
                    iconScale = elasticScale,
                    rupeeScale = rupeeBeat,
                    drawProgress = 1f,
                    glowStrength = beatEnv,
                    paint = paint
                )
            }
        }
    }
}

private fun createSquirclePath(cx: Float, cy: Float, size: Float, n: Float = 4.2f): AndroidPath {
    val path = AndroidPath()
    val half = size * 0.5f
    val steps = 64
    for (i in 0..steps) {
        val a = (i.toFloat() / steps.toFloat()) * (Math.PI * 2).toFloat()
        val cosA = cos(a)
        val sinA = sin(a)
        val x = cx + half * sign(cosA) * (abs(cosA).pow(2f / n))
        val y = cy + half * sign(sinA) * (abs(sinA).pow(2f / n))
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun drawRupeeFlowIcon(
    nativeCanvas: android.graphics.Canvas,
    x: Float,
    y: Float,
    size: Float,
    drawProgress: Float = 1f,
    iconScale: Float = 1f,
    rupeeScale: Float = 1f,
    glowStrength: Float = 0f,
    paint: Paint
) {
    if (size <= 1f) return

    nativeCanvas.save()
    nativeCanvas.translate(x, y)
    nativeCanvas.scale(iconScale, iconScale)

    // 1. Dark Navy Squircle (#18202A) with outer halo / shadow
    val squirclePath = createSquirclePath(0f, 0f, size, 4.2f)
    if (glowStrength > 0.01f) {
        val outerSquircle = createSquirclePath(0f, 0f, size * 1.08f, 4.2f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.08f
        paint.color = AndroidColor.argb((70 * glowStrength).toInt().coerceIn(0, 255), 16, 185, 129)
        nativeCanvas.drawPath(outerSquircle, paint)
    } else {
        val shadowSquircle = createSquirclePath(0f, size * 0.04f, size * 1.02f, 4.2f)
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.argb(80, 0, 0, 0)
        nativeCanvas.drawPath(shadowSquircle, paint)
    }

    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.parseColor("#18202A")
    nativeCanvas.drawPath(squirclePath, paint)

    // 2. Inner Border
    val innerSquircle = createSquirclePath(0f, 0f, (size - 1.5f).coerceAtLeast(1f), 4.2f)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = maxOf(1.5f, size * 0.018f)
    if (glowStrength > 0.01f) {
        val alpha = ((0.35f + glowStrength * 0.55f).coerceIn(0f, 1f) * 255).toInt()
        paint.color = AndroidColor.argb(alpha, 16, 185, 129)
    } else {
        paint.color = AndroidColor.parseColor("#263242")
    }
    nativeCanvas.drawPath(innerSquircle, paint)

    // 3. Rupee Symbol (₹)
    nativeCanvas.save()
    nativeCanvas.scale(rupeeScale, rupeeScale)
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeJoin = Paint.Join.ROUND

    val u = size * 0.48f
    val strokeW = maxOf(3.5f, size * (0.076f + glowStrength * 0.008f))

    val pTop = Ease.norm(drawProgress, 0.0f, 0.5f)
    val pMid = Ease.norm(drawProgress, 0.14f, 0.68f)
    val pStem = Ease.norm(drawProgress, 0.24f, 1.0f)

    // Soft cyan halo under ₹ strokes during pulse beat
    if (glowStrength > 0.01f) {
        paint.color = AndroidColor.argb((90 * glowStrength).toInt().coerceIn(0, 255), 6, 182, 212)
        paint.strokeWidth = strokeW * 1.8f
        if (pTop > 0f) {
            val xStart = -0.35f * u; val xEnd = 0.35f * u; val yTop = -0.33f * u
            nativeCanvas.drawLine(xStart, yTop, Ease.lerp(xStart, xEnd, pTop), yTop, paint)
        }
        if (pMid > 0f) {
            val xStart = -0.35f * u; val xEnd = 0.27f * u; val yMid = -0.10f * u
            nativeCanvas.drawLine(xStart, yMid, Ease.lerp(xStart, xEnd, pMid), yMid, paint)
        }
        if (pStem > 0f) {
            val p0x = -0.14f * u; val p0y = -0.33f * u
            val cp1x = 0.20f * u; val cp1y = -0.33f * u
            val cp2x = 0.20f * u; val cp2y = 0.11f * u
            val p1x = -0.20f * u; val p1y = 0.11f * u
            val p2x = 0.29f * u; val p2y = 0.43f * u
            val haloStem = AndroidPath()
            haloStem.moveTo(p0x, p0y)
            if (pStem <= 0.56f) {
                val t = pStem / 0.56f
                val ax = Ease.lerp(p0x, cp1x, t); val ay = Ease.lerp(p0y, cp1y, t)
                val bx = Ease.lerp(cp1x, cp2x, t); val by = Ease.lerp(cp1y, cp2y, t)
                val cx = Ease.lerp(cp2x, p1x, t); val cy = Ease.lerp(cp2y, p1y, t)
                val dx = Ease.lerp(ax, bx, t); val dy = Ease.lerp(ay, by, t)
                val ex = Ease.lerp(bx, cx, t); val ey = Ease.lerp(by, cy, t)
                val fx = Ease.lerp(dx, ex, t); val fy = Ease.lerp(dy, ey, t)
                haloStem.cubicTo(ax, ay, dx, dy, fx, fy)
            } else {
                haloStem.cubicTo(cp1x, cp1y, cp2x, cp2y, p1x, p1y)
                val tTail = (pStem - 0.56f) / 0.44f
                haloStem.lineTo(Ease.lerp(p1x, p2x, tTail), Ease.lerp(p1y, p2y, tTail))
            }
            nativeCanvas.drawPath(haloStem, paint)
        }
    }

    // Top Bar (Electric Blue #3882F6)
    if (pTop > 0f) {
        paint.color = AndroidColor.parseColor("#3882F6")
        paint.strokeWidth = strokeW
        val xStart = -0.35f * u
        val xEnd = 0.35f * u
        val yTop = -0.33f * u
        nativeCanvas.drawLine(xStart, yTop, Ease.lerp(xStart, xEnd, pTop), yTop, paint)
    }

    // Middle Bar (Cyan #06B6D4)
    if (pMid > 0f) {
        paint.color = AndroidColor.parseColor("#06B6D4")
        paint.strokeWidth = strokeW
        val xStart = -0.35f * u
        val xEnd = 0.27f * u
        val yMid = -0.10f * u
        nativeCanvas.drawLine(xStart, yMid, Ease.lerp(xStart, xEnd, pMid), yMid, paint)
    }

    // Bowl & Tail (Emerald Green #10B981)
    if (pStem > 0f) {
        paint.color = AndroidColor.parseColor("#10B981")
        paint.strokeWidth = strokeW * 1.04f

        val p0x = -0.14f * u; val p0y = -0.33f * u
        val cp1x = 0.20f * u; val cp1y = -0.33f * u
        val cp2x = 0.20f * u; val cp2y = 0.11f * u
        val p1x = -0.20f * u; val p1y = 0.11f * u
        val p2x = 0.29f * u; val p2y = 0.43f * u

        val stemPath = AndroidPath()
        stemPath.moveTo(p0x, p0y)

        if (pStem <= 0.56f) {
            val t = pStem / 0.56f
            val ax = Ease.lerp(p0x, cp1x, t); val ay = Ease.lerp(p0y, cp1y, t)
            val bx = Ease.lerp(cp1x, cp2x, t); val by = Ease.lerp(cp1y, cp2y, t)
            val cx = Ease.lerp(cp2x, p1x, t); val cy = Ease.lerp(cp2y, p1y, t)
            val dx = Ease.lerp(ax, bx, t); val dy = Ease.lerp(ay, by, t)
            val ex = Ease.lerp(bx, cx, t); val ey = Ease.lerp(by, cy, t)
            val fx = Ease.lerp(dx, ex, t); val fy = Ease.lerp(dy, ey, t)
            stemPath.cubicTo(ax, ay, dx, dy, fx, fy)
        } else {
            stemPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p1x, p1y)
            val tTail = (pStem - 0.56f) / 0.44f
            stemPath.lineTo(Ease.lerp(p1x, p2x, tTail), Ease.lerp(p1y, p2y, tTail))
        }
        nativeCanvas.drawPath(stemPath, paint)
    }

    nativeCanvas.restore()
    nativeCanvas.restore()
}

private fun drawSparkleAccents(
    nativeCanvas: android.graphics.Canvas,
    cx: Float,
    cy: Float,
    iconSize: Float,
    frameFloat: Float,
    paint: Paint
) {
    data class Sparkle(val dx: Float, val dy: Float, val start: Float, val dur: Float, val color: Int, val r: Float)
    val sparkles = listOf(
        Sparkle(-0.68f, -0.56f, 6f, 11f, AndroidColor.parseColor("#3882F6"), 6f),
        Sparkle(0.72f, -0.52f, 11f, 11f, AndroidColor.parseColor("#10B981"), 7f),
        Sparkle(-0.62f, 0.58f, 15f, 10f, AndroidColor.parseColor("#06B6D4"), 5.5f)
    )

    for (sp in sparkles) {
        val p = Ease.norm(frameFloat, sp.start, sp.start + sp.dur)
        if (p <= 0f || p >= 1f) continue

        val env = sin(p * Math.PI.toFloat())
        val sx = cx + sp.dx * iconSize * (0.95f + p * 0.22f)
        val sy = cy + sp.dy * iconSize * (0.95f + p * 0.22f)
        val rad = sp.r * env

        nativeCanvas.save()
        val origAlpha = paint.alpha
        paint.alpha = (env * 0.85f * 255).toInt().coerceIn(0, 255)
        nativeCanvas.translate(sx, sy)
        nativeCanvas.rotate(p * 0.8f * 57.2958f)
        paint.style = Paint.Style.FILL
        paint.color = sp.color

        val starPath = AndroidPath()
        starPath.moveTo(0f, -rad)
        starPath.quadTo(0f, 0f, rad, 0f)
        starPath.quadTo(0f, 0f, 0f, rad)
        starPath.quadTo(0f, 0f, -rad, 0f)
        starPath.quadTo(0f, 0f, 0f, -rad)
        starPath.close()

        nativeCanvas.drawPath(starPath, paint)
        paint.alpha = origAlpha
        nativeCanvas.restore()
    }
}

private fun drawWordmark(
    nativeCanvas: android.graphics.Canvas,
    startX: Float,
    centerY: Float,
    fontSize: Float,
    introT: Float,
    retractT: Float,
    clipLeftX: Float,
    textPaint: Paint
) {
    val word = "RupeeFlow"
    nativeCanvas.save()

    if (clipLeftX > 0f) {
        nativeCanvas.clipRect(clipLeftX, 0f, 4000f, 4000f)
    }

    textPaint.textSize = fontSize

    val totalWidth = textPaint.measureText(word)
    val flowShader = LinearGradient(
        startX + totalWidth * 0.5f,
        centerY - fontSize * 0.4f,
        startX + totalWidth,
        centerY + fontSize * 0.4f,
        AndroidColor.parseColor("#10B981"),
        AndroidColor.parseColor("#06B6D4"),
        Shader.TileMode.CLAMP
    )

    val slideBackX = -Ease.inOutCubic(retractT) * (totalWidth + fontSize * 0.65f)

    val fontMetrics = textPaint.fontMetrics
    val textBaseOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f

    var penX = startX
    for (i in 0 until word.length) {
        val ch = word[i].toString()
        val chWidth = textPaint.measureText(ch)
        val isFlowPart = i >= 5

        val charStart = (i.toFloat() / word.length.toFloat()) * 0.52f
        val charEnd = charStart + 0.48f
        val localIn = Ease.norm(introT, charStart, charEnd)
        val springScale = Ease.outBack(localIn, 1.85f)
        val alpha = (Ease.clamp(localIn * 1.45f, 0f, 1f) * (1f - Ease.norm(retractT, 0.45f, 0.95f))).coerceIn(0f, 1f)

        if (alpha > 0.01f && springScale > 0.01f) {
            val liftY = (1f - Ease.outCubic(localIn)) * fontSize * 0.36f
            val drawX = penX + slideBackX

            nativeCanvas.save()
            textPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            nativeCanvas.translate(drawX + chWidth * 0.5f, centerY + liftY)
            nativeCanvas.scale(springScale, springScale)

            if (isFlowPart) {
                textPaint.shader = flowShader
                textPaint.color = AndroidColor.WHITE
            } else {
                textPaint.shader = null
                textPaint.color = AndroidColor.parseColor("#f8fafc")
            }
            nativeCanvas.drawText(ch, -chWidth * 0.5f, textBaseOffset, textPaint)
            nativeCanvas.restore()
        }

        penX += chWidth
    }

    textPaint.shader = null
    nativeCanvas.restore()
}
