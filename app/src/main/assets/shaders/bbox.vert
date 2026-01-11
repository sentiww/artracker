#version 300 es
layout(location = 0) in vec2 aPosition;

uniform mat4 u_ViewProjection;
uniform vec3 u_BoxOrigin;
uniform float u_Width;
uniform float u_Height;
uniform vec3 u_Right;
uniform vec3 u_Up;

void main() {
  vec3 right = normalize(u_Right);
  vec3 up = normalize(u_Up);
  vec3 offset = right * (aPosition.x * 0.5 * u_Width) + up * (aPosition.y * 0.5 * u_Height);
  vec3 worldPos = u_BoxOrigin + offset;
  gl_Position = u_ViewProjection * vec4(worldPos, 1.0);
}
