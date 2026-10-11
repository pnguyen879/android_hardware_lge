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
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object CoverClock {

    private const val MAX_ICONS = 5
    private const val ICON_SIZE = 16
    private const val ICON_GAP = 6

    private const val BATTERY_W = 12f
    private const val BODY_TOP = 2f
    private const val BODY_BOTTOM = 20f
    private const val BATTERY_SCALE = 1.1f

    private val BATTERY_BODY =
        Path().apply {
            moveTo(3.5f, 2f)
            cubicTo(3.5f, 1.4477f, 3.9477f, 1f, 4.5f, 1f)
            lineTo(7.5f, 1f)
            cubicTo(8.0523f, 1f, 8.5f, 1.4477f, 8.5f, 2f)
            lineTo(9.5f, 2f)
            cubicTo(10.6046f, 2f, 11.5f, 2.8954f, 11.5f, 4f)
            lineTo(11.5f, 18f)
            cubicTo(11.5f, 19.1046f, 10.6046f, 20f, 9.5f, 20f)
            lineTo(2.5f, 20f)
            cubicTo(1.3954f, 20f, 0.5f, 19.1046f, 0.5f, 18f)
            lineTo(0.5f, 4f)
            cubicTo(0.5f, 2.8954f, 1.3954f, 2f, 2.5f, 2f)
            close()
        }

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
        val compact = virtualH < 100f
        val designH = if (compact) 64f else 128f
        canvas.translate(0f, (virtualH - designH) / 2f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER

        val now = Date()
        val time = DateFormat.getTimeFormat(context).format(now)
        val timeSize = if (compact) 28f else 40f
        paint.typeface = Typeface.create(systemFont("config_bodyFontFamily"), Typeface.BOLD)
        paint.textSize = timeSize
        if (paint.measureText(time) > 220) {
            paint.textSize = timeSize * 220 / paint.measureText(time)
        }
        canvas.drawText(time, 120f, if (compact) 26f else 46f, paint)

        paint.typeface = systemFont("config_bodyFontFamily")
        paint.textSize = if (compact) 12f else 16f
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE d MMM")
        canvas.drawText(
            SimpleDateFormat(pattern, Locale.getDefault()).format(now),
            120f,
            if (compact) 40f else 68f,
            paint,
        )

        if (compact) {
            val hasIcons = CoverNotifications.active.isNotEmpty()
            drawBattery(canvas, paint, level, charging, if (hasIcons) 70f else 120f, 46f, 0.7f, 13f)
            drawNotifications(context, canvas, paint, 175f, 46f, 12, 11f)
        } else {
            drawNotifications(context, canvas, paint, 120f, 76f, ICON_SIZE, 12f)
            drawBattery(canvas, paint, level, charging, 120f, 98f, BATTERY_SCALE, 18f)
        }

        val colors = IntArray(width * height)
        bitmap.getPixels(colors, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return ByteArray(colors.size) { i -> (colors[i] and 0xff).toByte() }
    }

    private fun drawNotifications(
        context: Context,
        canvas: Canvas,
        paint: Paint,
        centerX: Float,
        top: Float,
        iconSize: Int,
        textSize: Float,
    ) {
        val all = CoverNotifications.active
        if (all.isEmpty()) return
        val icons =
            all.take(MAX_ICONS).mapNotNull { sbn ->
                runCatching {
                        sbn.notification.smallIcon
                            ?.loadDrawable(sbn.getPackageContext(context))
                            ?.mutate()
                            ?.apply { setTint(Color.WHITE) }
                    }
                    .getOrNull()
            }
        val extra = all.size - icons.size
        paint.typeface = systemFont("config_bodyFontFamily")
        paint.textSize = textSize
        paint.textAlign = Paint.Align.LEFT
        val extraText = if (extra > 0) "+$extra" else ""
        val extraWidth = if (extra > 0) paint.measureText(extraText) + ICON_GAP else 0f
        val total =
            icons.size * iconSize + (icons.size - 1).coerceAtLeast(0) * ICON_GAP + extraWidth
        var x = centerX - total / 2
        for (icon in icons) {
            icon.setBounds(x.toInt(), top.toInt(), x.toInt() + iconSize, top.toInt() + iconSize)
            icon.draw(canvas)
            x += iconSize + ICON_GAP
        }
        if (extra > 0) canvas.drawText(extraText, x, top + iconSize - 3f, paint)
    }

    private fun drawBattery(
        canvas: Canvas,
        paint: Paint,
        level: Int,
        charging: Boolean,
        centerX: Float,
        top: Float,
        scale: Float,
        textSize: Float,
    ) {
        val percent = if (level < 0) "--%" else "$level%"
        paint.typeface = systemFont("config_bodyFontFamily")
        paint.textSize = textSize
        paint.textAlign = Paint.Align.LEFT
        val iconW = BATTERY_W * scale
        val start = centerX - (iconW + 8f + paint.measureText(percent)) / 2f

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.save()
        canvas.translate(start, top)
        canvas.scale(scale, scale)
        paint.alpha = 0x4d
        canvas.drawPath(BATTERY_BODY, paint)
        val fraction = level.coerceAtLeast(0) / 100f
        if (fraction > 0f) {
            canvas.save()
            canvas.clipPath(BATTERY_BODY)
            paint.alpha = 0xff
            val fillTop = BODY_BOTTOM - (BODY_BOTTOM - BODY_TOP) * fraction
            canvas.drawRect(0f, fillTop, BATTERY_W, BODY_BOTTOM, paint)
            canvas.restore()
        }
        if (charging) {
            paint.color = if (fraction > 0.5f) Color.BLACK else Color.WHITE
            canvas.drawPath(BOLT, paint)
        }
        canvas.restore()

        paint.color = Color.WHITE
        paint.alpha = 0xff
        canvas.drawText(percent, start + iconW + 8f, top + textSize - 1f, paint)
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
