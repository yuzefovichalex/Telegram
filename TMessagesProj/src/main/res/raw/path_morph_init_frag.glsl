#version 300 es

precision highp float;

in vec2 uv;
layout(location = 0) out vec4 outColor;
uniform sampler2D uShape;

void main() {
    float shapeVal = texture(uShape, uv).r;
    if (shapeVal > 0.5) {
        outColor = vec4(uv, 0.0, 1.0);
    } else {
        outColor = vec4(-1.0, -1.0, 0.0, 1.0);
    }
}