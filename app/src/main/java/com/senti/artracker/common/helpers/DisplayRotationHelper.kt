package com.senti.artracker.common.helpers

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.google.ar.core.Session

/**
 * Helper to track the display rotations. In particular, the 180 degree rotations are not notified
 * by the onSurfaceChanged() callback, and thus they require listening to the Android display
 * events.
 */
class DisplayRotationHelper(context: Context) : DisplayListener {
  private var viewportChanged = false
  private var viewportWidth = 0
  private var viewportHeight = 0
  private val display: android.view.Display
  private val displayManager: DisplayManager
  private val cameraManager: CameraManager

  init {
    displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      context.display ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
      ?: throw IllegalStateException("Unable to obtain default display")
    } else {
      @Suppress("DEPRECATION")
      (windowManager.defaultDisplay)
    }
  }

  /** Registers the display listener. Should be called from [Activity.onResume]. */
  fun onResume() {
    displayManager.registerDisplayListener(this, null)
  }

  /** Unregisters the display listener. Should be called from [Activity.onPause]. */
  fun onPause() {
    displayManager.unregisterDisplayListener(this)
  }

  /** Records a change in surface dimensions. */
  fun onSurfaceChanged(width: Int, height: Int) {
    viewportWidth = width
    viewportHeight = height
    viewportChanged = true
  }

  /** Updates the session display geometry if a change was recorded. */
  fun updateSessionIfNeeded(session: Session) {
    if (viewportChanged) {
      val displayRotation = display.rotation
      session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
      viewportChanged = false
    }
  }

  /**
   * Returns the aspect ratio of the GL surface viewport while accounting for the display rotation
   * relative to the device camera sensor orientation.
   */
  fun getCameraSensorRelativeViewportAspectRatio(cameraId: String): Float {
    return when (val cameraSensorToDisplayRotation = getCameraSensorToDisplayRotation(cameraId)) {
      90, 270 -> viewportHeight.toFloat() / viewportWidth.toFloat()
      0, 180 -> viewportWidth.toFloat() / viewportHeight.toFloat()
      else -> throw RuntimeException("Unhandled rotation: $cameraSensorToDisplayRotation")
    }
  }

  /** Returns the rotation of the back-facing camera with respect to the display. */
  fun getCameraSensorToDisplayRotation(cameraId: String): Int {
    val characteristics = try {
      cameraManager.getCameraCharacteristics(cameraId)
    } catch (e: CameraAccessException) {
      throw RuntimeException("Unable to determine display orientation", e)
    }

    val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    val displayOrientation = toDegrees(display.rotation)
    return (sensorOrientation - displayOrientation + 360) % 360
  }

  private fun toDegrees(rotation: Int): Int {
    return when (rotation) {
      Surface.ROTATION_0 -> 0
      Surface.ROTATION_90 -> 90
      Surface.ROTATION_180 -> 180
      Surface.ROTATION_270 -> 270
      else -> throw RuntimeException("Unknown rotation $rotation")
    }
  }

  override fun onDisplayAdded(displayId: Int) = Unit

  override fun onDisplayRemoved(displayId: Int) = Unit

  override fun onDisplayChanged(displayId: Int) {
    viewportChanged = true
  }
}
