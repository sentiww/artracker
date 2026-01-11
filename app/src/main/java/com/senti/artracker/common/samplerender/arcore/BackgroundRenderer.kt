package com.senti.artracker.common.samplerender.arcore

import android.media.Image
import android.opengl.GLES30
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.senti.artracker.common.samplerender.Framebuffer
import com.senti.artracker.common.samplerender.Mesh
import com.senti.artracker.common.samplerender.SampleRender
import com.senti.artracker.common.samplerender.Shader
import com.senti.artracker.common.samplerender.Texture
import com.senti.artracker.common.samplerender.VertexBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * This class both renders the AR camera background and composes the a scene foreground.
 */
class BackgroundRenderer(render: SampleRender) {
  private val cameraTexCoords: FloatBuffer =
    ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer()

  private val mesh: Mesh
  private val cameraTexCoordsVertexBuffer: VertexBuffer
  private var backgroundShader: Shader? = null
  private var occlusionShader: Shader? = null
  val cameraDepthTexture: Texture
  val cameraColorTexture: Texture

  private var useDepthVisualization = false
  private var useOcclusion = false
  private var aspectRatio = 0f

  init {
    cameraColorTexture =
      Texture(
        render,
        Texture.Target.TEXTURE_EXTERNAL_OES,
        Texture.WrapMode.CLAMP_TO_EDGE,
        false
      )
    cameraDepthTexture =
      Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false)

    val screenCoordsVertexBuffer = VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER)
    cameraTexCoordsVertexBuffer = VertexBuffer(render, 2, null)
    val virtualSceneTexCoordsVertexBuffer = VertexBuffer(render, 2, VIRTUAL_SCENE_TEX_COORDS_BUFFER)
    val vertexBuffers =
      arrayOf(screenCoordsVertexBuffer, cameraTexCoordsVertexBuffer, virtualSceneTexCoordsVertexBuffer)
    mesh = Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, vertexBuffers)
  }

  /**
   * Sets whether the background camera image should be replaced with a depth visualization instead.
   */
  @Throws(IOException::class)
  fun setUseDepthVisualization(render: SampleRender, useDepthVisualization: Boolean) {
    backgroundShader?.let {
      if (this.useDepthVisualization == useDepthVisualization) {
        return
      }
      it.close()
      backgroundShader = null
    }
    this.useDepthVisualization = useDepthVisualization
    backgroundShader =
      if (useDepthVisualization) {
        Shader.createFromAssets(
            render,
            "shaders/background_show_depth_color_visualization.vert",
            "shaders/background_show_depth_color_visualization.frag",
            null
          )
          .setTexture("u_CameraDepthTexture", cameraDepthTexture)
          .setDepthTest(false)
          .setDepthWrite(false)
      } else {
        Shader.createFromAssets(
            render,
            "shaders/background_show_camera.vert",
            "shaders/background_show_camera.frag",
            null
          )
          .setTexture("u_CameraColorTexture", cameraColorTexture)
          .setDepthTest(false)
          .setDepthWrite(false)
      }
  }

  /** Updates the display geometry. */
  fun updateDisplayGeometry(frame: Frame) {
    if (frame.hasDisplayGeometryChanged()) {
      frame.transformCoordinates2d(
        Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
        NDC_QUAD_COORDS_BUFFER,
        Coordinates2d.TEXTURE_NORMALIZED,
        cameraTexCoords
      )
      cameraTexCoordsVertexBuffer.set(cameraTexCoords)
    }
  }

  /** Update depth texture with Image contents. */
  fun updateCameraDepthTexture(image: Image) {
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cameraDepthTexture.textureId)
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D,
      0,
      GLES30.GL_RG8,
      image.width,
      image.height,
      0,
      GLES30.GL_RG,
      GLES30.GL_UNSIGNED_BYTE,
      image.planes[0].buffer
    )
    if (useOcclusion) {
      aspectRatio = image.width.toFloat() / image.height.toFloat()
      occlusionShader?.setFloat("u_DepthAspectRatio", aspectRatio)
    }
  }

  /** Draws the AR background image. */
  fun drawBackground(render: SampleRender) {
    val shader = backgroundShader ?: throw IllegalStateException("Background shader not initialized")
    render.draw(mesh, shader)
  }

  /** Draws the virtual scene. */
  fun drawVirtualScene(render: SampleRender, virtualSceneFramebuffer: Framebuffer, zNear: Float, zFar: Float) {
    val shader = occlusionShader ?: throw IllegalStateException("Occlusion shader not initialized")
    shader.setTexture("u_VirtualSceneColorTexture", virtualSceneFramebuffer.getColorTexture())
    if (useOcclusion) {
      shader
        .setTexture("u_VirtualSceneDepthTexture", virtualSceneFramebuffer.getDepthTexture())
        .setFloat("u_ZNear", zNear)
        .setFloat("u_ZFar", zFar)
    }
    render.draw(mesh, shader)
  }

  companion object {
    private const val COORDS_BUFFER_SIZE = 2 * 4 * 4

    private val NDC_QUAD_COORDS_BUFFER: FloatBuffer =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
          put(floatArrayOf(-1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f))
          rewind()
        }

    private val VIRTUAL_SCENE_TEX_COORDS_BUFFER: FloatBuffer =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
          put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
          rewind()
        }
  }
}
