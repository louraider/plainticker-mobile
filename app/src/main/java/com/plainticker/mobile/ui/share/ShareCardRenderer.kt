package com.plainticker.mobile.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberSurface

/**
 * Draws a [ShareCard] into a 1080 by 1350 bitmap with the platform Canvas, off the main thread.
 * A Canvas rather than an off-screen composition: a composition needs a window to measure in, and
 * a picture sent to other people must not depend on the size of the phone that made it.
 *
 * **Amber, always dark.** The card is the founder's approved palette (`AmberDarkColors`), whatever
 * the phone's own setting, so a card looks the same in every feed. Bricolage Grotesque for every
 * word and number (its `wght`/`opsz` axes set per line, `tnum` on the figures only, DESIGN.md
 * section 3), JetBrains Mono for the transaction alone.
 *
 * **One bold thing.** The brand mark's two registration corners, drawn large around the headline:
 * the kept place around a figure the mark stands for (DESIGN.md section 9). Everything else is
 * quiet: facts in a two-column tonal grid (the app's own `FactGrid` anatomy), the link and the
 * lockup at the foot. No gradient, no shadow, no glow.
 *
 * **Nothing clips.** Every line either wraps within its column or shrinks to fit it, and when a
 * card's content would run into the foot, the whole body steps down in scale until it does not.
 */
object ShareCardRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val MARGIN = 88f
    private const val FRAME_INSET = 44f
    private const val CORNER_ARM = 76f
    private const val CORNER_THICKNESS = 10f
    private const val PANEL_RADIUS = 32f
    private const val SEAM = 2f
    private const val MIN_SCALE = 0.6f

    private val colors = AmberDarkColors
    private val ground = colors.surfaceGround.toArgb()
    private val raised = colors.surfaceRaised.toArgb()
    private val primary = colors.textPrimary.toArgb()
    private val secondary = colors.textSecondary.toArgb()
    private val tertiary = colors.textTertiary(AmberSurface.RAISED).toArgb()
    private val amber = colors.actionText.toArgb()
    private val caution = colors.stateCaution.toArgb()

    fun render(context: Context, card: ShareCard, resolve: (Copy) -> String): Bitmap {
        val fonts = Fonts(
            display = ResourcesCompat.getFont(context, R.font.bricolage_grotesque) ?: Typeface.DEFAULT,
            mono = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium) ?: Typeface.MONOSPACE,
        )
        val text = Resolved(
            eyebrow = card.eyebrow?.let(resolve),
            headline = resolve(card.headline),
            factsLabel = card.factsLabel?.let(resolve),
            facts = card.facts.map { fact ->
                ResolvedFact(resolve(fact.label), resolve(fact.value), fact.sub?.let(resolve), fact.span, fact.caution, fact.mono)
            },
            url = resolve(card.url),
            footer = card.footer?.let(resolve),
            wordmark = context.getString(R.string.app_name),
        )
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ground)
        val foot = Foot(text, fonts)
        val footTop = HEIGHT - MARGIN - foot.height
        var scale = 1f
        var body = Body(card.kind, text, fonts, scale)
        while (body.bottom > footTop - 48f && scale > MIN_SCALE) {
            scale -= 0.05f
            body = Body(card.kind, text, fonts, scale)
        }
        body.draw(canvas)
        foot.draw(canvas, footTop)
        return bitmap
    }

    private class Fonts(val display: Typeface, val mono: Typeface)

    private class Resolved(
        val eyebrow: String?,
        val headline: String,
        val factsLabel: String?,
        val facts: List<ResolvedFact>,
        val url: String,
        val footer: String?,
        val wordmark: String,
    )

    private class ResolvedFact(
        val label: String,
        val value: String,
        val sub: String?,
        val span: Int,
        val caution: Boolean,
        val mono: Boolean,
    )

    /** A Bricolage paint at one instance of its axes; [opsz] is clamped to the face's 12 to 96. */
    private fun display(fonts: Fonts, size: Float, color: Int, weight: Int, tnum: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = fonts.display
            textSize = size
            this.color = color
            val opsz = size.coerceIn(12f, 96f).toInt()
            fontVariationSettings = "'wght' $weight, 'opsz' $opsz, 'wdth' 100"
            if (tnum) fontFeatureSettings = "'tnum'"
        }

    private fun mono(fonts: Fonts, size: Float, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = fonts.mono
            textSize = size
            this.color = color
        }

    private fun layout(text: String, paint: TextPaint, width: Float, maxLines: Int, spacing: Float = 1f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, spacing)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    /** Shrinks [paint] until [text] fits [width] on one line, never below 60 percent of its size. */
    private fun fitOneLine(text: String, paint: TextPaint, width: Float): TextPaint {
        val floor = paint.textSize * 0.6f
        while (paint.measureText(text) > width && paint.textSize > floor) paint.textSize -= 1f
        return paint
    }

    private fun Canvas.drawLayout(layout: StaticLayout, x: Float, y: Float) {
        save()
        translate(x, y)
        layout.draw(this)
        restore()
    }

    /** The 46-unit two-corners block of `ic_brand_mark_tight`, scaled to [size] at [x], [y]. */
    private fun Canvas.drawMark(x: Float, y: Float, size: Float, paint: Paint) {
        val u = size / 46f
        listOf(
            floatArrayOf(0f, 0f, 28f, 12f),
            floatArrayOf(0f, 0f, 12f, 28f),
            floatArrayOf(18f, 34f, 46f, 46f),
            floatArrayOf(34f, 18f, 46f, 46f),
        ).forEach { (l, t, r, b) -> drawRect(x + l * u, y + t * u, x + r * u, y + b * u, paint) }
    }

    /** The headline in its corners, then the facts. Laid out at [scale] so the caller can fit it. */
    private class Body(kind: ShareCardKind, private val text: Resolved, private val fonts: Fonts, scale: Float) {
        private val innerLeft = MARGIN + FRAME_INSET
        private val innerWidth = WIDTH - 2 * (MARGIN + FRAME_INSET)
        private val frameTop = MARGIN
        private val eyebrow = text.eyebrow?.let {
            layout(it, display(fonts, 40f * scale, secondary, 600), innerWidth, 1)
        }
        private val headline = layout(
            text.headline,
            display(fonts, (if (kind == ShareCardKind.Stock) 92f else 104f) * scale, primary, 700).apply {
                letterSpacing = -0.01f
            },
            innerWidth,
            maxLines = if (kind == ShareCardKind.Stock) 2 else 3,
            spacing = 0.98f,
        )
        private val eyebrowTop = frameTop + 48f * scale
        private val headlineTop = eyebrowTop + (eyebrow?.let { it.height + 12f * scale } ?: 0f)
        private val frameBottom = headlineTop + headline.height + 48f * scale
        private val label = text.factsLabel?.let { layout(it, display(fonts, 34f * scale, secondary, 600), WIDTH - 2 * MARGIN, 1) }
        private val labelTop = frameBottom + 56f * scale
        private val panelTop = labelTop + (label?.let { it.height + 20f * scale } ?: 0f)
        private val rows: List<List<Cell>> = pair(text.facts).map { row ->
            val cellWidth = if (row.size == 1 && row[0].span >= 2) WIDTH - 2 * MARGIN else (WIDTH - 2 * MARGIN - SEAM) / 2
            row.map { Cell(it, cellWidth, fonts, scale) }
        }
        private val rowHeights = rows.map { row -> row.maxOf { it.height } }
        private val panelBottom = panelTop + rowHeights.sum() + SEAM * (rows.size - 1).coerceAtLeast(0)
        val bottom: Float get() = if (rows.isEmpty()) frameBottom else panelBottom

        fun draw(canvas: Canvas) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            // The two corners around the headline, top left and bottom right, in amber.
            fill.color = amber
            val right = WIDTH - MARGIN
            canvas.drawRect(MARGIN, frameTop, MARGIN + CORNER_ARM, frameTop + CORNER_THICKNESS, fill)
            canvas.drawRect(MARGIN, frameTop, MARGIN + CORNER_THICKNESS, frameTop + CORNER_ARM, fill)
            canvas.drawRect(right - CORNER_ARM, frameBottom - CORNER_THICKNESS, right, frameBottom, fill)
            canvas.drawRect(right - CORNER_THICKNESS, frameBottom - CORNER_ARM, right, frameBottom, fill)
            eyebrow?.let { canvas.drawLayout(it, innerLeft, eyebrowTop) }
            canvas.drawLayout(headline, innerLeft, headlineTop)
            if (rows.isEmpty()) return
            label?.let { canvas.drawLayout(it, MARGIN, labelTop) }
            // One tonal panel, the rows and cells parted by seams of the ground, the way the
            // app's own FactGrid parts its cells.
            fill.color = raised
            canvas.drawRoundRect(RectF(MARGIN, panelTop, right, panelBottom), PANEL_RADIUS, PANEL_RADIUS, fill)
            fill.color = ground
            var y = panelTop
            rows.forEachIndexed { index, row ->
                val height = rowHeights[index]
                var x = MARGIN
                row.forEachIndexed { column, cell ->
                    cell.draw(canvas, x, y)
                    x += cell.width
                    if (column < row.lastIndex) {
                        canvas.drawRect(x, y, x + SEAM, y + height, fill)
                        x += SEAM
                    }
                }
                y += height
                if (index < rows.lastIndex) {
                    canvas.drawRect(MARGIN, y, right, y + SEAM, fill)
                    y += SEAM
                }
            }
        }

        /** Half-width cells two to a row, in order; a span-2 cell, or one left over, takes its row. */
        private fun pair(facts: List<ResolvedFact>): List<List<ResolvedFact>> {
            val out = ArrayList<List<ResolvedFact>>()
            var pending: ResolvedFact? = null
            facts.forEach { fact ->
                if (fact.span >= 2) {
                    pending?.let { out += listOf(it) }
                    pending = null
                    out += listOf(fact)
                } else if (pending == null) {
                    pending = fact
                } else {
                    out += listOf(pending!!, fact)
                    pending = null
                }
            }
            pending?.let { out += listOf(it) }
            return out
        }
    }

    /** One fact: its label over its value, and an optional sub line, inside the cell's padding. */
    private class Cell(fact: ResolvedFact, val width: Float, fonts: Fonts, scale: Float) {
        private val padX = 32f * scale
        private val padY = 28f * scale
        private val inner = width - 2 * padX
        private val label = layout(fact.label, display(fonts, 28f * scale, secondary, 400), inner, 2)
        private val valueText = fact.value
        private val valuePaint = fitOneLine(
            fact.value,
            if (fact.mono) mono(fonts, 40f * scale, primary) else display(fonts, 50f * scale, if (fact.caution) caution else primary, 600, tnum = true),
            inner,
        )
        private val valueHeight = valuePaint.fontMetrics.let { it.descent - it.ascent }
        private val sub = fact.sub?.let { layout(it, display(fonts, 24f * scale, tertiary, 400), inner, 3) }
        private val gap = 10f * scale
        val height: Float = padY + label.height + gap + valueHeight + (sub?.let { gap + it.height } ?: 0f) + padY

        fun draw(canvas: Canvas, x: Float, y: Float) {
            canvas.drawLayout(label, x + padX, y + padY)
            val valueTop = y + padY + label.height + gap
            val text = TextUtils.ellipsize(valueText, valuePaint, inner, TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, x + padX, valueTop - valuePaint.fontMetrics.ascent, valuePaint)
            sub?.let { canvas.drawLayout(it, x + padX, valueTop + valueHeight + gap) }
        }
    }

    /** The lockup and the link on one line, the small print under them. Fixed size, bottom anchored. */
    private class Foot(private val text: Resolved, private val fonts: Fonts) {
        private val wordmark = display(fonts, 40f, primary, 700)

        /** Bricolage's cap height is 660 of 1000 units: the mark is as tall as the wordmark's capitals. */
        private val markSize = 40f * 0.66f
        private val markGap = 16f
        private val wordmarkWidth = wordmark.measureText(text.wordmark)
        private val urlRoom = WIDTH - 2 * MARGIN - markSize - markGap - wordmarkWidth - 40f
        private val url = fitOneLine(text.url, display(fonts, 40f, amber, 600), urlRoom)
        private val lineHeight = 52f
        private val footer = text.footer?.let { layout(it, display(fonts, 26f, tertiary, 400), WIDTH - 2 * MARGIN, 2) }
        val height: Float = lineHeight + (footer?.let { 20f + it.height } ?: 0f)

        fun draw(canvas: Canvas, top: Float) {
            val baseline = top + lineHeight / 2 - (wordmark.fontMetrics.ascent + wordmark.fontMetrics.descent) / 2
            val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = amber }
            // The mark sits on the wordmark's cap height, the way the app's top bar sets it.
            canvas.drawMark(MARGIN, baseline - markSize, markSize, mark)
            canvas.drawText(text.wordmark, MARGIN + markSize + markGap, baseline, wordmark)
            val urlText = TextUtils.ellipsize(text.url, url, urlRoom, TextUtils.TruncateAt.END).toString()
            canvas.drawText(urlText, WIDTH - MARGIN - url.measureText(urlText), baseline, url)
            footer?.let { canvas.drawLayout(it, MARGIN, top + lineHeight + 20f) }
        }
    }
}
