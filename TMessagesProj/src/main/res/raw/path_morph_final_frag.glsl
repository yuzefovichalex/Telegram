#version 300 es

precision highp float;

in vec2 uv;
layout(location = 0) out vec4 outColor;

uniform sampler2D uJFA;
uniform sampler2D uShape;

uniform vec2 uDrop;
uniform float uRadius;
uniform float uK;

float smoothU(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

void main() {
    vec2 src = texture(uJFA, uv).xy;
    float dW = (src.x < 0.0) ? 1e3 : distance(uv, src);
    float distToDrop = length(uv - uDrop);
    float dD = distToDrop - uRadius;

    if (distToDrop < uRadius + 0.001 && uv.y > uDrop.y) {
        discard;
    }

    float d = smoothU(dW, dD, uK);
    outColor = vec4(0.0, 0.0, 0.0, smoothstep(-0.001, 0.0, -d));
}