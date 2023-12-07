precision mediump float;
varying vec4 v_Color;

void main() {
    // Do not draw shaders with alpha == 0.0
    if (v_Color.z == 0.0) {
        discard;
    }

    // Make shader cirle
    vec2 coord = gl_PointCoord - vec2(0.5);
    if (length(coord) > 0.5) {
        discard;
    }

    gl_FragColor = v_Color;
}