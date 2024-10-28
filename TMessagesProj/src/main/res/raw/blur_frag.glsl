precision mediump float;

varying vec2 v_TextCoord;

uniform sampler2D u_Texture;
uniform vec2 u_TexelOffset;
uniform float u_Radius;

float gaussian(float x, float sigma) {
    return (1.0 / (sqrt(2.0 * 3.14159265359) * sigma)) * exp(-(x * x) / (2.0 * sigma * sigma));
}

void main() {
    float sigma = 1.0 + u_Radius / 2.0;
    vec4 color = vec4(0.0);
    float weight_sum = 0.0;

    for (int i = -int(u_Radius); i <= int(u_Radius); ++i) {
        vec2 sampleCoords = v_TextCoord + float(i) * u_TexelOffset;
        if (sampleCoords.x >= 0.0 && sampleCoords.x <= 1.0 && sampleCoords.y >= 0.0 && sampleCoords.y <= 1.0) {
            float weight = gaussian(float(i), sigma);
            color += texture2D(u_Texture, sampleCoords) * weight;
            weight_sum += weight;
        }
    }

    if (weight_sum > 0.0) {
        gl_FragColor = color / weight_sum;
    } else {
        gl_FragColor = texture2D(u_Texture, v_TextCoord);
    }
}
