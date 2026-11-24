#ifndef DISPLAYHUD_GLSL
#define DISPLAYHUD_GLSL

// DisplayHud (https://github.com/dorondo0000/DisplayHUD)
// Values must match the server (DisplayHudManager unit/screen/alignment gap).
// A display entity ridden by the player and translated far below (-1.5*GAP ...) has a
// camera-relative Position.y below -1000. Only those vertices use Position.xy as screen coords.
//   unit: 1px = 100 blocks (X, Y), reference 1920x1080, origin = screen center
//   alignment on y in GAP steps: -1.5 stretch, -2.5 left, -3.5 center, -4.5 right.
//         Aligned elements keep a 16:9 area and stick to their side.
//   depth: Position.z / 1e6 (server location.z * 100, larger z draws on top)
// Projection (ProjMat) must be included first; the aspect ratio comes from ProjMat.
// Globals (ScreenSize) is not used: vanilla entity/item/text/block/outline shaders do not
// declare that block and OpenGL drivers broke entity rendering when it was added.
//
// NVIDIA OpenGL (found 2026-11-22 with Limbotomy): writing a shader output inside a branch
// (if (hud) { vertexColor = ...; }) broke world geometry drawn by the same shader. Everything
// here is branchless: compute both values, pick with mix(a, b, bool) (an exact select, no
// arithmetic blend) and write each output exactly once. Shader sources are ASCII only, since
// strict drivers reject non-ASCII bytes even inside comments.

#define DISPLAYHUD_X 100.0
#define DISPLAYHUD_Y 100.0
#define DISPLAYHUD_GAP 1000000.0
#define DISPLAYHUD_DEPTH 1000000.0
#define DISPLAYHUD_REF vec2(1920.0, 1080.0)

bool displayhud_is_hud(vec3 position) {
    return position.y < -1000.0;
}

vec4 displayhud_clip(vec3 position) {
    bool aligned = position.y < -2.0 * DISPLAYHUD_GAP;
    bool center = position.y < -3.0 * DISPLAYHUD_GAP;
    bool right = position.y < -4.0 * DISPLAYHUD_GAP;
    float aspect = abs(ProjMat[1][1] / ProjMat[0][0]); // width / height ([0][0] = f/aspect, [1][1] = f)
    float keep = mix(1.0, (16.0 / 9.0) / aspect, aligned); // share of the width taken by the 16:9 area
    float shiftY = mix(0.0, mix(mix(1.0, 2.0, center), 3.0, right) * DISPLAYHUD_GAP, aligned);
    float offset = mix(0.0, mix(mix(keep - 1.0, 0.0, center), 1.0 - keep, right), aligned);
    vec3 pos = position + vec3(0.0, 1.5 * DISPLAYHUD_GAP + shiftY, 0.0);
    vec2 xy = vec2(-pos.x * keep, pos.y) / (DISPLAYHUD_REF * vec2(DISPLAYHUD_X, DISPLAYHUD_Y) / 2.0);
    // Reversed Z (closer = larger depth), kept inside 0..1 so both [0,1] and [-1,1] depth ranges work.
    return vec4(xy.x + offset, xy.y, 0.5 - pos.z * (0.5 / DISPLAYHUD_DEPTH), 1.0);
}

// Above-hand display (DisplayHud 2.2 Beta)
// Vanilla clears depth right before drawing the first-person hand, so world-entity HUDs are always
// covered by it. For setAboveHand(true) the server sends light block 1 / sky 2 (UV2 = (16, 32); the
// HUD does not use light). Those vertices go to a very near depth band no world object can reach
// (reversed Z 0.90 - 0.99), and the displayhud_capture pass in post_effect/entity_outline.json
// paints that band again after the hand. Normal HUD depth sits near 0.5 (5e-5 per location.z),
// so location.z -900 .. 900 keeps its order inside the band.
#define DISPLAYHUD_ABOVE_HAND_LIGHT ivec2(16, 32)

// Pointer (DisplayHud.setPointer, minigame-lib addition)
// An above-hand HUD with light block 2 / sky 2. Its tint RGB carries the yaw (12 bit) and pitch (12 bit)
// at the time the server computed its position. Every frame it moves by the difference to the real view
// rotation (ModelViewMat) * 15px per degree, so it follows the mouse between server updates.
#define DISPLAYHUD_POINTER_LIGHT ivec2(32, 32)
#define DISPLAYHUD_POINTER_PX 15.0

bool displayhud_is_pointer(ivec2 light) {
    return all(equal(light, DISPLAYHUD_POINTER_LIGHT));
}

vec3 displayhud_pointer(vec3 position, vec4 color, mat3 view) {
    ivec3 c = ivec3(round(color.rgb * 255.0));
    float refYaw = float((c.r << 4) | (c.g >> 4)) / 4096.0 * 360.0;
    float refPitch = float(((c.g & 15) << 8) | c.b) / 4095.0 * 180.0 - 90.0;
    vec3 f = -vec3(view[0][2], view[1][2], view[2][2]); // view direction in world space
    float yaw = degrees(atan(-f.x, f.z));
    float pitch = degrees(asin(clamp(-f.y, -1.0, 1.0)));
    float dYaw = mod(yaw - refYaw + 540.0, 360.0) - 180.0;
    vec2 d = clamp(vec2(dYaw, pitch - refPitch) * DISPLAYHUD_POINTER_PX, vec2(-240.0), vec2(240.0));
    return position - vec3(d.x * DISPLAYHUD_X, d.y * DISPLAYHUD_Y, 0.0);
}

// HUD vertex position: pointer vertices follow the live view rotation, others stay put.
vec3 displayhud_hud_position(vec3 position, vec4 color, mat3 view, ivec2 light) {
    return mix(position, displayhud_pointer(position, color, view), bvec3(displayhud_is_pointer(light)));
}

// HUD vertex color: the pointer's tint carries the rotation, so it is drawn white.
vec4 displayhud_hud_color(vec4 color, ivec2 light) {
    return mix(color, vec4(1.0, 1.0, 1.0, color.a), bvec4(displayhud_is_pointer(light)));
}

float displayhud_above_hand_depth(float z, ivec2 light) {
    bool above = all(equal(light, DISPLAYHUD_ABOVE_HAND_LIGHT)) || displayhud_is_pointer(light);
    return mix(z, 0.945 + clamp(z - 0.5, -0.045, 0.045), above);
}

#endif
