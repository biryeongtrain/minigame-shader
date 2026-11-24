#version 330
#extension GL_ARB_separate_shader_objects : require

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include <minecraft:light.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler1;
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
layout(location = 2) out vec4 vertexPerFaceColorBack;
layout(location = 3) out vec4 vertexPerFaceColorFront;
#else
layout(location = 2) out vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) out vec4 overlayColor;
#endif

layout(location = 6) out vec2 texCoord0;
#ifdef GLINT
layout(location = 7) out vec2 texCoordGlint;
#endif

#include <minecraft:displayhud.glsl> // DisplayHud

void main() {
    // DisplayHud: vertices placed far below (y < -1000) become screen-fixed HUD.
    // Branchless (see displayhud.glsl): every output is written exactly once.
    bool hud = displayhud_is_hud(Position);
    vec4 hudPos = displayhud_clip(displayhud_hud_position(Position, Color, mat3(ModelViewMat), UV2));
#if !defined(EMISSIVE) && !defined(OIT)
    hudPos.z = displayhud_above_hand_depth(hudPos.z, UV2); // above-hand marker (light block 1 / sky 2)
#endif
    gl_Position = mix(ProjMat * ModelViewMat * vec4(Position, 1.0), hudPos, bvec4(hud));

    sphericalVertexDistance = mix(fog_spherical_distance(Position), 0.0, hud);
    cylindricalVertexDistance = mix(fog_cylindrical_distance(Position), 0.0, hud);

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, Normal);
    vertexPerFaceColorBack = mix(minecraft_mix_light_separate(-light, Color), displayhud_hud_color(Color, UV2), bvec4(hud));
    vertexPerFaceColorFront = mix(minecraft_mix_light_separate(light, Color), displayhud_hud_color(Color, UV2), bvec4(hud));
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = Color;
#else
    vertexColor = mix(minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color), displayhud_hud_color(Color, UV2), bvec4(hud));
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
    lightMapColor = mix(sample_lightmap(Sampler2, UV2), vec4(1.0), bvec4(hud));
#endif

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
    overlayColor = texelFetch(Sampler1, UV1, 0);
#endif

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#else
    texCoord0 = UV0;
#endif

#ifdef GLINT
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
