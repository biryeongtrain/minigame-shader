#version 150

#ifndef DANTASHADER_GLOBALS_GLSL
#define DANTASHADER_GLOBALS_GLSL

layout(std140) uniform Globals {
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
};

#endif
