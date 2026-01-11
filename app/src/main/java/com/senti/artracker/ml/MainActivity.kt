package com.senti.artracker.ml

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.text.InputType
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.senti.artracker.common.helpers.FullScreenHelper
import com.senti.artracker.R
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException


class MainActivity : AppCompatActivity() {
  val TAG = "MainActivity"
  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper

  lateinit var renderer: AppRenderer
  lateinit var view: MainActivityView
  private lateinit var detectionSettings: DetectionSettings

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
    // When session creation or session.resume fails, we display a message and log detailed information.
    arCoreSessionHelper.exceptionCallback = { exception ->
      val message = when (exception) {
        is UnavailableArcoreNotInstalledException,
        is UnavailableUserDeclinedInstallationException -> "Please install ARCore"
        is UnavailableApkTooOldException -> "Please update ARCore"
        is UnavailableSdkTooOldException -> "Please update this app"
        is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
        is CameraNotAvailableException -> "Camera not available. Try restarting the app."
        else -> "Failed to create AR session: $exception"
      }
      Log.e(TAG, message, exception)
    }

    arCoreSessionHelper.beforeSessionResume = { session ->
      session.configure(
        session.config.apply {
          // To get the best image of the object in question, enable autofocus.
          focusMode = Config.FocusMode.AUTO
          if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            depthMode = Config.DepthMode.AUTOMATIC
          }
        }
      )

      val filter = CameraConfigFilter(session)
        .setFacingDirection(CameraConfig.FacingDirection.BACK)
      val configs = session.getSupportedCameraConfigs(filter)
      val sort = compareByDescending<CameraConfig> { it.imageSize.width }
        .thenByDescending { it.imageSize.height }
      session.cameraConfig = configs.sortedWith(sort)[0]
    }
    lifecycle.addObserver(arCoreSessionHelper)

    renderer = AppRenderer(this)
    lifecycle.addObserver(renderer)
    view = MainActivityView(this, renderer)
    setContentView(view.root)
    renderer.bindView(view)
    lifecycle.addObserver(view)
    view.settingsButton.setOnClickListener { showSettingsDialog() }
    detectionSettings = renderer.currentSettings()
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    arCoreSessionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.main_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_confidence -> {
        showSettingsDialog()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun showSettingsDialog() {
    val padding = (16 * resources.displayMetrics.density).toInt()
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(padding, padding / 2, padding, padding / 2)
    }

    fun createLabeledInput(labelText: String, input: EditText): LinearLayout {
      return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val label = TextView(this@MainActivity).apply {
          text = labelText
        }
        addView(label)
        addView(input)
      }
    }

    val confidenceInput = EditText(this).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
      setText(detectionSettings.detectionConfidence.toString())
      setSelection(text?.length ?: 0)
    }
    val ttlInput = EditText(this).apply {
      inputType = InputType.TYPE_CLASS_NUMBER
      setText(detectionSettings.trackerTtlMillis.toString())
      setSelection(text?.length ?: 0)
    }

    val showBoxesSwitch = SwitchCompat(this).apply {
      text = getString(R.string.show_bounding_boxes)
      isChecked = detectionSettings.showBoundingBoxes
    }

    val showConfidenceSwitch = SwitchCompat(this).apply {
      text = getString(R.string.show_confidence)
      isChecked = detectionSettings.showConfidence
    }

    val showPointCloudSwitch = SwitchCompat(this).apply {
      text = getString(R.string.show_point_cloud)
      isChecked = detectionSettings.showPointCloud
    }

    container.addView(createLabeledInput(getString(R.string.detection_confidence_label), confidenceInput))
    container.addView(createLabeledInput(getString(R.string.tracker_ttl_label), ttlInput))
    container.addView(showBoxesSwitch)
    container.addView(showConfidenceSwitch)
    container.addView(showPointCloudSwitch)

    AlertDialog.Builder(this)
      .setTitle(R.string.settings)
      .setMessage(R.string.settings_description)
      .setView(container)
      .setPositiveButton(R.string.apply) { _, _ ->
        val confidence = confidenceInput.text.toString().toFloatOrNull()
        val ttl = ttlInput.text.toString().toLongOrNull()
        if (confidence == null || confidence !in 0f..1f) {
          Log.w(TAG, "Ignoring confidence update: invalid value $confidence")
          return@setPositiveButton
        }
        if (ttl == null || ttl <= 0) {
          Log.w(TAG, "Ignoring TTL update: invalid value $ttl")
          return@setPositiveButton
        }
        detectionSettings = DetectionSettings(
          showBoundingBoxes = showBoxesSwitch.isChecked,
          showConfidence = showConfidenceSwitch.isChecked,
          showPointCloud = showPointCloudSwitch.isChecked,
          detectionConfidence = confidence,
          trackerTtlMillis = ttl
        )
        renderer.applySettings(detectionSettings)
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }
}
