package com.senti.artracker.common.samplerender

import android.opengl.GLES30
import java.io.Closeable
import java.nio.IntBuffer

/** A list of vertex indices stored GPU-side. */
class IndexBuffer(
  @Suppress("UNUSED_PARAMETER") render: SampleRender,
  entries: IntBuffer?
) : Closeable {
  private val buffer = GpuBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, GpuBuffer.INT_SIZE, entries)

  /** Populate with new data. */
  fun set(entries: IntBuffer?) {
    buffer.set(entries)
  }

  override fun close() {
    buffer.free()
  }

  internal fun getBufferId(): Int = buffer.getBufferId()

  internal fun getSize(): Int = buffer.getSize()
}
