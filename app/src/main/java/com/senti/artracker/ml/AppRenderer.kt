package com.senti.artracker.ml

import android.opengl.Matrix
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.senti.artracker.common.helpers.DisplayRotationHelper
import com.senti.artracker.common.samplerender.SampleRender
import com.senti.artracker.common.samplerender.arcore.BackgroundRenderer
import com.senti.artracker.ml.classification.DetectedObjectResult
import com.senti.artracker.ml.classification.MLKitObjectDetector
import com.senti.artracker.ml.render.BoundingBoxRender3D
import com.senti.artracker.ml.render.LabelRender
import com.senti.artracker.ml.render.PointCloudRender
import com.senti.artracker.ml.tracking.ObjectTracker
import java.util.Collections
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Renders tracked detections and AR scene objects.
 */
class AppRenderer(private val activity: MainActivity) : DefaultLifecycleObserver, SampleRender.Renderer,
  CoroutineScope by MainScope() {

  companion object {
    private const val MIN_BOX_SIZE = 0.05f
    private const val LOG_TAG = "AppRenderer"
  }

  lateinit var view: MainActivityView
    private set

  private val displayRotationHelper = DisplayRotationHelper(activity)
  private lateinit var backgroundRenderer: BackgroundRenderer
  private val pointCloudRender = PointCloudRender()
  private val labelRenderer = LabelRender()
  private val boundingBoxRender = BoundingBoxRender3D()

  private val viewMatrix = FloatArray(16)
  private val projectionMatrix = FloatArray(16)
  private val viewProjectionMatrix = FloatArray(16)

  private val trackedAnchors = Collections.synchronizedMap(mutableMapOf<Int, ARTrackedAnchor>())
  private val mlKitAnalyzer = MLKitObjectDetector(activity)
  private val detectionManager: DetectionManager

  @Volatile private var currentSettings: DetectionSettings
  @Volatile private var isProcessingImage = false
  @Volatile private var hasPendingResult = false
  @Volatile private var showBoundingBoxes = true
  @Volatile private var showConfidence = true
  @Volatile private var showPointCloud = true
  private var currentAnalyzer = mlKitAnalyzer
  private var currentFovX = Math.PI.toFloat() / 2f
  private var currentFovY = Math.PI.toFloat() / 2f
  private var objectResults: List<DetectedObjectResult>? = null

  init {
    currentSettings = DetectionSettings.defaults()
    detectionManager = DetectionManager(currentSettings)
  }

  override fun onResume(owner: LifecycleOwner) {
    displayRotationHelper.onResume()
  }

  override fun onPause(owner: LifecycleOwner) {
    displayRotationHelper.onPause()
  }

  fun bindView(mainView: MainActivityView) {
    view = mainView
  }

  override fun onSurfaceCreated(render: SampleRender) {
    backgroundRenderer = BackgroundRenderer(render).apply { setUseDepthVisualization(render, false) }
    pointCloudRender.onSurfaceCreated(render)
    labelRenderer.onSurfaceCreated(render)
    boundingBoxRender.onSurfaceCreated(render)
  }

  override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
  }

  override fun onDrawFrame(render: SampleRender) {
    val session = activity.arCoreSessionHelper.sessionCache ?: return
    session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))

    displayRotationHelper.updateSessionIfNeeded(session)

    val frame = try {
      session.update()
    } catch (e: CameraNotAvailableException) {
      log("Camera not available during onDrawFrame", e)
      log("Camera not available. Try restarting the app.")
      return
    }

    backgroundRenderer.updateDisplayGeometry(frame)
    backgroundRenderer.drawBackground(render)

    val camera = frame.camera
    camera.getViewMatrix(viewMatrix, 0)
    camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100.0f)
    updateFov(projectionMatrix)
    Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

    if (camera.trackingState != TrackingState.TRACKING) return

    if (showPointCloud) {
      frame.acquirePointCloud().use { pointCloud ->
        pointCloudRender.drawPointCloud(render, pointCloud, viewProjectionMatrix)
      }
    }

    if (!isProcessingImage && !hasPendingResult) {
      val cameraImage = frame.tryAcquireCameraImage()
      if (cameraImage != null) {
        isProcessingImage = true
        launch(Dispatchers.IO) {
          try {
            val cameraId = session.cameraConfig.cameraId
            val rotation = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)
            objectResults = currentAnalyzer.analyze(cameraImage, rotation)
            hasPendingResult = true
          } catch (e: Exception) {
            log("Failed to analyze camera image", e)
            view.post { log("Failed to analyze camera image: ${e.message ?: "Unknown error"}") }
          } finally {
            cameraImage.close()
            isProcessingImage = false
          }
        }
      }
    }

    val rawDetections = objectResults
    if (rawDetections != null) {
      objectResults = null
      hasPendingResult = false

      val trackedObjects = detectionManager.processDetections(rawDetections)
      log("$currentAnalyzer tracked objects: $trackedObjects")
      val hasAnchors = updateTrackedAnchors(trackedObjects, frame)
      view.post {
        when {
          trackedObjects.isEmpty() && rawDetections.isEmpty() && !mlKitAnalyzer.hasCustomModel() ->
            log("Default ML Kit classification model returned no results. Supply a tuned custom model.")
          trackedObjects.isEmpty() && rawDetections.isEmpty() ->
            log("Classification model returned no results.")
          !hasAnchors ->
            log("Objects were classified, but could not be attached to an anchor. Move the device for better understanding.")
        }
      }
    }

    val anchorsSnapshot = synchronized(trackedAnchors) { trackedAnchors.values.toList() }
    for (tracked in anchorsSnapshot) {
      val anchor = tracked.anchor
      if (anchor.trackingState != TrackingState.TRACKING) continue
      if (showBoundingBoxes) {
        val (widthMeters, heightMeters) = calculateBoundingBoxScale(tracked, camera.pose)
        if (widthMeters > 0f && heightMeters > 0f) {
          val rightAxis = anchor.pose.getTransformedAxis(0, 1.0f)
          val upAxis = anchor.pose.getTransformedAxis(1, 1.0f)
          boundingBoxRender.draw(render, viewProjectionMatrix, anchor.pose, widthMeters, heightMeters, rightAxis, upAxis)
        }
      }
      val labelText = if (showConfidence) {
        val percent = (tracked.trackedObject.confidence * 100).toInt()
        "${tracked.trackedObject.label} $percent%"
      } else {
        tracked.trackedObject.label
      }
      labelRenderer.draw(render, viewProjectionMatrix, anchor.pose, camera.pose, labelText)
    }
  }

  private fun Frame.tryAcquireCameraImage() = try {
    acquireCameraImage()
  } catch (e: NotYetAvailableException) {
    null
  } catch (e: Throwable) {
    throw e
  }

  private fun calculateBoundingBoxScale(trackedAnchor: ARTrackedAnchor, cameraPose: Pose): Pair<Float, Float> {
    val trackedObject = trackedAnchor.trackedObject
    val imageWidth = trackedObject.imageWidth.coerceAtLeast(1)
    val imageHeight = trackedObject.imageHeight.coerceAtLeast(1)
    val widthRatio = trackedObject.boundingBox.width().toFloat() / imageWidth
    val heightRatio = trackedObject.boundingBox.height().toFloat() / imageHeight
    if (widthRatio <= 0f || heightRatio <= 0f) return 0f to 0f

    val anchorPos = trackedAnchor.anchor.pose.translation
    val cameraPos = cameraPose.translation
    val dx = anchorPos[0] - cameraPos[0]
    val dy = anchorPos[1] - cameraPos[1]
    val dz = anchorPos[2] - cameraPos[2]
    val distance = sqrt(dx * dx + dy * dy + dz * dz)
    if (!distance.isFinite() || distance <= 0f) return MIN_BOX_SIZE to MIN_BOX_SIZE

    val widthAngle = widthRatio * currentFovX
    val heightAngle = heightRatio * currentFovY
    val widthMeters = (2f * distance * tan(widthAngle / 2f)).takeIf { it.isFinite() && it > 0f } ?: MIN_BOX_SIZE
    val heightMeters = (2f * distance * tan(heightAngle / 2f)).takeIf { it.isFinite() && it > 0f } ?: MIN_BOX_SIZE
    return max(widthMeters, MIN_BOX_SIZE) to max(heightMeters, MIN_BOX_SIZE)
  }

  fun applySettings(settings: DetectionSettings) {
    currentSettings = settings
    detectionManager.updateSettings(currentSettings)
    showBoundingBoxes = currentSettings.showBoundingBoxes
    showConfidence = currentSettings.showConfidence
    showPointCloud = currentSettings.showPointCloud
    currentAnalyzer = mlKitAnalyzer
  }

  fun currentSettings(): DetectionSettings = detectionManager.currentSettings()

  private fun updateTrackedAnchors(trackedObjects: List<ObjectTracker.TrackedObject>, frame: Frame): Boolean {
    synchronized(trackedAnchors) {
      val trackedById = trackedObjects.associateBy { it.id }
      val iterator = trackedAnchors.entries.iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val updatedObject = trackedById[entry.key]
        if (updatedObject == null) {
          entry.value.anchor.detach()
          iterator.remove()
        } else {
          entry.value.trackedObject = updatedObject
        }
      }
      for (trackedObject in trackedObjects) {
        if (trackedAnchors.containsKey(trackedObject.id)) continue
        val (atX, atY) = trackedObject.centerCoordinate
        val anchor = createAnchor(atX.toFloat(), atY.toFloat(), frame) ?: continue
        trackedAnchors[trackedObject.id] = ARTrackedAnchor(trackedObject.id, anchor, trackedObject)
      }
      return trackedAnchors.isNotEmpty()
    }
  }

  private fun log(message: String, throwable: Throwable? = null) {
    if (throwable == null) {
      Log.i(LOG_TAG, message)
    } else {
      Log.e(LOG_TAG, message, throwable)
    }
  }

  private fun updateFov(projectionMatrix: FloatArray) {
    val absFx = abs(projectionMatrix[0])
    val absFy = abs(projectionMatrix[5])
    if (absFx > 0f && absFx.isFinite()) {
      currentFovX = 2f * atan(1f / absFx)
    }
    if (absFy > 0f && absFy.isFinite()) {
      currentFovY = 2f * atan(1f / absFy)
    }
  }

  private val convertFloats = FloatArray(2)
  private val convertFloatsOut = FloatArray(2)

  fun createAnchor(xImage: Float, yImage: Float, frame: Frame): Anchor? {
    convertFloats[0] = xImage
    convertFloats[1] = yImage
    frame.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS, convertFloats, Coordinates2d.VIEW, convertFloatsOut)

    val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
    val result = hits.getOrNull(0) ?: return null
    return result.trackable.createAnchor(result.hitPose)
  }
}

data class ARTrackedAnchor(val id: Int, val anchor: Anchor, var trackedObject: ObjectTracker.TrackedObject)
