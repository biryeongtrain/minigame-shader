#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5) in vec2 UV3;
#endif
layout(location = 6) in vec3 Normal;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif
layout(location = 2) out vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
#endif

layout(location = 5) out vec2 texCoord0;
#ifdef GLINT
layout(location = 6) out vec2 texCoordGlint;
#endif

#include <minecraft:displayhud.glsl> // DisplayHud

void main() {
    // DisplayHud: vertices placed far below (y < -1000) become screen-fixed HUD.
    // Branchless (see displayhud.glsl): every output is written exactly once.
    bool hud = displayhud_is_hud(Position);
    vec4 hudPos = displayhud_clip(displayhud_hud_position(Position, Color, mat3(ModelViewMat), UV2));
#if !defined(OIT)
    hudPos.z = displayhud_above_hand_depth(hudPos.z, UV2); // above-hand marker (light block 1 / sky 2)
#endif
    gl_Position = mix(ProjMat * ModelViewMat * vec4(Position, 1.0), hudPos, bvec4(hud));

    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = mix(fog_spherical_distance(Position), 0.0, hud);
    cylindricalVertexDistance = mix(fog_cylindrical_distance(Position), 0.0, hud);
    #endif
    vertexColor = mix(minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color), displayhud_hud_color(Color, UV2), bvec4(hud));
    #ifndef OIT_ALPHA_ONLY
    lightMapColor = mix(sample_lightmap(Sampler2, UV2), vec4(1.0), bvec4(hud));
    overlayColor = texelFetch(Sampler1, UV1, 0);
    #endif

    texCoord0 = UV0;
    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    texCoordGlint = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif
    #endif

}
