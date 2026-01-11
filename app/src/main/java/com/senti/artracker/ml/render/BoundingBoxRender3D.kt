package com.senti.artracker.ml.render

import com.google.ar.core.Pose
import com.senti.artracker.common.samplerender.Mesh
import com.senti.artracker.common.samplerender.SampleRender
import com.senti.artracker.common.samplerender.Shader
import com.senti.artracker.common.samplerender.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BoundingBoxRender3D {

  companion object {
    private val RECT_COORDS = floatArrayOf(
      -1f, -1f,
      1f, -1f,
      1f, 1f,
      -1f, 1f,
      -1f, -1f
    )
  }

  private lateinit var mesh: Mesh
  private lateinit var shader: Shader

  fun onSurfaceCreated(render: SampleRender) {
    val vertexBuffer = VertexBuffer(
      render,
      2,
      ByteBuffer.allocateDirect(RECT_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(RECT_COORDS)
        .apply { rewind() }
    )
    mesh = Mesh(render, Mesh.PrimitiveMode.LINE_STRIP, null, arrayOf(vertexBuffer))
    shader = Shader.createFromAssets(render, "shaders/bbox.vert", "shaders/bbox.frag", null)
      .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA)
      .setDepthTest(true)
      .setDepthWrite(false)
  }

  private val origin = FloatArray(3)

  fun draw(
    render: SampleRender,
    viewProjectionMatrix: FloatArray,
    pose: Pose,
    widthMeters: Float,
    heightMeters: Float,
    rightAxis: FloatArray,
    upAxis: FloatArray
  ) {
    origin[0] = pose.tx()
    origin[1] = pose.ty()
    origin[2] = pose.tz()
    shader
      .setMat4("u_ViewProjection", viewProjectionMatrix)
      .setVec3("u_BoxOrigin", origin)
      .setFloat("u_Width", widthMeters)
      .setFloat("u_Height", heightMeters)
      .setVec3("u_Right", rightAxis)
      .setVec3("u_Up", upAxis)
      .setVec4("uColor", floatArrayOf(0f, 1f, 0f, 0.8f))
    render.draw(mesh, shader)
  }
}
