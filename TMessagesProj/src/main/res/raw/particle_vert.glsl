precision highp float;

attribute float a_PointSize;
attribute vec4 a_Color;
varying vec4 v_Color;

attribute vec2 a_InitialPosition;
attribute vec2 a_TargetPosition;
attribute vec2 a_Time;

float interpolateLinear(float start, float end, float factor) {
    return start + factor * (end - start);
}

float a(float time, float tension) {
    return time * time * ((tension + 1.0) * time - tension);
}

float o(float time, float tension) {
    return time * time * ((tension + 1.0) * time + tension);
}

float interpolateAnticipateOvershoot(float start, float end, float factor) {
    float tension = 1.5 * 1.5;
    if (factor < 0.5) {
        factor = 0.5 * a(factor * 2.0, tension);
    } else {
        factor = 0.5 * (o(factor * 2.0 - 2.0, tension) + 2.0);
    }
    return interpolateLinear(start, end, factor);
}

vec4 calculatePosition() {
    if (a_Time.y != 0.0) {
        float factor = a_Time.x / a_Time.y;
        float x = interpolateLinear(a_InitialPosition.x, a_TargetPosition.x, factor);
        // Particles fall down first and then fly to top
        float y = interpolateAnticipateOvershoot(a_InitialPosition.y, a_TargetPosition.y, factor);
        return vec4(x, y, 0.0, 1.0);
    } else {
        return vec4(a_InitialPosition.x, a_InitialPosition.y, 0.0, 1.0);
    }
}

vec4 calculateColor() {
    float factor = 2.0 * a_Time.x / a_Time.y;
    float alpha = interpolateLinear(1.0, 0.0, factor);
    return vec4(a_Color.x, a_Color.y, a_Color.z, alpha);
}

void main() {
    gl_Position = calculatePosition();
    v_Color = calculateColor();
    gl_PointSize = a_PointSize;
}