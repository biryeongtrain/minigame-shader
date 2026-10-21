#ifndef MINECRAFT_PROJECTION_GLSL
#define MINECRAFT_PROJECTION_GLSL

// minigame-lib optional Dynamic FOV (CameraEffects.setFov), based on JNNGL's vanilla-shaders.
// The server sends a negative game time that encodes the requested FOV; GameTime >= 0 keeps vanilla projection.
// Opt-in only (config/minigame-lib/shaderfx.json: "dynamic-fov-shader": true) because it adds the Globals
// block to every shader that uses the projection matrix.

#include <minecraft:globals.glsl>

layout(std140) uniform Projection {
    mat4 MinigameLibProjMat;
};

mat4 minigamelib_projection() {
    mat4 projection = MinigameLibProjMat;
    if (GameTime >= 0.0 || projection[2][3] == 0.0) {
        return projection;
    }
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
