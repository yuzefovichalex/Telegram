#version 300 es

layout(location = 0) in vec2 pos;
out vec2 uv;

uniform bool uFlipY;

void main() {
    vec2 baseUV = pos * 0.5 + 0.5;
    uv = vec2(baseUV.x, uFlipY ? (1.0 - baseUV.y) : baseUV.y);
    gl_Position = vec4(pos, 0.0, 1.0);
}