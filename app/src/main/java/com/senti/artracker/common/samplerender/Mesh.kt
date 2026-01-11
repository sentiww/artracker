package com.senti.artracker.common.samplerender

import android.opengl.GLES30
import android.util.Log
import de.javagl.obj.ObjData
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import java.io.Closeable
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * A collection of vertices, faces, and other attributes that define how to render a 3D object.
 *
 * To render the mesh, use [SampleRender.draw].
 */
class Mesh(
  @Suppress("UNUSED_PARAMETER") render: SampleRender,
  private val primitiveMode: PrimitiveMode,
  private val indexBuffer: IndexBuffer?,
  private val vertexBuffers: Array<VertexBuffer>
) : Closeable {
  private val vertexArrayId = IntArray(1)

  init {
    require(vertexBuffers.isNotEmpty()) { "Must pass at least one vertex buffer" }

    try {
      GLES30.glGenVertexArrays(1, vertexArrayId, 0)
      GLError.maybeThrowGLException("Failed to generate a vertex array", "glGenVertexArrays")

      GLES30.glBindVertexArray(vertexArrayId[0])
      GLError.maybeThrowGLException("Failed to bind vertex array object", "glBindVertexArray")

      indexBuffer?.let {
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, it.getBufferId())
      }

      for (i in vertexBuffers.indices) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffers[i].getBufferId())
        GLError.maybeThrowGLException("Failed to bind vertex buffer", "glBindBuffer")
        GLES30.glVertexAttribPointer(
          i,
          vertexBuffers[i].getNumberOfEntriesPerVertex(),
          GLES30.GL_FLOAT,
          false,
          0,
          0
        )
        GLError.maybeThrowGLException(
          "Failed to associate vertex buffer with vertex array",
          "glVertexAttribPointer"
        )
        GLES30.glEnableVertexAttribArray(i)
        GLError.maybeThrowGLException("Failed to enable vertex buffer", "glEnableVertexAttribArray")
      }
    } catch (t: Throwable) {
      close()
      throw t
    }
  }

  override fun close() {
    if (vertexArrayId[0] != 0) {
      GLES30.glDeleteVertexArrays(1, vertexArrayId, 0)
      GLError.maybeLogGLError(
        Log.WARN,
        TAG,
        "Failed to free vertex array object",
        "glDeleteVertexArrays"
      )
      vertexArrayId[0] = 0
    }
  }

  /** Draws the mesh. */
  fun lowLevelDraw() {
    if (vertexArrayId[0] == 0) {
      throw IllegalStateException("Tried to draw a freed Mesh")
    }

    GLES30.glBindVertexArray(vertexArrayId[0])
    GLError.maybeThrowGLException("Failed to bind vertex array object", "glBindVertexArray")
    if (indexBuffer == null) {
      val numberOfVertices = vertexBuffers[0].getNumberOfVertices()
      for (i in 1 until vertexBuffers.size) {
        if (vertexBuffers[i].getNumberOfVertices() != numberOfVertices) {
          throw IllegalStateException("Vertex buffers have mismatching numbers of vertices")
        }
      }
      GLES30.glDrawArrays(primitiveMode.glesEnum, 0, numberOfVertices)
      GLError.maybeThrowGLException("Failed to draw vertex array object", "glDrawArrays")
    } else {
      GLES30.glDrawElements(
        primitiveMode.glesEnum,
        indexBuffer.getSize(),
        GLES30.GL_UNSIGNED_INT,
        0
      )
      GLError.maybeThrowGLException(
        "Failed to draw vertex array object with indices",
        "glDrawElements"
      )
    }
  }

  enum class PrimitiveMode(val glesEnum: Int) {
    POINTS(GLES30.GL_POINTS),
    LINE_STRIP(GLES30.GL_LINE_STRIP),
    LINE_LOOP(GLES30.GL_LINE_LOOP),
    LINES(GLES30.GL_LINES),
    TRIANGLE_STRIP(GLES30.GL_TRIANGLE_STRIP),
    TRIANGLE_FAN(GLES30.GL_TRIANGLE_FAN),
    TRIANGLES(GLES30.GL_TRIANGLES)
  }

  companion object {
    private val TAG = Mesh::class.java.simpleName

    @Throws(IOException::class)
    fun createFromAsset(render: SampleRender, assetFileName: String): Mesh {
      render.assets.open(assetFileName).use { inputStream ->
        val obj = ObjUtils.convertToRenderable(ObjReader.read(inputStream))
        val vertexIndices: IntBuffer = ObjData.getFaceVertexIndices(obj, 3)
        val localCoordinates: FloatBuffer = ObjData.getVertices(obj)
        val textureCoordinates: FloatBuffer = ObjData.getTexCoords(obj, 2)
        val normals: FloatBuffer = ObjData.getNormals(obj)

        val vertexBuffers = arrayOf(
          VertexBuffer(render, 3, localCoordinates),
          VertexBuffer(render, 2, textureCoordinates),
          VertexBuffer(render, 3, normals)
        )

        val indexBuffer = IndexBuffer(render, vertexIndices)
        return Mesh(render, PrimitiveMode.TRIANGLES, indexBuffer, vertexBuffers)
      }
    }
  }
}
