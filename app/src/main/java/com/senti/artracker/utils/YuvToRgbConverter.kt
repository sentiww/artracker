package com.senti.artracker.utils

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image

/**
 * Helper class used to efficiently convert a [Image] in [ImageFormat.YUV_420_888] format to an RGB
 * [Bitmap] without relying on the deprecated RenderScript APIs.
 */
class YuvToRgbConverter {
  private val yuvBytes = arrayOfNulls<ByteArray>(3)
  private var argbBuffer: IntArray? = null

  @Synchronized
  fun yuvToRgb(image: Image, output: Bitmap) {
    require(image.format == ImageFormat.YUV_420_888) { "Unsupported image format ${image.format}" }
    require(output.width == image.width && output.height == image.height) {
      "Output bitmap size must match image dimensions"
    }

    fillBytes(image.planes, yuvBytes)

    val width = image.width
    val height = image.height
    if (argbBuffer == null || argbBuffer!!.size < width * height) {
      argbBuffer = IntArray(width * height)
    }

    convertYuv420ToArgb8888(
      yuvBytes[0]!!,
      yuvBytes[1]!!,
      yuvBytes[2]!!,
      width,
      height,
      image.planes[0].rowStride,
      image.planes[1].rowStride,
      image.planes[1].pixelStride,
      argbBuffer!!
    )
    output.setPixels(argbBuffer!!, 0, width, 0, 0, width, height)
  }

  private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
    planes.forEachIndexed { index, plane ->
      val buffer = plane.buffer
      if (yuvBytes[index]?.size != buffer.capacity()) {
        yuvBytes[index] = ByteArray(buffer.capacity())
      }
      buffer.get(yuvBytes[index]!!)
      buffer.rewind()
    }
  }

  private fun convertYuv420ToArgb8888(
    yData: ByteArray,
    uData: ByteArray,
    vData: ByteArray,
    width: Int,
    height: Int,
    yRowStride: Int,
    uvRowStride: Int,
    uvPixelStride: Int,
    out: IntArray
  ) {
    var outputIndex = 0
    for (j in 0 until height) {
      val pY = yRowStride * j
      val pUV = uvRowStride * (j shr 1)
      for (i in 0 until width) {
        val uvOffset = pUV + (i shr 1) * uvPixelStride
        val y = 0xff and yData[pY + i].toInt()
        val u = 0xff and uData[uvOffset].toInt()
        val v = 0xff and vData[uvOffset].toInt()
        out[outputIndex++] = yuvToRgb(y, u, v)
      }
    }
  }

  private fun yuvToRgb(y: Int, u: Int, v: Int): Int {
    var yValue = y - 16
    var uValue = u - 128
    var vValue = v - 128
    if (yValue < 0) yValue = 0

    val y1192 = 1192 * yValue
    var r = y1192 + 1634 * vValue
    var g = y1192 - 833 * vValue - 400 * uValue
    var b = y1192 + 2066 * uValue

    r = r.coerceIn(0, 262143)
    g = g.coerceIn(0, 262143)
    b = b.coerceIn(0, 262143)

    return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
  }
}
