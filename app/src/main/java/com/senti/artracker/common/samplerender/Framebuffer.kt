package com.senti.artracker.common.samplerender

import android.opengl.GLES30
import android.util.Log
import java.io.Closeable

/** A framebuffer associated with a texture. */
class Framebuffer(render: SampleRender, width: Int, height: Int) : Closeable {
  private val framebufferId = IntArray(1)
  private val colorTexture: Texture
  private val depthTexture: Texture
  private var width: Int = -1
  private var height: Int = -1

  init {
    try {
      colorTexture = Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false)
      depthTexture = Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false)

      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture.textureId)
      GLError.maybeThrowGLException("Failed to bind depth texture", "glBindTexture")
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_NONE)
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri")
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri")
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri")

      resize(width, height)

      GLES30.glGenFramebuffers(1, framebufferId, 0)
      GLError.maybeThrowGLException("Framebuffer creation failed", "glGenFramebuffers")
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId[0])
      GLError.maybeThrowGLException("Failed to bind framebuffer", "glBindFramebuffer")
      GLES30.glFramebufferTexture2D(
        GLES30.GL_FRAMEBUFFER,
        GLES30.GL_COLOR_ATTACHMENT0,
        GLES30.GL_TEXTURE_2D,
        colorTexture.textureId,
        0
      )
      GLError.maybeThrowGLException(
        "Failed to bind color texture to framebuffer",
        "glFramebufferTexture2D"
      )
      GLES30.glFramebufferTexture2D(
        GLES30.GL_FRAMEBUFFER,
        GLES30.GL_DEPTH_ATTACHMENT,
        GLES30.GL_TEXTURE_2D,
        depthTexture.textureId,
        0
      )
      GLError.maybeThrowGLException(
        "Failed to bind depth texture to framebuffer",
        "glFramebufferTexture2D"
      )

      val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
      if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
        throw IllegalStateException("Framebuffer construction not complete: code $status")
      }
    } catch (t: Throwable) {
      close()
      throw t
    }
  }

  override fun close() {
    if (framebufferId[0] != 0) {
      GLES30.glDeleteFramebuffers(1, framebufferId, 0)
      GLError.maybeLogGLError(
        Log.WARN,
        TAG,
        "Failed to free framebuffer",
        "glDeleteFramebuffers"
      )
      framebufferId[0] = 0
    }
    colorTexture.close()
    depthTexture.close()
  }

  /** Resizes the framebuffer to the given dimensions. */
  fun resize(width: Int, height: Int) {
    if (this.width == width && this.height == height) {
      return
    }
    this.width = width
    this.height = height

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture.textureId)
    GLError.maybeThrowGLException("Failed to bind color texture", "glBindTexture")
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D,
      0,
      GLES30.GL_RGBA,
      width,
      height,
      0,
      GLES30.GL_RGBA,
      GLES30.GL_UNSIGNED_BYTE,
      null
    )
    GLError.maybeThrowGLException("Failed to specify color texture format", "glTexImage2D")

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture.textureId)
    GLError.maybeThrowGLException("Failed to bind depth texture", "glBindTexture")
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D,
      0,
      GLES30.GL_DEPTH_COMPONENT32F,
      width,
      height,
      0,
      GLES30.GL_DEPTH_COMPONENT,
      GLES30.GL_FLOAT,
      null
    )
    GLError.maybeThrowGLException("Failed to specify depth texture format", "glTexImage2D")
  }

  fun getColorTexture(): Texture = colorTexture

  fun getDepthTexture(): Texture = depthTexture

  fun getWidth(): Int = width

  fun getHeight(): Int = height

  internal fun getFramebufferId(): Int = framebufferId[0]

  companion object {
    private val TAG = Framebuffer::class.java.simpleName
  }
}
