#ifndef DISPLAYHUD_GLSL
#define DISPLAYHUD_GLSL

// DisplayHud (https://github.com/dorondo0000/DisplayHUD)
// 서버(DisplayHudManager)의 unit/screen/alignment-gap 설정과 값이 같아야 한다.
// 디스플레이 엔티티를 플레이어에게 태우고 변환(translation)을 아주 아래(-1.5*GAP ...)로 내리면
// 카메라 기준 좌표 Position.y 가 -1000 보다 작아진다. 그런 꼭짓점만 Position.xy 를 그대로 화면 좌표로 쓴다.
//   unit: 1px = 100 블록 (X, Y), 기준 해상도 1920x1080, 원점 화면 중앙
//   정렬: y 에 GAP 단위로 표시 (-1.5GAP 없음/stretch, -2.5GAP 왼쪽, -3.5GAP 가운데, -4.5GAP 오른쪽)
//         정렬이 있으면 16:9 영역을 유지하고 남는 가로는 정렬 쪽으로 붙인다.
//   깊이: Position.z / 1e6 (서버 location.z * 100 → 위에 그릴수록 z 를 크게)
// Projection(ProjMat) 이 먼저 포함돼 있어야 한다. 화면 비율은 ProjMat 에서 구한다.
// Globals(ScreenSize) 는 쓰지 않는다: 바닐라 코어 셰이더(entity/item/text/block/outline)가 쓰지 않는 블록이라
// OpenGL 에서 엔티티 렌더링이 깨진다(1인칭 팔 찌그러짐, 멀리 있는 스킨 검정). Vulkan 은 멀쩡해도 넣지 않는다.

#define DISPLAYHUD_X 100.0
#define DISPLAYHUD_Y 100.0
#define DISPLAYHUD_GAP 1000000.0
#define DISPLAYHUD_DEPTH 1000000.0
#define DISPLAYHUD_REF vec2(1920.0, 1080.0)

// Screen-space layout is independent of world zoom. Do not add a uniform block.
float displayhud_screen_aspect() {
#ifdef MINIGAMELIB_RAW_ASPECT
    return minigamelib_raw_aspect();
#else
    return abs(ProjMat[1][1] / ProjMat[0][0]);
#endif
}

bool displayhud_is_hud(vec3 position) {
    return position.y < -1000.0;
}

vec4 displayhud_clip(vec3 position) {
    vec3 pos = position + vec3(0.0, 1.5 * DISPLAYHUD_GAP, 0.0);
    pos.x *= -1.0;
    float offset = 0.0;
    if (position.y < -2.0 * DISPLAYHUD_GAP) {
        float aspect = displayhud_screen_aspect(); // 화면 가로/세로 (원근 투영: [0][0] = f/aspect, [1][1] = f)
        float keep = (16.0 / 9.0) / aspect;                // 16:9 영역이 차지하는 가로 비율
        if (position.y < -4.0 * DISPLAYHUD_GAP) {        // 오른쪽
            pos.y += 2.0 * DISPLAYHUD_GAP;
            offset = 1.0 - keep;
        } else if (position.y < -3.0 * DISPLAYHUD_GAP) { // 가운데
            pos.y += 1.0 * DISPLAYHUD_GAP;
        } else {                                         // 왼쪽
            offset = -1.0 + keep;
        }
        pos.y += 1.0 * DISPLAYHUD_GAP;
        pos.x *= keep;
    }
    pos.xy /= DISPLAYHUD_REF * vec2(DISPLAYHUD_X, DISPLAYHUD_Y) / 2.0;
    pos.x += offset;
    // 역Z(가까울수록 깊이 큼). 0..1 안에 두면 깊이 범위가 [0,1] 이든 [-1,1] 이든 그대로 쓸 수 있다.
    pos.z = 0.5 - pos.z * (0.5 / DISPLAYHUD_DEPTH);
    return vec4(pos, 1.0);
}

// ── 손 위 표시 (DisplayHud 2.2 Beta) ──
// 바닐라는 1인칭 손을 그리기 직전에 깊이를 지워서, 월드 엔티티인 HUD 는 항상 손에 가려진다.
// setAboveHand(true) 인 HUD 는 서버가 밝기를 block 1 / sky 2 로 보낸다(UV2 = (16, 32), HUD 는 밝기를 쓰지 않는다).
// 그 꼭짓점은 아무 월드 물체도 올 수 없는 아주 가까운 깊이 구간(역Z: 0.90 ~ 0.99)에 순서를 지켜 그리고,
// post_effect/entity_outline.json 의 displayhud_capture 단계가 이 구간의 픽셀을 손을 그린 뒤 다시 덮는다.
// 보통 HUD 깊이는 0.5 근처(location.z 1 당 5e-5). location.z -900 ~ 900 이 구간 안에서 순서가 지켜진다.
#define DISPLAYHUD_ABOVE_HAND_LIGHT ivec2(16, 32)

// ── 포인터 (DisplayHud.setPointer) ──
// 밝기 block 2 / sky 2 인 손 위 HUD. 틴트 RGB 에 서버 위치가 계산된 시점의 yaw(12bit)·pitch(12bit) 가 들어 있다.
// 매 프레임 실제 시선 회전(ModelViewMat)과의 차이 * 15px/도 만큼 옮겨, 서버 갱신 사이에도 마우스를 바로 따라간다.
#define DISPLAYHUD_POINTER_LIGHT ivec2(32, 32)
#define DISPLAYHUD_POINTER_PX 15.0

bool displayhud_is_pointer(ivec2 light) {
    return light == DISPLAYHUD_POINTER_LIGHT;
}

vec3 displayhud_pointer(vec3 position, vec4 color, mat3 view) {
    ivec3 c = ivec3(round(color.rgb * 255.0));
    float refYaw = float((c.r << 4) | (c.g >> 4)) / 4096.0 * 360.0;
    float refPitch = float(((c.g & 15) << 8) | c.b) / 4095.0 * 180.0 - 90.0;
    vec3 f = -vec3(view[0][2], view[1][2], view[2][2]);           // 월드 기준 시선 방향
    float yaw = degrees(atan(-f.x, f.z));
    float pitch = degrees(asin(clamp(-f.y, -1.0, 1.0)));
    float dYaw = mod(yaw - refYaw + 540.0, 360.0) - 180.0;
    vec2 d = clamp(vec2(dYaw, pitch - refPitch) * DISPLAYHUD_POINTER_PX, vec2(-240.0), vec2(240.0));
    return position - vec3(d.x * DISPLAYHUD_X, d.y * DISPLAYHUD_Y, 0.0);
}

float displayhud_above_hand_depth(float z, ivec2 light) {
    if (displayhud_is_pointer(light)) light = DISPLAYHUD_ABOVE_HAND_LIGHT;
    if (light != DISPLAYHUD_ABOVE_HAND_LIGHT) {
        return z;
    }
    return 0.945 + clamp(z - 0.5, -0.045, 0.045);
}

#endif
