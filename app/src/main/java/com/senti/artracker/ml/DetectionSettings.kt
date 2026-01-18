package com.senti.artracker.ml

data class DetectionSettings(
  val showBoundingBoxes: Boolean,
  val showConfidence: Boolean,
  val showPointCloud: Boolean,
  val detectionConfidence: Float,
  val trackerTtlMillis: Long,
  val ttsEnabled: Boolean,
) {
  init {
    require(detectionConfidence in 0f..1f) { "Confidence must be between 0 and 1" }
    require(trackerTtlMillis > 0) { "Tracker TTL must be positive" }
  }

  companion object {
    const val DEFAULT_CONFIDENCE = 0.95f
    const val DEFAULT_TRACKER_TTL_MS = 1_500L

    fun defaults() = DetectionSettings(
      showBoundingBoxes = true,
      showConfidence = true,
      showPointCloud = true,
      detectionConfidence = DEFAULT_CONFIDENCE,
      trackerTtlMillis = DEFAULT_TRACKER_TTL_MS,
      ttsEnabled = true
    )
  }
}
