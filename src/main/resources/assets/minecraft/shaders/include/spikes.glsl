#define SPIKES_SPEED 2000
#define SPIKES_RADIUS 0.09 // higher=less effect
#define SPIKES_COUNT 50
#define SPIKES_BLUR 50
#define SPIKES_BLUR_BIAS 0

vec4 spikes(vec4 vertexColor, vec2 centerUV, float rad) {
    vec4 color = vec4(1, 1, 1, 0);
    float angle = (atan(centerUV.y, centerUV.x) / PI / 2 + 0.5) * SPIKES_COUNT;
    float spikeTime = GameTime * SPIKES_SPEED + hash(int(angle)) % 100 * 63.1234;
    float s = (abs(fract(angle) - 0.5) * 20 / SPIKES_COUNT - 0.2) * length(centerUV) + (vertexColor.r*255/100.0) + (1 - vertexColor.a) * 0.05 + abs(fract(spikeTime) - 0.5) * 0.25;
    if (s < 0) {
        color = vec4(vec3(1), clamp(-s * SPIKES_BLUR + SPIKES_BLUR_BIAS, 0, 1));
    }

    return color;
}