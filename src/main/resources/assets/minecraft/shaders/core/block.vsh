#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

#include <minecraft:displayhud.glsl> // DisplayHud

void main() {
    vec3 pos = Position + ModelOffset;
    // DisplayHud: vertices placed far below (y < -1000) become screen-fixed HUD.
    // Branchless (see displayhud.glsl): every output is written exactly once.
    bool hud = displayhud_is_hud(pos);
    vec4 hudPos = displayhud_clip(pos);
#if !defined(OIT)
    hudPos.z = displayhud_above_hand_depth(hudPos.z, UV2);
#endif
    gl_Position = mix(ProjMat * ModelViewMat * vec4(pos, 1.0), hudPos, bvec4(hud));

    sphericalVertexDistance = mix(fog_spherical_distance(pos), 0.0, hud);
    cylindricalVertexDistance = mix(fog_cylindrical_distance(pos), 0.0, hud);
    #ifndef OIT_ALPHA_ONLY
    vertexColor = mix(Color * sample_lightmap(Sampler2, UV2), Color, bvec4(hud));
    #else
    vertexColor = Color;
    #endif
    texCoord0 = UV0;

}
