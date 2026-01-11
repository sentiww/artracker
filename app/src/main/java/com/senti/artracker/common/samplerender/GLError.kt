package com.senti.artracker.common.samplerender

import android.opengl.GLES30
import android.opengl.GLException
import android.opengl.GLU
import android.util.Log

/** Module for handling OpenGL errors. */
object GLError {
  /** Throws a [GLException] if a GL error occurred. */
  fun maybeThrowGLException(reason: String, api: String) {
    val errorCodes = getGlErrors() ?: return
    throw GLException(errorCodes[0], formatErrorMessage(reason, api, errorCodes))
  }

  /** Logs a message with the given logcat priority if a GL error occurred. */
  fun maybeLogGLError(priority: Int, tag: String, reason: String, api: String) {
    val errorCodes = getGlErrors() ?: return
    Log.println(priority, tag, formatErrorMessage(reason, api, errorCodes))
  }

  private fun formatErrorMessage(reason: String, api: String, errorCodes: List<Int>): String {
    val builder = StringBuilder(String.format("%s: %s: ", reason, api))
    val iterator = errorCodes.iterator()
    while (iterator.hasNext()) {
      val errorCode = iterator.next()
      builder.append(String.format("%s (%d)", GLU.gluErrorString(errorCode), errorCode))
      if (iterator.hasNext()) {
        builder.append(", ")
      }
    }
    return builder.toString()
  }

  private fun getGlErrors(): List<Int>? {
    var errorCode = GLES30.glGetError()
    if (errorCode == GLES30.GL_NO_ERROR) {
      return null
    }
    val errorCodes = mutableListOf(errorCode)
    while (true) {
      errorCode = GLES30.glGetError()
      if (errorCode == GLES30.GL_NO_ERROR) {
        break
      }
      errorCodes.add(errorCode)
    }
    return errorCodes
  }
}
