package com.senti.artracker.ml.tracking

import android.graphics.Rect
import com.senti.artracker.ml.classification.DetectedObjectResult
import kotlin.math.max
import kotlin.math.min

/**
 * Maintains lightweight trackers based on incoming [DetectedObjectResult]s by associating results
 * using Intersection over Union (IoU). Tracked objects are kept alive for a few frames even if no
 * new detection is available to reduce flicker.
 */
class ObjectTracker(
  private val iouThreshold: Float = 0.3f,
  ttlMillis: Long = 1500L
) {

  data class TrackedObject(
    val id: Int,
    val label: String,
    val confidence: Float,
    val centerCoordinate: Pair<Int, Int>,
    val boundingBox: Rect,
    val imageWidth: Int,
    val imageHeight: Int
  )

  private data class Track(
    val id: Int,
    val label: String,
    var confidence: Float,
    val boundingBox: Rect,
    var center: Pair<Int, Int>,
    var imageWidth: Int,
    var imageHeight: Int,
    var lastUpdateMillis: Long
  )

  private val tracks = mutableListOf<Track>()
  private var nextId = 1
  private var ttlMillis: Long = ttlMillis

  fun clear() {
    tracks.clear()
    nextId = 1
  }

  fun updateTtlMillis(newTtlMillis: Long) {
    ttlMillis = max(1L, newTtlMillis)
  }

  fun update(detections: List<DetectedObjectResult>, timestampMillis: Long): List<TrackedObject> {
    val validDetections = detections
      .filter { it.boundingBox.width() > 0 && it.boundingBox.height() > 0 }
    val unmatchedDetections = validDetections.indices.toMutableSet()

    for (track in tracks) {
      var bestDetectionIdx = -1
      var bestIou = 0f
      for (idx in unmatchedDetections) {
        val detection = validDetections[idx]
        if (detection.label != track.label) continue
        val iou = intersectionOverUnion(track.boundingBox, detection.boundingBox)
        if (iou > bestIou) {
          bestIou = iou
          bestDetectionIdx = idx
        }
      }
      if (bestDetectionIdx != -1 && bestIou >= iouThreshold) {
        val detection = validDetections[bestDetectionIdx]
        track.boundingBox.set(detection.boundingBox)
        track.center = detection.centerCoordinate
        track.confidence = detection.confidence
        track.lastUpdateMillis = timestampMillis
        track.imageWidth = detection.imageWidth
        track.imageHeight = detection.imageHeight
        unmatchedDetections.remove(bestDetectionIdx)
      }
    }

    for (idx in unmatchedDetections) {
      val detection = validDetections[idx]
      tracks += Track(
        id = nextId++,
        label = detection.label,
        confidence = detection.confidence,
        boundingBox = Rect(detection.boundingBox),
        center = detection.centerCoordinate,
        imageWidth = detection.imageWidth,
        imageHeight = detection.imageHeight,
        lastUpdateMillis = timestampMillis
      )
    }

    tracks.removeAll { timestampMillis - it.lastUpdateMillis > ttlMillis }

    return tracks.map { track ->
      TrackedObject(
        id = track.id,
        label = track.label,
        confidence = track.confidence,
        centerCoordinate = track.center,
        boundingBox = Rect(track.boundingBox),
        imageWidth = track.imageWidth,
        imageHeight = track.imageHeight
      )
    }
  }

  private fun intersectionOverUnion(a: Rect, b: Rect): Float {
    val intersectionLeft = max(a.left, b.left)
    val intersectionTop = max(a.top, b.top)
    val intersectionRight = min(a.right, b.right)
    val intersectionBottom = min(a.bottom, b.bottom)

    val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0)
    val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0)
    val intersectionArea = intersectionWidth * intersectionHeight

    if (intersectionArea <= 0) return 0f

    val areaA = a.width() * a.height()
    val areaB = b.width() * b.height()
    val unionArea = areaA + areaB - intersectionArea
    return if (unionArea <= 0) 0f else intersectionArea.toFloat() / unionArea.toFloat()
  }
}
