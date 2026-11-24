#version 330
#extension GL_ARB_separate_shader_objects : require

// minigame-lib (26.3): vanilla core/text.vsh + DisplayHud 2.2 + ShaderFX markers.

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
#endif

uniform sampler2D Sampler0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

// ShaderFX
layout(location = 4) flat out int effectId;
// animated emoji
layout(location = 5) flat out int frames;
layout(location = 6) flat out int fps;
layout(location = 7) flat out float frameheight;

#include <minecraft:displayhud.glsl> // DisplayHud

const vec2[4] corners = vec2[4](vec2(0), vec2(0, 1), vec2(1), vec2(1, 0));

void main() {
    // Branchless (see displayhud.glsl): NVIDIA OpenGL broke world geometry when outputs were written
    // inside if blocks. Every candidate value is computed, picked with mix(a, b, bool) or ?:, and each
    // output is written exactly once.
    vec4 worldPos = ProjMat * ModelViewMat * vec4(Position, 1.0);
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    vec4 litColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vec4 litColor = Color;
#endif

    // ShaderFX: glyph pixels with special alpha values mark full-screen / in-world effects.
    vec2 coord = corners[gl_VertexIndex % 4];
    vec4 col = round(textureLod(Sampler0, UV0, 0.0) * 255.0);
    bool screenFx = col.a == 251.0;               // screenspace
    bool worldFx = col.a == 253.0;                // normal/in-world effect
    bool emoji = col.a == 252.0 && Position.z == 0.0; // animated emoji
    vec4 screenPos = vec4((coord * 2.0 - 1.0) * vec2(1.0, -1.0), -1.0, 1.0);

    // DisplayHud: vertices placed far below (y < -1000) become screen-fixed HUD
#ifndef IS_GUI
    bool hud = displayhud_is_hud(Position);
    vec4 hudPos = displayhud_clip(Position);
#if !defined(IS_SEE_THROUGH) && !defined(OIT)
    hudPos.z = displayhud_above_hand_depth(hudPos.z, UV2); // above-hand marker (light block 1 / sky 2)
#endif
#else
    bool hud = false;
    vec4 hudPos = worldPos;
#endif

    gl_Position = mix(mix(worldPos, screenPos, bvec4(screenFx)), hudPos, bvec4(hud));
    vertexColor = mix(litColor, Color, bvec4(screenFx || worldFx || hud));
    texCoord0 = mix(UV0, UV0 - coord * 64.0 / 256.0, bvec2(screenFx));
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = mix(fog_spherical_distance(Position), 0.0, hud);
    cylindricalVertexDistance = mix(fog_cylindrical_distance(Position), 0.0, hud);
#endif
    effectId = (screenFx || worldFx) ? int(col.b) : 0;
    frames = emoji ? int(col.r) : 0;
    fps = emoji ? int(col.g) : 0;
    frameheight = emoji ? col.b : 0.0;
}
