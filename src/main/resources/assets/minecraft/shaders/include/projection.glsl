#version 150

#ifndef DANTASHADER_PROJECTION_GLSL
#define DANTASHADER_PROJECTION_GLSL

#moj_import <minecraft:globals.glsl>

layout(std140) uniform Projection {
    mat4 DantaShaderProjectionMat;
};

mat4 minigamelib_projection() {
    mat4 projection = DantaShaderProjectionMat;
    if (GameTime >= 0.0 || projection[2][3] == 0.0) {
        return projection;
    }

#ifdef DANTASHADER_HAS_FOG
    if (FogEnvironmentalStart > 3e38 && FogRenderDistanceStart > 3e38) {
        return projection;
    }
#endif

    float fov = floor(-GameTime * 180.0);
    if (fov < 1.0 || fov >= 180.0) {
        return projection;
    }

    float invTanHalfFov = 1.0 / tan(radians(fov * 0.5));
    float inverseAspect = projection[0][0] / projection[1][1];
    projection[0][0] = invTanHalfFov * inverseAspect;
    projection[1][1] = invTanHalfFov;
    return projection;
}

vec4 projection_from_position(vec4 position) {
    vec4 projection = position * 0.5;
    projection.xy = vec2(projection.x + projection.w, projection.y + projection.w);
    projection.zw = position.zw;
    return projection;
}

#define ProjMat minigamelib_projection()

#endif
