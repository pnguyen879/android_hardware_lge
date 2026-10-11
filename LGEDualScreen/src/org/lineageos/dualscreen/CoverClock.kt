/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.dualscreen

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LG stock layout:
 *
 *   09:00   MON, FEB 24
 *           [icons] | 100% [batt]
 */
internal object CoverClock {

    private const val MAX_ICONS = 3
    private const val MAX_WIDTH = 224f

    // Horizontal pill like the system icon: body 1.8h wide, small nub on the right.
    private const val BODY_RATIO = 1.8f
    private const val NUB_GAP = 0.06f
    private const val NUB_W = 0.12f
    private const val BATTERY_RATIO = BODY_RATIO + NUB_GAP + NUB_W

    private val BOLT =
        Path().apply {
            moveTo(7f, 5f)
            lineTo(4f, 11f)
            lineTo(5.8f, 11f)
            lineTo(5f, 16.5f)
            lineTo(8f, 10f)
            lineTo(6.2f, 10f)
            close()
        }

    @JvmStatic
    fun render(
        context: Context,
        width: Int,
        height: Int,
        battery: Int,
        charging: Boolean,
    ): ByteArray {
        require(width > 0 && height > 0) { "Bad size ${width}x$height" }
        val level = battery.coerceIn(-1, 100)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val s = width / 240f
        val virtualH = height / s
        canvas.scale(s, s)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        val font = systemFont("config_bodyFontFamily")
        paint.typeface = font

        val now = Date()
        val locale = Locale.getDefault()
        val is24 = DateFormat.is24HourFormat(context)
        val time = SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", locale).format(now)
        val date =
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
                .format(now)
                .uppercase(locale)
        val percent = if (level < 0) "--%" else "$level%"

        val all = CoverNotifications.active
        val icons = loadIcons(context, all)
        val extra = all.size - icons.size
        val extraText = if (extra > 0) "+$extra" else ""

        fun textW(size: Float, text: String): Float {
            paint.typeface = font
            paint.textSize = size
            return paint.measureText(text)
        }

        // Everything scales from the time size t.
        fun dateSize(t: Float) = t * 0.36f
        fun gap(t: Float) = t * 0.09f

        fun rowWidth(t: Float): Float {
            val ds = dateSize(t)
            val g = gap(t)
            var w = 0f
            if (icons.isNotEmpty()) {
                w += icons.size * ds + (icons.size - 1) * g
                if (extra > 0) w += g + textW(ds * 0.9f, extraText)
                w += g * 2 + 1f // divider
            }
            w += textW(ds * 0.9f, percent) + g + BATTERY_RATIO * ds
            return w
        }

        fun totalWidth(t: Float): Float {
            val colW = maxOf(textW(dateSize(t), date), rowWidth(t))
            return textW(t, time) + t * 0.22f + colW
        }

        var t = minOf(44f, virtualH * 0.6f)
        val total0 = totalWidth(t)
        if (total0 > MAX_WIDTH) t *= MAX_WIDTH / total0

        val ds = dateSize(t)
        val g = gap(t)
        val capH = t * 0.72f
        val timeW = textW(t, time)
        val total = totalWidth(t)

        val x0 = 120f - total / 2f
        val baseline = virtualH / 2f + capH / 2f

        // Time
        paint.typeface = font
        paint.textSize = t
        paint.alpha = 0xff
        canvas.drawText(time, x0, baseline, paint)

        val colX = x0 + timeW + t * 0.22f

        // Date: cap top lines up with the time's cap top
        paint.textSize = ds
        canvas.drawText(date, colX, baseline - capH + ds * 0.72f, paint)

        // Row: bottom lines up with the time baseline
        val rowTop = baseline - ds
        var x = colX
        for ((i, icon) in icons.withIndex()) {
            val l = x.toInt()
            val tp = rowTop.toInt()
            icon.setBounds(l, tp, l + ds.toInt(), tp + ds.toInt())
            icon.draw(canvas)
            x += ds + (if (i < icons.size - 1) g else 0f)
        }
        paint.textSize = ds * 0.9f
        val textBase = rowTop + ds / 2f + ds * 0.9f * 0.36f
        if (icons.isNotEmpty()) {
            if (extra > 0) {
                x += g
                paint.alpha = 0xff
                canvas.drawText(extraText, x, textBase, paint)
                x += paint.measureText(extraText)
            }
            x += g
            paint.alpha = 0x99
            canvas.drawRect(x, rowTop, x + 1f, rowTop + ds, paint)
            paint.alpha = 0xff
            x += 1f + g
        }
        canvas.drawText(percent, x, textBase, paint)
        x += paint.measureText(percent) + g
        drawBatteryIcon(canvas, paint, level, charging, x, rowTop, ds)

        val colors = IntArray(width * height)
        bitmap.getPixels(colors, 0, width, 0, 0, width, height)
        bitmap.recycle()
        // HAL wants 8-bit gray. Use luma, not just the blue channel.
        return ByteArray(colors.size) { i ->
            val c = colors[i]
            val r = (c shr 16) and 0xff
            val gr = (c shr 8) and 0xff
            val b = c and 0xff
            ((r * 77 + gr * 150 + b * 29) shr 8).toByte()
        }
    }

    private fun loadIcons(
        context: Context,
        all: List<android.service.notification.StatusBarNotification>,
    ): List<Drawable> =
        all.take(MAX_ICONS).mapNotNull { sbn ->
            runCatching {
                    sbn.notification.smallIcon
                        ?.loadDrawable(sbn.getPackageContext(context))
                        ?.mutate()
                        ?.apply { setTint(Color.WHITE) }
                }
                .getOrNull()
        }

    private fun drawBatteryIcon(
        canvas: Canvas,
        paint: Paint,
        level: Int,
        charging: Boolean,
        x: Float,
        top: Float,
        h: Float,
    ) {
        val bodyW = h * BODY_RATIO
        val r = h * 0.32f
        val fraction = level.coerceAtLeast(0) / 100f
        val body = Path().apply { addRoundRect(x, top, x + bodyW, top + h, r, r, Path.Direction.CW) }

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE

        // Empty part, dim
        paint.alpha = 0x4d
        canvas.drawPath(body, paint)
        // Nub
        val nubH = h * 0.4f
        val nubX = x + bodyW + h * NUB_GAP
        canvas.drawRoundRect(
            nubX,
            top + (h - nubH) / 2f,
            nubX + h * NUB_W,
            top + (h + nubH) / 2f,
            h * 0.06f,
            h * 0.06f,
            paint,
        )
        // Filled part, from the left
        if (fraction > 0f) {
            canvas.save()
            canvas.clipPath(body)
            paint.alpha = 0xff
            canvas.drawRect(x, top, x + bodyW * fraction, top + h, paint)
            canvas.restore()
        }
        // Bolt, centered in the body
        if (charging) {
            val k = h * 0.7f / 11.5f
            canvas.save()
            canvas.translate(x + bodyW / 2f - 6f * k, top + h / 2f - 10.75f * k)
            canvas.scale(k, k)
            paint.color = if (fraction > 0.5f) Color.BLACK else Color.WHITE
            paint.alpha = 0xff
            canvas.drawPath(BOLT, paint)
            canvas.restore()
        }
        paint.color = Color.WHITE
        paint.alpha = 0xff
    }

    private fun systemFont(vararg names: String): Typeface {
        val res = Resources.getSystem()
        for (name in names) {
            val id = res.getIdentifier(name, "string", "android")
            if (id != 0) {
                val family = runCatching { res.getString(id) }.getOrNull()
                if (!family.isNullOrEmpty()) return Typeface.create(family, Typeface.NORMAL)
            }
        }
        return Typeface.DEFAULT
    }
}
