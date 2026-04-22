

vec4 fractal2() {
    vec2 fragCoord = gl_FragCoord.xy;
    vec2 uv = (fragCoord * 2.0 - ScreenSize.xy) / ScreenSize.y;
    vec2 uv0 = uv;
    vec3 finalColor = vec3(0.0);

    float time = GameTime * 1000.0;

    for (int i = 0; i < 4; ++i) {
        float fi = float(i);
        uv = fract(uv * 1.5) - 0.5;

        float d = length(uv) * exp(-length(uv0));

        // palette (iquilez)
        vec3 a = vec3(0.5, 0.5, 0.5);
        vec3 b = vec3(0.5, 0.5, 0.5);
        vec3 c = vec3(1.0, 1.0, 1.0);
        vec3 dcol = vec3(0.263, 0.416, 0.557);
        float t = length(uv0) + fi * 0.4 + time * 0.4;
        vec3 col = a + b * cos(6.28318 * (c * t + dcol));

        float dd = sin(d * 8.0 + time) / 8.0;
        dd = abs(dd);
        // avoid NaN
        dd = max(dd, 1e-6);
        dd = pow(0.01 / dd, 1.2);

        finalColor += col * dd;
    }

    return vec4(finalColor, 1.0);
}