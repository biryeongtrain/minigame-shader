#version 330
#extension GL_ARB_separate_shader_objects : require

// DisplayHud 2.2 Beta - same as vanilla entity_sobel, but pixels with the glow marker (0x01FEFD) count as empty
// (so no glow border appears around legacy glow-marker HUDs and the trigger). Other glow colors behave exactly like vanilla.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Legacy glow marker: item/block displays glowing with 0x01FEFD (R=1, G=254, B=253).
// Must match server DisplayHud.ABOVE_HAND_GLOW_COLOR. The above-hand trigger display glows this color too (it is transparent, so it never shows).
bool displayhud_is_glow_mask(vec4 c) {
    vec3 rgb = c.rgb * 255.0;
    return c.a > 0.5 && abs(rgb.r - 1.0) < 0.6 && abs(rgb.g - 254.0) < 0.6 && abs(rgb.b - 253.0) < 0.6;
}

vec4 displayhud_glow_at(vec2 uv) {
    vec4 c = texture(InSampler, uv);
    return displayhud_is_glow_mask(c) ? vec4(0.0) : c;
}

void main(){
    vec2 oneTexel = 1.0 / InSize;

    vec4 center = displayhud_glow_at(texCoord);
    vec4 left = displayhud_glow_at(texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = displayhud_glow_at(texCoord + vec2(oneTexel.x, 0.0));
    vec4 up = displayhud_glow_at(texCoord - vec2(0.0, oneTexel.y));
    vec4 down = displayhud_glow_at(texCoord + vec2(0.0, oneTexel.y));
    float leftDiff  = abs(center.a - left.a);
    float rightDiff = abs(center.a - right.a);
    float upDiff    = abs(center.a - up.a);
    float downDiff  = abs(center.a - down.a);
    float total = clamp(leftDiff + rightDiff + upDiff + downDiff, 0.0, 1.0);
    vec3 outColor = center.rgb * center.a + left.rgb * left.a + right.rgb * right.a + up.rgb * up.a + down.rgb * down.a;
    fragColor = vec4(outColor * 0.2, total);
}
