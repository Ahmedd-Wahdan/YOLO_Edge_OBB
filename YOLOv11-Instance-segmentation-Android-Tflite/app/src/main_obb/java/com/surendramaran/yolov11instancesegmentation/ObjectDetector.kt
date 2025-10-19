// In file: ObjectDetector.kt
package com.surendramaran.yolov11instancesegmentation

import android.content.Context
import android.graphics.Bitmap
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
            tensorWidth = it[1]
            tensorHeight = it[2]
        }

        outputShape?.let {
            // Because the model has max_det=50, this will be 50
            numDetections = it[1]
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

        // Output shape is [1, 50, 7] for an OBB model with nms=True and max_det=50
        // 7 values are: cx, cy, w, h, angle, class_index, confidence_score
        // TODO: move this intialization to be a class member to avoid repeated allocations
        val outputBuffer = TensorBuffer.createFixedSize(
            intArrayOf(1, numDetections, 7),
            OUTPUT_IMAGE_TYPE
        )

        var interfaceTime = SystemClock.uptimeMillis()
        interpreter.run(imageBuffer.buffer, outputBuffer.buffer.rewind())
        interfaceTime = SystemClock.uptimeMillis() - interfaceTime

        var postProcessTime = SystemClock.uptimeMillis()
        val results = postProcess(outputBuffer.floatArray)
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

    private fun postProcess(outputArray: FloatArray): List<OrientedBoxResult>  {  // [1, max_num_of_detections, 7] -> [50, 7]
        val results = mutableListOf<OrientedBoxResult>()

        for (i in 0 until numDetections) {
            val offset = i * 7
            val confidence = outputArray[offset + 6]

            // Stop when we see a detection with confidence 0, as the rest are padding
            if (confidence == 0f) break

            if (confidence >= CONFIDENCE_THRESHOLD) {
                val classId = outputArray[offset + 5].toInt()
                val className = if (classId >= 0 && classId < labels.size) {
                    labels[classId]
                } else {
                    "Unknown"
                }

                results.add(
                    OrientedBoxResult(
                        cx = outputArray[offset],
                        cy = outputArray[offset + 1],
                        w = outputArray[offset + 2],
                        h = outputArray[offset + 3],
                        angle = outputArray[offset + 4],
                        cnf = confidence,
                        cls = classId,
                        clsName = className
                    )
                )
            }
        }
        return results
    }

    private fun preProcess(frame: Bitmap): TensorImage {
        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)
        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(resizedBitmap)
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