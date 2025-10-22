// In file: ObjectDetector.kt
package com.surendramaran.yolov11instancesegmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import com.surendramaran.yolov11instancesegmentation.MetaData.extractNamesFromLabelFile
import com.surendramaran.yolov11instancesegmentation.MetaData.extractNamesFromMetadata
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ObjectDetector(
    context: Context,
    modelPath: String,
    labelPath: String?,
    private val detectorListener: DetectorListener,
    private val message: (String) -> Unit
) {
    private var interpreter: Interpreter
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numDetections = 0

    // For letterboxing
    private var ratio: Float = 1.0f
    private var padX: Float = 0.0f
    private var padY: Float = 0.0f
    private val grayPaint = Paint().apply { color = Color.rgb(114, 114, 114) }

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    init {
        val options = Interpreter.Options()
        options.setNumThreads(4)

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)

        // Extract labels
        labels.addAll(extractNamesFromMetadata(model))
        if (labels.isEmpty()) {
            labelPath?.let { labels.addAll(extractNamesFromLabelFile(context, it)) }
        }
        if (labels.isEmpty()) {
            message("Model metadata not found, using default class names")
            labels.addAll(listOf("front", "back", "other", "flash")) // Your 4 classes
        }

        val inputShape = interpreter.getInputTensor(0)?.shape()
        val outputShape = interpreter.getOutputTensor(0)?.shape()

        inputShape?.let {
            tensorWidth = it[1] // 640
            tensorHeight = it[2] // 640
        }

        outputShape?.let {
            // [1, 50, 7]
            numDetections = it[1] // 50
        }
    }

    fun close() {
        interpreter.close()
    }

    fun invoke(frame: Bitmap) {
        if (tensorWidth == 0 || tensorHeight == 0) {
            detectorListener.onError("Interpreter not initialized properly")
            return
        }

        var preProcessTime = SystemClock.uptimeMillis()
        val imageBuffer = preProcess(frame)
        preProcessTime = SystemClock.uptimeMillis() - preProcessTime

        // Output shape is [1, 50, 7]
        // 7 values are: cx, cy, w, h, confidence_score, class_index, angle
        val outputBuffer = TensorBuffer.createFixedSize(
            intArrayOf(1, numDetections, 7),
            OUTPUT_IMAGE_TYPE
        )

        var interfaceTime = SystemClock.uptimeMillis()
        interpreter.run(imageBuffer.buffer, outputBuffer.buffer.rewind())
        interfaceTime = SystemClock.uptimeMillis() - interfaceTime

        var postProcessTime = SystemClock.uptimeMillis()
        val results = postProcess(outputBuffer.floatArray, frame.width, frame.height)
        postProcessTime = SystemClock.uptimeMillis() - postProcessTime

        if (results.isEmpty()) {
            detectorListener.onEmpty()
        } else {
            detectorListener.onDetect(
                preProcessTime = preProcessTime,
                interfaceTime = interfaceTime,
                postProcessTime = postProcessTime,
                results = results
            )
        }
    }

    /**
     * Post-processes the model output, filters by confidence, regularizes,
     * and scales boxes back to the original frame size.
     */
    private fun postProcess(outputArray: FloatArray, frameWidth: Int, frameHeight: Int): List<OrientedBoxResult> {
        val results = mutableListOf<OrientedBoxResult>()
        // Model output: [cx, cy, w, h, conf, cls_id, angle_radians]
        for (i in 0 until numDetections) {
            val offset = i * 7
            val confidence = outputArray[offset + 4]

            // Stop when we see a detection with confidence 0, as the rest are padding
            if (confidence == 0f) break

            if (confidence >= CONFIDENCE_THRESHOLD) {
                var boxW = outputArray[offset + 2]
                var boxH = outputArray[offset + 3]
                var angleRad = outputArray[offset + 6]

                // --- 1. Regularize rboxes (from python implementation) ---
                if ((angleRad % Math.PI) >= (Math.PI / 2)) {
                    val temp = boxW
                    boxW = boxH
                    boxH = temp
                }
                angleRad %= (Math.PI / 2).toFloat()

                // --- 2. Scale boxes (reverse letterboxing) ---
                // Coords are [0,1] relative to 640x640 letterboxed image
                // We scale them back to original frame.width x frame.height
                val scaledCx = (outputArray[offset] * tensorWidth - padX) / ratio
                val scaledCy = (outputArray[offset + 1] * tensorHeight - padY) / ratio
                val scaledW = (boxW * tensorWidth) / ratio
                val scaledH = (boxH * tensorHeight) / ratio

                // --- 3. Clip boxes to image boundaries ---
                val finalCx = scaledCx.coerceIn(0f, frameWidth.toFloat())
                val finalCy = scaledCy.coerceIn(0f, frameHeight.toFloat())
                val finalW = scaledW.coerceIn(0f, frameWidth.toFloat())
                val finalH = scaledH.coerceIn(0f, frameHeight.toFloat())

                val classId = outputArray[offset + 5].toInt()
                val className = if (classId >= 0 && classId < labels.size) {
                    labels[classId]
                } else {
                    "Unknown"
                }

                results.add(
                    OrientedBoxResult(
                        cx = finalCx,
                        cy = finalCy,
                        w = finalW,
                        h = finalH,
                        angle = angleRad,
                        cnf = confidence,
                        cls = classId,
                        clsName = className
                    )
                )
            }
        }
        return results
    }

    /**
     * Pre-processes the frame:
     * 1. Calculates letterbox parameters.
     * 2. Resizes the frame to fit 640x640 while maintaining aspect ratio.
     * 3. Creates a 640x640 bitmap and pads with gray.
     * 4. Normalizes the image (0-255 -> 0-1).
     */
    private fun preProcess(frame: Bitmap): TensorImage {
        // --- 1. Calculate Letterbox parameters ---
        val frameHeight = frame.height
        val frameWidth = frame.width
        
        // Find the smaller ratio (gain)
        ratio = min(
            tensorHeight.toFloat() / frameHeight,
            tensorWidth.toFloat() / frameWidth
        )

        // Calculate new unpadded dimensions
        val newUnpadW = (frameWidth * ratio).roundToInt()
        val newUnpadH = (frameHeight * ratio).roundToInt()

        // Calculate padding
        padX = (tensorWidth.toFloat() - newUnpadW) / 2f
        padY = (tensorHeight.toFloat() - newUnpadH) / 2f

        // --- 2. Resize frame ---
        val resizedBitmap = Bitmap.createScaledBitmap(frame, newUnpadW, newUnpadH, false)

        // --- 3. Create letterboxed bitmap and pad with gray ---
        val letterboxedBitmap = Bitmap.createBitmap(tensorWidth, tensorHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxedBitmap)
        canvas.drawRect(0f, 0f, tensorWidth.toFloat(), tensorHeight.toFloat(), grayPaint)
        canvas.drawBitmap(resizedBitmap, padX, padY, null)

        // --- 4. Load into TensorImage and normalize ---
        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(letterboxedBitmap)
        
        return imageProcessor.process(tensorImage)
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onEmpty()
        fun onDetect(
            interfaceTime: Long,
            results: List<OrientedBoxResult>,
            preProcessTime: Long,
            postProcessTime: Long
        )
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.3F
    }
}