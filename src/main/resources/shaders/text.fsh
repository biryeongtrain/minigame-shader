#version 330
#extension GL_ARB_separate_shader_objects : require

// minigame-lib (26.3): vanilla core/text.fsh + RGBMapUtils decode + ShaderFX effects + animated emoji.
// Generated at pack build time by ShaderFX RPHandler (imports and effect cases are inserted below).

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#endif

#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif

layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

layout(location = 4) flat in int effectId;
layout(location = 5) flat in int frames;
layout(location = 6) flat in int fps;
layout(location = 7) flat in float frameheight;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

//%IMPORTS%

#include <minecraft:minigamelib_rgbmap.glsl>

vec4 calculateFinalColor(vec4 color) {
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
#endif

#if !defined(IS_SEE_THROUGH) && !defined(IS_GUI)
#ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
#else
    vec4 fogColor = FogColor;
#endif
    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
    return color;
}

void main() {
#ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
#else
    vec4 texColor = texture(Sampler0, texCoord0);
#endif
    vec4 color = texColor * vertexColor * ColorModulator;

#ifndef IS_GRAYSCALE
    color = rgbmap_decode(color);
#endif

    vec2 centerUV = gl_FragCoord.xy / ScreenSize - 0.5;
    float ratio = ScreenSize.y / ScreenSize.x;

    if (effectId != 0) {
        switch (effectId) {
//%CASES%
        }
    }

    // animated emoji
    if (frames > 1) {
        int frameI = int(mod(floor(GameTime * 1000.0 * float(fps)), float(frames - 1)));
        float ty = texCoord0.y * 256.0;
        if (ty > frameheight)
            discard;
        color = texture(Sampler0, texCoord0 + vec2(0.0, frameheight / 256.0 * float(frameI + 1))) * vertexColor * ColorModulator;
    }

    if (color.a < 0.1) {
        discard;
    }

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    fragColor = calculateFinalColor(color);
#endif
}
