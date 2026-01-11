package com.senti.artracker.ml

import android.opengl.GLSurfaceView
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.senti.artracker.common.samplerender.SampleRender
import androidx.appcompat.widget.AppCompatButton
import com.senti.artracker.R

/**
 * Wraps [R.layout.activity_main] and controls lifecycle operations for [GLSurfaceView].
 */
class MainActivityView(val activity: MainActivity, renderer: AppRenderer) : DefaultLifecycleObserver {
  val root = View.inflate(activity, R.layout.activity_main, null)
  val surfaceView = root.findViewById<GLSurfaceView>(R.id.surfaceview).apply {
    SampleRender(this, renderer, activity.assets)
  }
  val settingsButton = root.findViewById<AppCompatButton>(R.id.settingsButton)

  override fun onResume(owner: LifecycleOwner) {
    surfaceView.onResume()
  }

  override fun onPause(owner: LifecycleOwner) {
    surfaceView.onPause()
  }

  fun post(action: Runnable) = root.post(action)

}
