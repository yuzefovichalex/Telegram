precision highp float;

attribute vec4 a_Position;
attribute vec2 a_TexCoord;

varying vec2 v_TextCoord;

void main() {
    gl_Position = a_Position;
    v_TextCoord = a_TexCoord;
}