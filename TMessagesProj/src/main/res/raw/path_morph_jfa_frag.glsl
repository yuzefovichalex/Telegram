#version 300 es

precision highp float;

in vec2 uv;
layout(location = 0) out vec4 outColor;
uniform sampler2D uPrev;
uniform float uStep;

void main() {
    vec2 best = texture(uPrev, uv).xy;
    float bd = (best.x < 0.0) ? 1e6 : dot(uv - best, uv - best);
    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) continue;
            vec2 o = uv + vec2(float(dx), float(dy)) * uStep;
            vec2 s = texture(uPrev, o).xy;
            if (s.x < 0.0) continue;
            float d = dot(uv - s, uv - s);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
    }
    outColor = vec4(best, 0.0, 1.0);
}