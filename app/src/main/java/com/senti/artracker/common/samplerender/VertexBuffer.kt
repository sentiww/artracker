package com.senti.artracker.common.samplerender

import android.opengl.GLES30
import java.io.Closeable
import java.nio.FloatBuffer

/** A list of vertex attribute data stored GPU-side. */
class VertexBuffer(
  @Suppress("UNUSED_PARAMETER") render: SampleRender,
  private val numberOfEntriesPerVertex: Int,
  entries: FloatBuffer?
) : Closeable {
  private val buffer: GpuBuffer

  init {
    if (entries != null && entries.limit() % numberOfEntriesPerVertex != 0) {
      throw IllegalArgumentException(
        "If non-null, vertex buffer data must be divisible by the number of data points per vertex"
      )
    }
    buffer = GpuBuffer(GLES30.GL_ARRAY_BUFFER, GpuBuffer.FLOAT_SIZE, entries)
  }

  /** Populate with new data. */
  fun set(entries: FloatBuffer?) {
    if (entries != null && entries.limit() % numberOfEntriesPerVertex != 0) {
      throw IllegalArgumentException(
        "If non-null, vertex buffer data must be divisible by the number of data points per vertex"
      )
    }
    buffer.set(entries)
  }

  override fun close() {
    buffer.free()
  }

  internal fun getBufferId(): Int = buffer.getBufferId()

  internal fun getNumberOfEntriesPerVertex(): Int = numberOfEntriesPerVertex

  internal fun getNumberOfVertices(): Int = buffer.getSize() / numberOfEntriesPerVertex
}
