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
            drawRotatedBox(canvas, result, colorResId)
        }
        return overlayBitmap
    }

    private fun drawRotatedBox(canvas: Canvas, box: OrientedBoxResult, colorResId: Int) {
        val paint = Paint().apply {
            color = ContextCompat.getColor(context, colorResId)
            strokeWidth = 3F
            style = Paint.Style.STROKE
        }

        // Calculate the four corner points of the rotated rectangle
        val corners = getRotatedCorners(box)

        // Create a path and draw it
        val path = Path().apply {
            moveTo(corners[0].x, corners[0].y) // Top-Left
            lineTo(corners[1].x, corners[1].y) // Top-Right
            lineTo(corners[2].x, corners[2].y) // Bottom-Right
            lineTo(corners[3].x, corners[3].y) // Bottom-Left
            close()
        }
        canvas.drawPath(path, paint)

        // Draw the label at the first corner (Top-Left)
        drawTextLabel(canvas, box, corners[0], colorResId)
    }

    /**
     * Calculates the four corner points of a rotated box.
     * This logic is a direct translation of the python `xywhr2xyxyxyxy` function.
     */
    private fun getRotatedCorners(box: OrientedBoxResult): Array<PointF> {
        // Coordinates are already scaled to the original image size
        val centerX = box.cx
        val centerY = box.cy
        val boxWidth = box.w
        val boxHeight = box.h
        val angle = box.angle // Angle is in radians

        val cosA = cos(angle)
        val sinA = sin(angle)

        val w_half = boxWidth / 2
        val h_half = boxHeight / 2

        // Vector 1 (points from center to right edge of box, rotated)
        val vec1x = w_half * cosA
        val vec1y = w_half * sinA
        
        // Vector 2 (points from center to top edge of box, rotated)
        val vec2x = -h_half * sinA
        val vec2y = h_half * cosA

        val corners = Array(4) { PointF() }
        // pt4 in python (ctr - vec1 + vec2)
        corners[0] = PointF(centerX - vec1x + vec2x, centerY - vec1y + vec2y) // Top-Left
        // pt1 in python (ctr + vec1 + vec2)
        corners[1] = PointF(centerX + vec1x + vec2x, centerY + vec1y + vec2y) // Top-Right
        // pt2 in python (ctr + vec1 - vec2)
        corners[2] = PointF(centerX + vec1x - vec2x, centerY + vec1y - vec2y) // Bottom-Right
        // pt3 in python (ctr - vec1 - vec2)
        corners[3] = PointF(centerX - vec1x - vec2x, centerY - vec1y - vec2y) // Bottom-Left

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
        if (left < 0) {
            left = 0f
        }
        if (left + textWidth > canvas.width) {
            left = canvas.width - textWidth - (2 * padding).toFloat()
        }
        
        canvas.drawRect(left, top, left + textWidth + (2 * padding), top + textHeight + (2 * padding), textBackgroundPaint)
        // Draw text
        canvas.drawText(label, left + padding, top + textHeight + padding, textPaint)
    }
}