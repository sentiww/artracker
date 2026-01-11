package com.senti.artracker.common.samplerender

import android.opengl.GLES30
import android.util.Log
import java.nio.Buffer

internal class GpuBuffer(
  private val target: Int,
  private val numberOfBytesPerEntry: Int,
  entries: Buffer?
) {
  private val bufferId = IntArray(1)
  private var size: Int
  private var capacity: Int

  init {
    var initialEntries = entries
    if (initialEntries != null) {
      require(initialEntries.isDirect) {
        "If non-null, entries buffer must be a direct buffer"
      }
      if (initialEntries.limit() == 0) {
        initialEntries = null
      }
    }

    if (initialEntries == null) {
      size = 0
      capacity = 0
    } else {
      size = initialEntries.limit()
      capacity = initialEntries.limit()
    }

    try {
      GLES30.glBindVertexArray(0)
      GLError.maybeThrowGLException("Failed to unbind vertex array", "glBindVertexArray")

      GLES30.glGenBuffers(1, bufferId, 0)
      GLError.maybeThrowGLException("Failed to generate buffers", "glGenBuffers")

      GLES30.glBindBuffer(target, bufferId[0])
      GLError.maybeThrowGLException("Failed to bind buffer object", "glBindBuffer")

      initialEntries?.let {
        it.rewind()
        GLES30.glBufferData(
          target,
          it.limit() * numberOfBytesPerEntry,
          it,
          GLES30.GL_DYNAMIC_DRAW
        )
      }
      GLError.maybeThrowGLException("Failed to populate buffer object", "glBufferData")
    } catch (t: Throwable) {
      free()
      throw t
    }
  }

  fun set(entries: Buffer?) {
    if (entries == null || entries.limit() == 0) {
      size = 0
      return
    }
    require(entries.isDirect) { "If non-null, entries buffer must be a direct buffer" }

    GLES30.glBindBuffer(target, bufferId[0])
    GLError.maybeThrowGLException("Failed to bind vertex buffer object", "glBindBuffer")

    entries.rewind()

    if (entries.limit() <= capacity) {
      GLES30.glBufferSubData(target, 0, entries.limit() * numberOfBytesPerEntry, entries)
      GLError.maybeThrowGLException(
        "Failed to populate vertex buffer object",
        "glBufferSubData"
      )
      size = entries.limit()
    } else {
      GLES30.glBufferData(
        target,
        entries.limit() * numberOfBytesPerEntry,
        entries,
        GLES30.GL_DYNAMIC_DRAW
      )
      GLError.maybeThrowGLException("Failed to populate vertex buffer object", "glBufferData")
      size = entries.limit()
      capacity = entries.limit()
    }
  }

  fun free() {
    if (bufferId[0] != 0) {
      GLES30.glDeleteBuffers(1, bufferId, 0)
      GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free buffer object", "glDeleteBuffers")
      bufferId[0] = 0
    }
  }

  fun getBufferId(): Int = bufferId[0]

  fun getSize(): Int = size

  companion object {
    private val TAG = GpuBuffer::class.java.simpleName

    // These values refer to the byte count of the corresponding Java datatypes.
    const val INT_SIZE = 4
    const val FLOAT_SIZE = 4
  }
}
