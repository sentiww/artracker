package com.senti.artracker.common.helpers

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Helper to set up the Android full screen mode. */
object FullScreenHelper {
  /**
   * Sets the Android fullscreen flags. Expected to be called from
   * [Activity.onWindowFocusChanged].
   */
  fun setFullScreenOnWindowFocusChanged(activity: Activity, hasFocus: Boolean) {
    if (!hasFocus) return

    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
  }
}
