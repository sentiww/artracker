package com.senti.artracker.ml.classification

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import com.senti.artracker.utils.YuvToRgbConverter
import com.google.ar.core.Frame

/**
 * Describes a common interface for ML-based detectors that can infer object labels in a given [Image]
 * and returns a list of [DetectedObjectResult].
 */
abstract class ObjectDetector(val context: Context) {
  val yuvConverter = YuvToRgbConverter()

  /**
   * Infers a list of [DetectedObjectResult] given a camera image frame, which contains a confidence level,
   * a label, and a pixel coordinate on the image which is believed to be the center of the object.
   */
  abstract suspend fun analyze(image: Image, imageRotation: Int): List<DetectedObjectResult>

  /**
   * [Frame.acquireCameraImage] returns an image in YUV format.
   * https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#acquireCameraImage()
   *
   * Converts a YUV image to a [Bitmap] using [YuvToRgbConverter].
   */
  fun convertYuv(image: Image): Bitmap {
    return Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply {
      yuvConverter.yuvToRgb(image, this)
    }
  }
}
