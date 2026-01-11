package com.senti.artracker.common.samplerender

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer

/** A GPU-side texture. */
class Texture(
  @Suppress("UNUSED_PARAMETER") render: SampleRender,
  val target: Target,
  wrapMode: WrapMode,
  useMipmaps: Boolean = true
) : Closeable {
  private val textureHandle = IntArray(1)

  val textureId: Int
    get() = textureHandle[0]

  init {
    GLES30.glGenTextures(1, textureHandle, 0)
    GLError.maybeThrowGLException("Texture creation failed", "glGenTextures")

    val minFilter = if (useMipmaps) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR

    try {
      GLES30.glBindTexture(target.glesEnum, textureHandle[0])
      GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture")
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_MIN_FILTER, minFilter)
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri")
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri")

      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_WRAP_S, wrapMode.glesEnum)
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri")
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_WRAP_T, wrapMode.glesEnum)
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri")
    } catch (t: Throwable) {
      close()
      throw t
    }
  }

  /** Create a texture from the given asset file name. */
  companion object {
    private val TAG = Texture::class.java.simpleName

    @Throws(IOException::class)
    fun createFromAsset(
      render: SampleRender,
      assetFileName: String,
      wrapMode: WrapMode,
      colorFormat: ColorFormat
    ): Texture {
      val texture = Texture(render, Target.TEXTURE_2D, wrapMode)
      var bitmap: Bitmap? = null
      try {
        bitmap = convertBitmapToConfig(
          BitmapFactory.decodeStream(render.assets.open(assetFileName)),
          Bitmap.Config.ARGB_8888
        )
        val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.textureId)
        GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture")
        GLES30.glTexImage2D(
          GLES30.GL_TEXTURE_2D,
          0,
          colorFormat.glesEnum,
          bitmap.width,
          bitmap.height,
          0,
          GLES30.GL_RGBA,
          GLES30.GL_UNSIGNED_BYTE,
          buffer
        )
        GLError.maybeThrowGLException("Failed to populate texture data", "glTexImage2D")
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLError.maybeThrowGLException("Failed to generate mipmaps", "glGenerateMipmap")
      } catch (t: Throwable) {
        texture.close()
        throw t
      } finally {
        bitmap?.recycle()
      }
      return texture
    }

    private fun convertBitmapToConfig(bitmap: Bitmap, config: Bitmap.Config): Bitmap {
      if (bitmap.config == config) {
        return bitmap
      }
      val result = bitmap.copy(config, false)
      bitmap.recycle()
      return result
    }
  }

  override fun close() {
    if (textureHandle[0] != 0) {
      GLES30.glDeleteTextures(1, textureHandle, 0)
      GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free texture", "glDeleteTextures")
      textureHandle[0] = 0
    }
  }

  enum class WrapMode(val glesEnum: Int) {
    CLAMP_TO_EDGE(GLES30.GL_CLAMP_TO_EDGE),
    MIRRORED_REPEAT(GLES30.GL_MIRRORED_REPEAT),
    REPEAT(GLES30.GL_REPEAT)
  }

  enum class Target(val glesEnum: Int) {
    TEXTURE_2D(GLES30.GL_TEXTURE_2D),
    TEXTURE_EXTERNAL_OES(GLES11Ext.GL_TEXTURE_EXTERNAL_OES),
    TEXTURE_CUBE_MAP(GLES30.GL_TEXTURE_CUBE_MAP)
  }

  enum class ColorFormat(val glesEnum: Int) {
    LINEAR(GLES30.GL_RGBA8),
    SRGB(GLES30.GL_SRGB8_ALPHA8)
  }
}
