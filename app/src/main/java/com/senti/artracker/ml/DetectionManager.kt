package com.senti.artracker.ml

import android.os.SystemClock
import com.senti.artracker.ml.classification.DetectedObjectResult
import com.senti.artracker.ml.tracking.ObjectTracker

class DetectionManager(initialSettings: DetectionSettings) {
  private val tracker = ObjectTracker(ttlMillis = initialSettings.trackerTtlMillis)
  private var settings = initialSettings

  fun updateSettings(newSettings: DetectionSettings) {
    settings = newSettings
    tracker.updateTtlMillis(newSettings.trackerTtlMillis)
  }

  fun currentSettings(): DetectionSettings = settings

  fun processDetections(rawDetections: List<DetectedObjectResult>): List<ObjectTracker.TrackedObject> {
    val filtered = rawDetections.filter { it.confidence >= settings.detectionConfidence }
    return tracker.update(filtered, SystemClock.elapsedRealtime())
  }

  fun reset() {
    tracker.clear()
  }
}
