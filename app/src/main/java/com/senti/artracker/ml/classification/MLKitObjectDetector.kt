package com.senti.artracker.ml.classification

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import com.senti.artracker.ml.classification.utils.ImageUtils
import com.senti.artracker.ml.classification.utils.VertexUtils.rotateCoordinates
import com.senti.artracker.ml.classification.utils.VertexUtils.rotateBoundingRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.tasks.asDeferred
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * Analyzes an image using ML Kit.
 */
class MLKitObjectDetector(context: Activity) : ObjectDetector(context) {
  companion object {
    private const val MODEL_FILE = "model_trained.tflite"
    private const val LABELS_FILE = "labels.txt"
  }

  private val interpreter: Interpreter = Interpreter(loadModelFile(context, MODEL_FILE))
  private val labels: List<String> = loadLabels(context, LABELS_FILE)
  private val inputShape = interpreter.getInputTensor(0).shape()
  private val inputDataType = interpreter.getInputTensor(0).dataType()
  private val modelInputHeight = inputShape[1]
  private val modelInputWidth = inputShape[2]
  private val modelInputChannels = inputShape[3]
  private val outputClasses = interpreter.getOutputTensor(0).shape().last()
  private val outputDataType = interpreter.getOutputTensor(0).dataType()
  private val supportsInputChannels = modelInputChannels == 1 || modelInputChannels == 3
  private val usesFloatInput = inputDataType == DataType.FLOAT32
  private val usesFloatOutput = outputDataType == DataType.FLOAT32
  private val canUseCustomClassifier =
    supportsInputChannels && usesFloatInput && usesFloatOutput && labels.size >= outputClasses

  private val builder = ObjectDetectorOptions.Builder()

  private val options = builder
    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
    .enableClassification()
    .enableMultipleObjects()
    .build()
  private val detector = ObjectDetection.getClient(options)

  override suspend fun analyze(image: Image, imageRotation: Int): List<DetectedObjectResult> {
    // `image` is in YUV (https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#acquireCameraImage()),
    val convertYuv = convertYuv(image)

    // The model performs best on upright images, so rotate it.
    val rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)

    val inputImage = InputImage.fromBitmap(rotatedImage, 0)

    val mlKitDetectedObjects = detector.process(inputImage).asDeferred().await()
    return mlKitDetectedObjects.mapNotNull { obj ->
      val boundingBox = obj.boundingBox
      val customClassification = classifyWithCustomModel(rotatedImage, boundingBox)
      val (confidence, labelText) = customClassification ?: run {
        val bestLabel = obj.labels.maxByOrNull { label -> label.confidence } ?: return@mapNotNull null
        bestLabel.confidence to bestLabel.text
      }
      val coords = boundingBox.exactCenterX().toInt() to boundingBox.exactCenterY().toInt()
      val rotatedCoordinates = coords.rotateCoordinates(rotatedImage.width, rotatedImage.height, imageRotation)
      val rotatedBoundingBox = boundingBox.rotateBoundingRect(rotatedImage.width, rotatedImage.height, imageRotation)
      DetectedObjectResult(
        confidence,
        labelText,
        rotatedCoordinates,
        rotatedBoundingBox,
        rotatedImage.width,
        rotatedImage.height
      )
    }
  }

  fun hasCustomModel() = canUseCustomClassifier

  private fun classifyWithCustomModel(bitmap: Bitmap, boundingBox: Rect): Pair<Float, String>? {
    if (!canUseCustomClassifier) return null
    val cropRect = clampRect(boundingBox, bitmap.width, bitmap.height) ?: return null
    val cropped = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
    val scaledBitmap = Bitmap.createScaledBitmap(cropped, modelInputWidth, modelInputHeight, true)

    val inputBuffer = convertBitmapToModelInput(scaledBitmap)
    val output = Array(1) { FloatArray(outputClasses) }
    interpreter.run(inputBuffer, output)
    val scores = output[0]
    val best = scores
      .withIndex()
      .maxByOrNull { it.value }
      ?: return null
    val label = labels.getOrNull(best.index) ?: return null
    return best.value to label
  }

  private fun clampRect(rect: Rect, width: Int, height: Int): Rect? {
    val left = rect.left.coerceAtLeast(0)
    val top = rect.top.coerceAtLeast(0)
    val right = rect.right.coerceAtMost(width)
    val bottom = rect.bottom.coerceAtMost(height)
    if (left >= right || top >= bottom) {
      return null
    }
    return Rect(left, top, right, bottom)
  }

  private fun convertBitmapToModelInput(bitmap: Bitmap): ByteBuffer {
    val width = bitmap.width
    val height = bitmap.height
    require(width == modelInputWidth && height == modelInputHeight) {
      "Bitmap size $width x $height does not match model input size $modelInputWidth x $modelInputHeight"
    }
    val buffer = ByteBuffer.allocateDirect(4 * width * height * modelInputChannels)
      .order(ByteOrder.nativeOrder())
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    var index = 0
    while (index < pixels.size) {
      val color = pixels[index]
      val r = (color shr 16 and 0xFF)
      val g = (color shr 8 and 0xFF)
      val b = (color and 0xFF)
      when (modelInputChannels) {
        1 -> {
          val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
          buffer.putFloat(gray)
        }
        3 -> {
          buffer.putFloat(r / 255f)
          buffer.putFloat(g / 255f)
          buffer.putFloat(b / 255f)
        }
        else -> throw IllegalStateException("Unsupported input channel count: $modelInputChannels")
      }
      index++
    }
    buffer.rewind()
    return buffer
  }

  private fun loadModelFile(context: Activity, modelPath: String): MappedByteBuffer =
    context.assets.openFd(modelPath).use { fileDescriptor ->
      FileInputStream(fileDescriptor.fileDescriptor).use { input ->
        val channel = input.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
      }
    }

  private fun loadLabels(context: Activity, labelsPath: String): List<String> =
    context.assets.open(labelsPath).bufferedReader().useLines { sequence ->
      sequence.map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }
}
