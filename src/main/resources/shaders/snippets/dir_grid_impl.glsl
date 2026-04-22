void main() {
    int s = int(vertexColor.r * 255);
    vec2 grid = (ivec2(gl_FragCoord.xy / 32) * 32);
    vec2 inGrid = gl_FragCoord.xy - grid - 16;
    float size;

    switch (int(vertexColor.b * 255)) {
        case 0: size = grid.x / ScreenSize.x; break;
        case 1: size = 1 - grid.x / ScreenSize.x; break;
        case 2: size = grid.y / ScreenSize.y; break;
        default: size = 1 - grid.y / ScreenSize.y; break;
    }

    size = (size - vertexColor.a * 2 + 1) * 32;
    color = (abs(inGrid.x) + abs(inGrid.y) > size) ? vec4(0, 0, 0, 1) : vec4(0);
}