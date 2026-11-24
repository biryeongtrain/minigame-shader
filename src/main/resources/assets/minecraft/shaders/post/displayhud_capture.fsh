#version 330
#extension GL_ARB_separate_shader_objects : require

// DisplayHud 2.2 Beta - above-hand display, step 1 (first pass of this chain).
// This chain (entity_outline) runs inside level rendering before the first-person hand; its result (minecraft:entity_outline) is alpha-composited after the hand.
// It copies only the HUD pixels to lift above the hand (light marker -> dedicated depth band, or the legacy glow marker) from the main target before the hand is drawn.

uniform sampler2D InSampler;
uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Legacy glow marker: item/block displays glowing with 0x01FEFD (R=1, G=254, B=253).
// Must match server DisplayHud.ABOVE_HAND_GLOW_COLOR. The above-hand trigger display glows this color too (it is transparent, so it never shows).
bool displayhud_is_glow_mask(vec4 c) {
    vec3 rgb = c.rgb * 255.0;
    return c.a > 0.5 && abs(rgb.r - 1.0) < 0.6 && abs(rgb.g - 254.0) < 0.6 && abs(rgb.b - 253.0) < 0.6;
}

// Above-hand depth band (core shader displayhud_above_hand_depth): reversed Z 0.90 - 0.99.
// Normal HUD sits near 0.5; world geometry passes 0.85 only within 0.06 blocks of the camera.
// If the depth range changes from [-1,1] to [0,1] (0.95 - 0.995) it still stays above 0.85.
bool displayhud_is_above_hand_depth(float depth) {
    return depth > 0.85;
}

void main() {
    if (displayhud_is_above_hand_depth(texture(MainDepthSampler, texCoord).r) || displayhud_is_glow_mask(texture(InSampler, texCoord))) {
        fragColor = vec4(texture(MainSampler, texCoord).rgb, 1.0);
    } else {
        fragColor = vec4(0.0);
    }
}
