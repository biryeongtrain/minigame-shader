#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 texCoord0;

#include <minecraft:displayhud.glsl> // DisplayHud

void main() {
    // DisplayHud: vertices placed far below (y < -1000) become screen-fixed HUD (branchless, see displayhud.glsl).
    gl_Position = mix(ProjMat * ModelViewMat * vec4(Position, 1.0), displayhud_clip(Position), bvec4(displayhud_is_hud(Position)));

    vertexColor = Color;
    texCoord0 = UV0;
}
