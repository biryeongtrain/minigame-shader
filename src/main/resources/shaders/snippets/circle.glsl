void main() {
    float Time = cos(vertexColor.a * PI / 2);
    color = vec4(vertexColor.rgb, (length((gl_FragCoord.xy / ScreenSize - 0.5) / vec2(ScreenSize.y / ScreenSize.x, 1)) + 0.1 - Time) * (1 - Time) * 100);
}