package com.senti.artracker.ml.classification.utils

import android.graphics.Rect

object VertexUtils {
  fun Pair<Int, Int>.rotateCoordinates(
    imageWidth: Int,
    imageHeight: Int,
    imageRotation: Int,
  ): Pair<Int, Int> {
    val (x, y) = this
    return when (imageRotation) {
      0 -> x to y
      180 -> imageWidth - x to imageHeight - y
      90 -> y to imageWidth - x
      270 -> imageHeight - y to x
      else -> error("Invalid imageRotation $imageRotation")
    }
  }

  fun Rect.rotateBoundingRect(
    imageWidth: Int,
    imageHeight: Int,
    imageRotation: Int,
  ): Rect {
    val rotatedCorners = listOf(
      (left to top).rotateCoordinates(imageWidth, imageHeight, imageRotation),
      (right to top).rotateCoordinates(imageWidth, imageHeight, imageRotation),
      (left to bottom).rotateCoordinates(imageWidth, imageHeight, imageRotation),
      (right to bottom).rotateCoordinates(imageWidth, imageHeight, imageRotation)
    )
    return rotatedCorners.toBoundingRectWithin(imageWidth, imageHeight) ?: Rect()
  }

  private fun List<Pair<Int, Int>>.toBoundingRectWithin(
    imageWidth: Int,
    imageHeight: Int,
  ): Rect? {
    if (isEmpty()) return null
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    for ((x, y) in this) {
      if (x < minX) minX = x
      if (y < minY) minY = y
      if (x > maxX) maxX = x
      if (y > maxY) maxY = y
    }
    if (minX == Int.MAX_VALUE || minY == Int.MAX_VALUE) return null
    val clampedMinX = minX.coerceIn(0, imageWidth)
    val clampedMinY = minY.coerceIn(0, imageHeight)
    val clampedMaxX = maxX.coerceIn(clampedMinX + 1, imageWidth)
    val clampedMaxY = maxY.coerceIn(clampedMinY + 1, imageHeight)
    return Rect(clampedMinX, clampedMinY, clampedMaxX, clampedMaxY)
  }
}
