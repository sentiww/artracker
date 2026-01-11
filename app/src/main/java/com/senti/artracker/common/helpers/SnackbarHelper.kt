package com.senti.artracker.common.helpers

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

/**
 * Helper to manage the sample snackbar. Hides the Android boilerplate code, and exposes simpler
 * methods.
 */
class SnackbarHelper {
  private var messageSnackbar: Snackbar? = null
  private var maxLines = 2
  private var lastMessage = ""
  private var snackbarView: View? = null

  private enum class DismissBehavior {
    HIDE,
    SHOW,
    FINISH
  }

  val isShowing: Boolean
    get() = messageSnackbar != null

  /** Shows a snackbar with a given message. */
  fun showMessage(activity: Activity, message: String) {
    if (message.isNotEmpty() && (!isShowing || lastMessage != message)) {
      lastMessage = message
      show(activity, message, DismissBehavior.HIDE)
    }
  }

  /** Shows a snackbar with a given message, and a dismiss button. */
  fun showMessageWithDismiss(activity: Activity, message: String) {
    show(activity, message, DismissBehavior.SHOW)
  }

  /** Shows a snackbar with a given error message. */
  fun showError(activity: Activity, errorMessage: String) {
    show(activity, errorMessage, DismissBehavior.FINISH)
  }

  /** Hides the currently showing snackbar, if there is one. */
  fun hide(activity: Activity) {
    val snackbarToHide = messageSnackbar ?: return
    lastMessage = ""
    messageSnackbar = null
    activity.runOnUiThread { snackbarToHide.dismiss() }
  }

  fun setMaxLines(lines: Int) {
    maxLines = lines
  }

  /** Sets the view used to find a suitable Snackbar parent. */
  fun setParentView(snackbarView: View?) {
    this.snackbarView = snackbarView
  }

  private fun show(activity: Activity, message: String, dismissBehavior: DismissBehavior) {
    activity.runOnUiThread {
      val parent = snackbarView ?: activity.findViewById(android.R.id.content)
      messageSnackbar =
        Snackbar.make(parent, message, Snackbar.LENGTH_INDEFINITE).apply {
          view.setBackgroundColor(BACKGROUND_COLOR)
          if (dismissBehavior != DismissBehavior.HIDE) {
            setAction("Dismiss") { this@apply.dismiss() }
            if (dismissBehavior == DismissBehavior.FINISH) {
              addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                  override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                  }
                }
              )
            }
          }
          val textView =
            view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
          textView.maxLines = maxLines
        }
      messageSnackbar?.show()
    }
  }

  companion object {
    private const val BACKGROUND_COLOR = 0xbf323232.toInt()
  }
}
