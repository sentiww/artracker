package com.senti.artracker.common.samplerender

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** A SampleRender context. */
class SampleRender(
  glSurfaceView: GLSurfaceView,
  private val renderer: Renderer,
  private val assetManager: AssetManager
) {
  private var viewportWidth = 1
  private var viewportHeight = 1

  init {
    glSurfaceView.preserveEGLContextOnPause = true
    glSurfaceView.setEGLContextClientVersion(3)
    glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
    glSurfaceView.setRenderer(
      object : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
          GLES30.glEnable(GLES30.GL_BLEND)
          GLError.maybeThrowGLException("Failed to enable blending", "glEnable")
          renderer.onSurfaceCreated(this@SampleRender)
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
          viewportWidth = width
          viewportHeight = height
          renderer.onSurfaceChanged(this@SampleRender, width, height)
        }

        override fun onDrawFrame(gl: GL10) {
          clear(null, 0f, 0f, 0f, 1f)
          renderer.onDrawFrame(this@SampleRender)
        }
      }
    )
    glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    glSurfaceView.setWillNotDraw(false)
  }

  /** Draw a [Mesh] with the specified [Shader]. */
  fun draw(mesh: Mesh, shader: Shader) {
    draw(mesh, shader, null)
  }

  /** Draw a [Mesh] with the specified [Shader] to the given [Framebuffer]. */
  fun draw(mesh: Mesh, shader: Shader, framebuffer: Framebuffer?) {
    useFramebuffer(framebuffer)
    shader.lowLevelUse()
    mesh.lowLevelDraw()
  }

  /** Clear the given framebuffer. */
  fun clear(framebuffer: Framebuffer?, r: Float, g: Float, b: Float, a: Float) {
    useFramebuffer(framebuffer)
    GLES30.glClearColor(r, g, b, a)
    GLError.maybeThrowGLException("Failed to set clear color", "glClearColor")
    GLES30.glDepthMask(true)
    GLError.maybeThrowGLException("Failed to set depth write mask", "glDepthMask")
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    GLError.maybeThrowGLException("Failed to clear framebuffer", "glClear")
  }

  val assets: AssetManager
    get() = assetManager

  interface Renderer {
    fun onSurfaceCreated(render: SampleRender)
    fun onSurfaceChanged(render: SampleRender, width: Int, height: Int)
    fun onDrawFrame(render: SampleRender)
  }

  private fun useFramebuffer(framebuffer: Framebuffer?) {
    val framebufferId: Int
    val width: Int
    val height: Int
    if (framebuffer == null) {
      framebufferId = 0
      width = viewportWidth
      height = viewportHeight
    } else {
      framebufferId = framebuffer.getFramebufferId()
      width = framebuffer.getWidth()
      height = framebuffer.getHeight()
    }
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId)
    GLError.maybeThrowGLException("Failed to bind framebuffer", "glBindFramebuffer")
    GLES30.glViewport(0, 0, width, height)
    GLError.maybeThrowGLException("Failed to set viewport dimensions", "glViewport")
  }
}
