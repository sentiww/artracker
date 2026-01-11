package com.senti.artracker.common.samplerender

import android.opengl.GLES30
import android.opengl.GLException
import android.util.Log
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/** Represents a GPU shader, the state of its associated uniforms, and some additional draw state. */
class Shader(
  @Suppress("UNUSED_PARAMETER") render: SampleRender,
  vertexShaderCode: String,
  fragmentShaderCode: String,
  defines: Map<String, String>?
) : Closeable {
  private var programId = 0
  private val uniforms = mutableMapOf<Int, Uniform>()
  private var maxTextureUnit = 0
  private val uniformLocations = mutableMapOf<String, Int>()
  private val uniformNames = mutableMapOf<Int, String>()

  private var depthTest = true
  private var depthWrite = true
  private var sourceRgbBlend = BlendFactor.ONE
  private var destRgbBlend = BlendFactor.ZERO
  private var sourceAlphaBlend = BlendFactor.ONE
  private var destAlphaBlend = BlendFactor.ZERO

  init {
    var vertexShaderId = 0
    var fragmentShaderId = 0
    val definesCode = createShaderDefinesCode(defines)
    try {
      vertexShaderId =
        createShader(GLES30.GL_VERTEX_SHADER, insertShaderDefinesCode(vertexShaderCode, definesCode))
      fragmentShaderId =
        createShader(
          GLES30.GL_FRAGMENT_SHADER,
          insertShaderDefinesCode(fragmentShaderCode, definesCode)
        )

      programId = GLES30.glCreateProgram()
      GLError.maybeThrowGLException("Shader program creation failed", "glCreateProgram")
      GLES30.glAttachShader(programId, vertexShaderId)
      GLError.maybeThrowGLException("Failed to attach vertex shader", "glAttachShader")
      GLES30.glAttachShader(programId, fragmentShaderId)
      GLError.maybeThrowGLException("Failed to attach fragment shader", "glAttachShader")
      GLES30.glLinkProgram(programId)
      GLError.maybeThrowGLException("Failed to link shader program", "glLinkProgram")

      val linkStatus = IntArray(1)
      GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
      if (linkStatus[0] == GLES30.GL_FALSE) {
        val infoLog = GLES30.glGetProgramInfoLog(programId)
        GLError.maybeLogGLError(
          Log.WARN,
          TAG,
          "Failed to retrieve shader program info log",
          "glGetProgramInfoLog"
        )
        throw GLException(0, "Shader link failed: $infoLog")
      }
    } catch (t: Throwable) {
      close()
      throw t
    } finally {
      if (vertexShaderId != 0) {
        GLES30.glDeleteShader(vertexShaderId)
        GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free vertex shader", "glDeleteShader")
      }
      if (fragmentShaderId != 0) {
        GLES30.glDeleteShader(fragmentShaderId)
        GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free fragment shader", "glDeleteShader")
      }
    }
  }

  override fun close() {
    if (programId != 0) {
      GLES30.glDeleteProgram(programId)
      programId = 0
    }
  }

  fun setDepthTest(depthTest: Boolean): Shader {
    this.depthTest = depthTest
    return this
  }

  fun setDepthWrite(depthWrite: Boolean): Shader {
    this.depthWrite = depthWrite
    return this
  }

  fun setBlend(sourceBlend: BlendFactor, destBlend: BlendFactor): Shader {
    sourceRgbBlend = sourceBlend
    destRgbBlend = destBlend
    sourceAlphaBlend = sourceBlend
    destAlphaBlend = destBlend
    return this
  }

  fun setBlend(
    sourceRgbBlend: BlendFactor,
    destRgbBlend: BlendFactor,
    sourceAlphaBlend: BlendFactor,
    destAlphaBlend: BlendFactor
  ): Shader {
    this.sourceRgbBlend = sourceRgbBlend
    this.destRgbBlend = destRgbBlend
    this.sourceAlphaBlend = sourceAlphaBlend
    this.destAlphaBlend = destAlphaBlend
    return this
  }

  fun setTexture(name: String, texture: Texture): Shader {
    val location = getUniformLocation(name)
    val existing = uniforms[location]
    val textureUnit = if (existing is UniformTexture) existing.textureUnit else maxTextureUnit++
    uniforms[location] = UniformTexture(textureUnit, texture)
    return this
  }

  fun setBool(name: String, v0: Boolean): Shader {
    val values = intArrayOf(if (v0) 1 else 0)
    uniforms[getUniformLocation(name)] = UniformInt(values)
    return this
  }

  fun setInt(name: String, v0: Int): Shader {
    val values = intArrayOf(v0)
    uniforms[getUniformLocation(name)] = UniformInt(values)
    return this
  }

  fun setFloat(name: String, v0: Float): Shader {
    val values = floatArrayOf(v0)
    uniforms[getUniformLocation(name)] = Uniform1f(values)
    return this
  }

  fun setVec2(name: String, values: FloatArray): Shader {
    require(values.size == 2) { "Value array length must be 2" }
    uniforms[getUniformLocation(name)] = Uniform2f(values.clone())
    return this
  }

  fun setVec3(name: String, values: FloatArray): Shader {
    require(values.size == 3) { "Value array length must be 3" }
    uniforms[getUniformLocation(name)] = Uniform3f(values.clone())
    return this
  }

  fun setVec4(name: String, values: FloatArray): Shader {
    require(values.size == 4) { "Value array length must be 4" }
    uniforms[getUniformLocation(name)] = Uniform4f(values.clone())
    return this
  }

  fun setMat2(name: String, values: FloatArray): Shader {
    require(values.size == 4) { "Value array length must be 4 (2x2)" }
    uniforms[getUniformLocation(name)] = UniformMatrix2f(values.clone())
    return this
  }

  fun setMat3(name: String, values: FloatArray): Shader {
    require(values.size == 9) { "Value array length must be 9 (3x3)" }
    uniforms[getUniformLocation(name)] = UniformMatrix3f(values.clone())
    return this
  }

  fun setMat4(name: String, values: FloatArray): Shader {
    require(values.size == 16) { "Value array length must be 16 (4x4)" }
    uniforms[getUniformLocation(name)] = UniformMatrix4f(values.clone())
    return this
  }

  fun setBoolArray(name: String, values: BooleanArray): Shader {
    val intValues = IntArray(values.size)
    for (i in values.indices) {
      intValues[i] = if (values[i]) 1 else 0
    }
    uniforms[getUniformLocation(name)] = UniformInt(intValues)
    return this
  }

  fun setIntArray(name: String, values: IntArray): Shader {
    uniforms[getUniformLocation(name)] = UniformInt(values.clone())
    return this
  }

  fun setFloatArray(name: String, values: FloatArray): Shader {
    uniforms[getUniformLocation(name)] = Uniform1f(values.clone())
    return this
  }

  fun setVec2Array(name: String, values: FloatArray): Shader {
    require(values.size % 2 == 0) { "Value array length must be divisible by 2" }
    uniforms[getUniformLocation(name)] = Uniform2f(values.clone())
    return this
  }

  fun setVec3Array(name: String, values: FloatArray): Shader {
    require(values.size % 3 == 0) { "Value array length must be divisible by 3" }
    uniforms[getUniformLocation(name)] = Uniform3f(values.clone())
    return this
  }

  fun setVec4Array(name: String, values: FloatArray): Shader {
    require(values.size % 4 == 0) { "Value array length must be divisible by 4" }
    uniforms[getUniformLocation(name)] = Uniform4f(values.clone())
    return this
  }

  fun setMat2Array(name: String, values: FloatArray): Shader {
    require(values.size % 4 == 0) { "Value array length must be divisible by 4 (2x2)" }
    uniforms[getUniformLocation(name)] = UniformMatrix2f(values.clone())
    return this
  }

  fun setMat3Array(name: String, values: FloatArray): Shader {
    require(values.size % 9 == 0) { "Values array length must be divisible by 9 (3x3)" }
    uniforms[getUniformLocation(name)] = UniformMatrix3f(values.clone())
    return this
  }

  fun setMat4Array(name: String, values: FloatArray): Shader {
    require(values.size % 16 == 0) { "Value array length must be divisible by 16 (4x4)" }
    uniforms[getUniformLocation(name)] = UniformMatrix4f(values.clone())
    return this
  }

  fun lowLevelUse() {
    if (programId == 0) {
      throw IllegalStateException("Attempted to use freed shader")
    }
    GLES30.glUseProgram(programId)
    GLError.maybeThrowGLException("Failed to use shader program", "glUseProgram")
    GLES30.glBlendFuncSeparate(
      sourceRgbBlend.glesEnum,
      destRgbBlend.glesEnum,
      sourceAlphaBlend.glesEnum,
      destAlphaBlend.glesEnum
    )
    GLError.maybeThrowGLException("Failed to set blend mode", "glBlendFuncSeparate")
    GLES30.glDepthMask(depthWrite)
    GLError.maybeThrowGLException("Failed to set depth write mask", "glDepthMask")
    if (depthTest) {
      GLES30.glEnable(GLES30.GL_DEPTH_TEST)
      GLError.maybeThrowGLException("Failed to enable depth test", "glEnable")
    } else {
      GLES30.glDisable(GLES30.GL_DEPTH_TEST)
      GLError.maybeThrowGLException("Failed to disable depth test", "glDisable")
    }
    try {
      val obsoleteEntries = ArrayList<Int>(uniforms.size)
      for ((location, uniform) in uniforms) {
        try {
          uniform.use(location)
          if (uniform !is UniformTexture) {
            obsoleteEntries.add(location)
          }
        } catch (e: GLException) {
          val name = uniformNames[location]
          throw IllegalArgumentException("Error setting uniform `$name`", e)
        }
      }
      obsoleteEntries.forEach { uniforms.remove(it) }
    } finally {
      GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
      GLError.maybeLogGLError(Log.WARN, TAG, "Failed to set active texture", "glActiveTexture")
    }
  }

  private fun getUniformLocation(name: String): Int {
    val location = uniformLocations[name]
    if (location != null) {
      return location
    }
    val resolvedLocation = GLES30.glGetUniformLocation(programId, name)
    GLError.maybeThrowGLException("Failed to find uniform", "glGetUniformLocation")
    if (resolvedLocation == -1) {
      throw IllegalArgumentException("Shader uniform does not exist: $name")
    }
    uniformLocations[name] = resolvedLocation
    uniformNames[resolvedLocation] = name
    return resolvedLocation
  }

  private interface Uniform {
    fun use(location: Int)
  }

  private class UniformTexture(val textureUnit: Int, private val texture: Texture) : Uniform {
    override fun use(location: Int) {
      if (texture.textureId == 0) {
        throw IllegalStateException("Tried to draw with freed texture")
      }
      GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + textureUnit)
      GLError.maybeThrowGLException("Failed to set active texture", "glActiveTexture")
      GLES30.glBindTexture(texture.target.glesEnum, texture.textureId)
      GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture")
      GLES30.glUniform1i(location, textureUnit)
      GLError.maybeThrowGLException("Failed to set shader texture uniform", "glUniform1i")
    }
  }

  private class UniformInt(private val values: IntArray) : Uniform {
    override fun use(location: Int) {
      GLES30.glUniform1iv(location, values.size, values, 0)
      GLError.maybeThrowGLException("Failed to set shader uniform 1i", "glUniform1iv")
    }
  }

  private class Uniform1f(private val values: FloatArray) : Uniform {
    override fun use(location: Int) {
      GLES30.glUniform1fv(location, values.size, values, 0)
      GLError.maybeThrowGLException("Failed to set shader uniform 1f", "glUniform1fv")
    }
  }

  private class Uniform2f(private val values: FloatArray) : Uniform {
    override fun use(location: Int) {
      GLES30.glUniform2fv(location, values.size / 2, values, 0)
      GLError.maybeThrowGLException("Failed to set shader uniform 2f", "glUniform2fv")
    }
  }

  private class Uniform3f(private val values: FloatArray) : Uniform {
    override fun use(location: Int) {
      GLES30.glUniform3fv(location, values.size / 3, values, 0)
      GLError.maybeThrowGLException("Failed to set shader uniform 3f", "glUniform3fv")
    }
  }

  private class Uniform4f(private val values: FloatArray) : Uniform {
    override fun use(location: Int) {
      GLES30.glUniform4fv(location, values.size / 4, values, 0)
      GLError.maybeThrowGLException("Failed to set shader uniform 4f", "glUniform4fv")
    }
  }

  private class UniformMatrix2f(private val values: FloatArray) : Uniform {
    override fun use(location: Int) {
      GLES30.glUniformMatrix2fv(location, values.size / 4, false, values, 0)
      GLError.maybeThrowGLException(
        "Failed to set shader uniform matrix 2f",
        "glUniformMatrix2fv"
      )
    }
  }

  private class UniformMatrix3f(private val values: FloatArray) : Uniform {
    override fun use(location: Int) {
      GLES30.glUniformMatrix3fv(location, values.size / 9, false, values, 0)
      GLError.maybeThrowGLException(
        "Failed to set shader uniform matrix 3f",
        "glUniformMatrix3fv"
      )
    }
  }

  private class UniformMatrix4f(private val values: FloatArray) : Uniform {
    override fun use(location: Int) {
      GLES30.glUniformMatrix4fv(location, values.size / 16, false, values, 0)
      GLError.maybeThrowGLException(
        "Failed to set shader uniform matrix 4f",
        "glUniformMatrix4fv"
      )
    }
  }

  enum class BlendFactor(val glesEnum: Int) {
    ZERO(GLES30.GL_ZERO),
    ONE(GLES30.GL_ONE),
    SRC_COLOR(GLES30.GL_SRC_COLOR),
    ONE_MINUS_SRC_COLOR(GLES30.GL_ONE_MINUS_SRC_COLOR),
    DST_COLOR(GLES30.GL_DST_COLOR),
    ONE_MINUS_DST_COLOR(GLES30.GL_ONE_MINUS_DST_COLOR),
    SRC_ALPHA(GLES30.GL_SRC_ALPHA),
    ONE_MINUS_SRC_ALPHA(GLES30.GL_ONE_MINUS_SRC_ALPHA),
    DST_ALPHA(GLES30.GL_DST_ALPHA),
    ONE_MINUS_DST_ALPHA(GLES30.GL_ONE_MINUS_DST_ALPHA),
    CONSTANT_COLOR(GLES30.GL_CONSTANT_COLOR),
    ONE_MINUS_CONSTANT_COLOR(GLES30.GL_ONE_MINUS_CONSTANT_COLOR),
    CONSTANT_ALPHA(GLES30.GL_CONSTANT_ALPHA),
    ONE_MINUS_CONSTANT_ALPHA(GLES30.GL_ONE_MINUS_CONSTANT_ALPHA)
  }

  companion object {
    private val TAG = Shader::class.java.simpleName

    @Throws(IOException::class)
    fun createFromAssets(
      render: SampleRender,
      vertexShaderFileName: String,
      fragmentShaderFileName: String,
      defines: Map<String, String>?
    ): Shader {
      val assets = render.assets
      return Shader(
        render,
        inputStreamToString(assets.open(vertexShaderFileName)),
        inputStreamToString(assets.open(fragmentShaderFileName)),
        defines
      )
    }

    private fun createShader(type: Int, code: String): Int {
      val shaderId = GLES30.glCreateShader(type)
      GLError.maybeThrowGLException("Shader creation failed", "glCreateShader")
      GLES30.glShaderSource(shaderId, code)
      GLError.maybeThrowGLException("Shader source failed", "glShaderSource")
      GLES30.glCompileShader(shaderId)
      GLError.maybeThrowGLException("Shader compilation failed", "glCompileShader")

      val compileStatus = IntArray(1)
      GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
      if (compileStatus[0] == GLES30.GL_FALSE) {
        val infoLog = GLES30.glGetShaderInfoLog(shaderId)
        GLError.maybeLogGLError(
          Log.WARN,
          TAG,
          "Failed to retrieve shader info log",
          "glGetShaderInfoLog"
        )
        GLES30.glDeleteShader(shaderId)
        GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free shader", "glDeleteShader")
        throw GLException(0, "Shader compilation failed: $infoLog")
      }

      return shaderId
    }

    private fun createShaderDefinesCode(defines: Map<String, String>?): String {
      if (defines == null) {
        return ""
      }
      val builder = StringBuilder()
      for ((key, value) in defines) {
        builder.append("#define $key $value\n")
      }
      return builder.toString()
    }

    private fun insertShaderDefinesCode(sourceCode: String, definesCode: String): String {
      if (definesCode.isEmpty()) {
        return sourceCode
      }
      val regex = Regex("(?m)^(\\s*#\\s*version\\s+.*)$")
      val match = regex.find(sourceCode)
      return if (match != null) {
        buildString {
          append(sourceCode.substring(0, match.range.last + 1))
          append('\n')
          append(definesCode)
          append(sourceCode.substring(match.range.last + 1))
        }
      } else {
        definesCode + sourceCode
      }
    }

    private fun inputStreamToString(stream: InputStream): String {
      InputStreamReader(stream, Charsets.UTF_8).use { reader ->
        val buffer = CharArray(4 * 1024)
        val builder = StringBuilder()
        while (true) {
          val amount = reader.read(buffer)
          if (amount == -1) {
            break
          }
          builder.append(buffer, 0, amount)
        }
        return builder.toString()
      }
    }
  }
}
