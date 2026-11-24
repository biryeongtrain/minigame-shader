#version 330
#extension GL_ARB_separate_shader_objects : require

// DisplayHud 2.2 Beta - above-hand display, last step (last pass of this chain, output minecraft:entity_outline).
// Keeps the copied HUD pixel (opaque) if there is one, otherwise the glow result (Glow). This buffer is drawn over the screen after the hand.
// To combine with another glow shader, feed its final result as Glow (see README).

uniform sampler2D GlowSampler;
uniform sampler2D HudSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 hud = texture(HudSampler, texCoord);
    fragColor = hud.a > 0.5 ? hud : texture(GlowSampler, texCoord);
}
