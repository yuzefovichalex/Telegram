precision mediump float;

uniform vec2 u_ViewportSize;
uniform vec2 rectSize;
uniform vec2 rectPos;
uniform float rectRadius;
uniform vec3 circleData;
uniform float progress;

varying vec2 v_TextCoord;

float circle(vec2 samplePosition, float radius){
    return length(samplePosition) - radius;
}

float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x>0.0)?r.xy : r.zw;
    r.x  = (p.y>0.0)?r.x  : r.y;
    vec2 q = abs(p)-b+r.x;
    return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - r.x;
}

vec2 translate(vec2 samplePosition, vec2 offset){
    return samplePosition - offset;
}

float intersect(float shape1, float shape2){
    return max(shape1, shape2);
}

float merge(float shape1, float shape2){
    return min(shape1, shape2);
}

float round_merge(float shape1, float shape2, float radius){
    vec2 intersectionSpace = vec2(shape1 - radius, shape2 - radius);
    intersectionSpace = min(intersectionSpace, 0.0);
    float insideDistance = -length(intersectionSpace);
    float simpleUnion = merge(shape1, shape2);
    float outsideDistance = max(simpleUnion, radius);
    return  insideDistance + outsideDistance;
}

void main() {
    vec2 st = v_TextCoord;
    float ratio = u_ViewportSize.x / u_ViewportSize.y;
    st.x *= ratio;

    vec2 halfRectSize = rectSize / 2.0;
    float rectD = sdRoundedBox(
        translate(st, vec2((rectPos.x + halfRectSize.x) * ratio, rectPos.y + halfRectSize.y) / u_ViewportSize),
        vec2(halfRectSize.x * ratio, halfRectSize.y) / u_ViewportSize,
        vec4(rectRadius / u_ViewportSize.x * ratio)
    );

    float circleD = circle(
        translate(st, vec2(circleData.x * ratio, circleData.y) / u_ViewportSize),
        circleData.z / u_ViewportSize.x * ratio
    );

    float d = round_merge(circleD, rectD, 0.05);
    gl_FragColor = vec4(1.0, 1.0, 1.0, step(0.0, -d));
}