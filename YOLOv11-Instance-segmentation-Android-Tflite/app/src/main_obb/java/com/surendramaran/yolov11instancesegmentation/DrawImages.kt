// In file: DrawImages.kt
package com.surendramaran.yolov11instancesegmentation

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

class DrawImages(private val context: Context) {

    private val boxColors = listOf(
        R.color.overlay_orange, R.color.overlay_blue, R.color.overlay_green, R.color.overlay_red,
        R.color.overlay_pink, R.color.overlay_cyan, R.color.overlay_purple, R.color.overlay_gray,
        R.color.overlay_teal, R.color.overlay_yellow
    )

    fun invoke(results: List<OrientedBoxResult>, originalBitmap: Bitmap?): Bitmap {
        // Use the original frame's dimensions for drawing
        val width = originalBitmap?.width ?: 640
        val height = originalBitmap?.height ?: 480
        val overlayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlayBitmap)

        results.forEach { result ->
            val colorResId = boxColors[result.cls % 10]
            drawRotatedBox(canvas, result, width, height, colorResId)
        }
        return overlayBitmap
    }

    private fun drawRotatedBox(canvas: Canvas, box: OrientedBoxResult, width: Int, height: Int, colorResId: Int) {
        val paint = Paint().apply {
            color = ContextCompat.getColor(context, colorResId)
            strokeWidth = 3F
            style = Paint.Style.STROKE
        }

        // Calculate the four corner points of the rotated rectangle
        val corners = getRotatedCorners(box, width, height)

        // Create a path and draw it
        val path = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            lineTo(corners[1].x, corners[1].y)
            lineTo(corners[2].x, corners[2].y)
            lineTo(corners[3].x, corners[3].y)
            close()
        }
        canvas.drawPath(path, paint)

        // Draw the label
        drawTextLabel(canvas, box, corners[0], colorResId)
    }

    private fun getRotatedCorners(box: OrientedBoxResult, imageWidth: Int, imageHeight: Int): Array<PointF> {
        val centerX = box.cx * imageWidth
        val centerY = box.cy * imageHeight
        val boxWidth = box.w * imageWidth
        val boxHeight = box.h * imageHeight
        val angle = box.angle // Angle is in radians

        val cosA = cos(angle)
        val sinA = sin(angle)

        val w_half_cos = (boxWidth / 2) * cosA
        val w_half_sin = (boxWidth / 2) * sinA
        val h_half_cos = (boxHeight / 2) * cosA
        val h_half_sin = (boxHeight / 2) * sinA

        // Calculate corners relative to center
        val corners = Array(4) { PointF() }
        corners[0] = PointF(centerX - w_half_cos + h_half_sin, centerY - w_half_sin - h_half_cos) // Top-left
        corners[1] = PointF(centerX + w_half_cos + h_half_sin, centerY + w_half_sin - h_half_cos) // Top-right
        corners[2] = PointF(centerX + w_half_cos - h_half_sin, centerY + w_half_sin + h_half_cos) // Bottom-right
        corners[3] = PointF(centerX - w_half_cos - h_half_sin, centerY - w_half_sin + h_half_cos) // Bottom-left

        return corners
    }
    
    private fun drawTextLabel(canvas: Canvas, box: OrientedBoxResult, anchorPoint: PointF, colorResId: Int) {
        val textBackgroundPaint = Paint().apply {
            color = ContextCompat.getColor(context, colorResId)
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 24f
        }

        val label = "${box.clsName} ${(box.cnf * 100).toInt()}%"
        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)

        val textWidth = bounds.width()
        val textHeight = bounds.height()
        val padding = 4

        // Draw text background (adjust position if it goes off-screen)
        var left = anchorPoint.x
        var top = anchorPoint.y - textHeight - (2 * padding)
        if (top < 0) {
            top = anchorPoint.y + padding
        }
        if (left + textWidth > canvas.width) {
            left = canvas.width - textWidth - (2 * padding).toFloat()
        }
        
        canvas.drawRect(left, top, left + textWidth + (2 * padding), top + textHeight + (2 * padding), textBackgroundPaint)
        // Draw text
        canvas.drawText(label, left + padding, top + textHeight + padding, textPaint)
    }
}