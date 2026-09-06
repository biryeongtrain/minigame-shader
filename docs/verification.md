# Minigame Lib 검증 기록

2026-09-06, Java 21 · Fabric 1.21.8. 로컬 테스트 서버만 사용했습니다.

## 자동 검사

`rtk sh ./gradlew test runGameTest remapJar --console=plain --no-daemon` 통과.

- JUnit: 8개, 실패·건너뜀 0개. 보간·각도 경계·컷, JSON·참조·저장 충돌, 실제 Vorbis 길이·손상, Unicode 파일명·플레이리스트, 완료 응답·단조 시계.
- Fabric GameTest: 7개 통과(라이브러리 6개 + Fabric 기본 1개). 몸·게임 모드·피해·입력 보호, 교체·공유 티켓·외부 순간이동·게임 모드 변경, 원래 월드 엔티티와 FOV 복구, 그룹 검증·완료 핸들, 개인 패널·Dialog 소유권, 일시 정지한 전환의 투명도·가상 배우, 팩 빌드 실패·설정 단계 준비 상태·늦은 완료 응답·늦은 합류·음악 우선순위·접속 종료 정리.
- 팩 빌드 실패 검사는 비어 있지 않은 디렉터리를 ZIP 출력 대상으로 지정합니다. 해당 검사에서 출력되는 `DirectoryNotEmptyException`은 의도한 실패입니다.
- 게임 테스트는 Minecraft 패킷 처리기와 EmbeddedChannel을 사용합니다. 렌더링 이후의 표시·음원 출력까지 증명하지 않습니다.

전체 빌드 로그: [ui-full-gate.log](../build/ui-full-gate.log). JUnit 보고서: [HTML](../build/reports/tests/test/index.html).

## 실제 클라이언트

Copper Golem Fabric 서버와 1.21.8 vision client를 사용했습니다. vision mod는 관측 도구이며 시네마틱 회전을 구현하지 않습니다. 이미지와 영상은 실제 Minecraft framebuffer입니다.

- 몸을 오버월드 `(0.5, -60, 0.5)`, 크리에이티브 상태로 둔 채 네더 `(1000, 82, 1000)`, 엔드 `(600, 83, 600)`, 먼 오버월드 `(2000, -55, 2000)`를 관전했습니다. 재생 중과 종료 후 서버의 위치·차원·게임 모드가 유지됐습니다.
- yaw·pitch, `179 → -179` 경계, 컷·이동 보간을 연속 화면으로 확인했습니다. 팩에는 회전용 마커·후처리 셰이더가 없습니다.
- 씬 배우가 이동하고, `text_display` 투명도에 따른 전환 마스크가 열리는 것을 확인했습니다. 배우는 실제 월드 엔티티에 추가되지 않음을 GameTest에서도 검사했습니다.
- Camera 생성 → 40틱 키프레임 편집 → 카메라·씬 저장 → 실제 클라이언트 재접속 → 저장된 씬 재생을 수행했습니다. 입력은 QA 전용 명령이 native Interaction·Dialog 응답 패킷 처리기를 호출했습니다. 물리 마우스 조준 클릭 자동화는 수행하지 않았습니다.
- 두 관리자의 패널 격리와 다른 소유자의 Dialog 응답 거절은 GameTest, 저장 충돌은 JUnit으로 검사했습니다.
- DisplayHUD의 텍스트·아이템·블록, RGBMapUtils 이미지, ShaderFX 색상 덮개와 FOV 변경을 확인했습니다. 차원 관전 후 HUD·RGB 표시와 이전 FOV가 복원됐습니다.

[카메라 연속 영상](../build/verification/camera-tour-final.mp4) · [화면 전환 영상](../build/verification/transition.mp4) · [저장 후 재접속 재생](../build/verification/editor-preview.mp4)

[패널](../build/verification/editor-panels-final.png) · [타임라인](../build/verification/editor-timeline-final.png) · [Dialog](../build/verification/editor-dialog-final.png) · [HUD·FOV 복구](../build/verification/hud-fov-restored.png) · [RGB 복구](../build/verification/rgbmap-full-restored.png) · [ShaderFX](../build/verification/shaderfx-red.png)

서버 상태: [재생 중·종료 후 몸 상태](../build/verification/tour-final/checks.json), [클라이언트 차원 기록](../build/verification/tour-final/states.json).

## 레퍼런스 UI 반영

`lol.mp4`의 세로 메뉴, 검은 월드 패널, 회색 목록 행, 색상 버튼과 트랙 막대를 반영했습니다. 패널과 글자는 같은 평면에 고정하며, 각 항목의 PLAY / SHOW / DELETE와 조준 시 강조를 제공합니다. 목록과 씬 트랙의 페이지는 별도로 넘깁니다. 시간 눈금으로 커서를 옮기고 VIEW로 정지 시점을 확인하거나 PLAY로 재생합니다.

- 새 패널에서 Camera 생성 → 이름 입력 → SAVE → 편집기 종료·재진입 → 저장된 속성 확인을 수행했습니다. Camera / Entity / Scenes 전환, SHOW / HIDE, 목록·트랙의 다음 페이지, ◆ 클릭으로 60틱 키프레임 Dialog 열기도 확인했습니다.
- 10개 트랙이 있는 로컬 QA 씬에서 커서 이동과 VIEW를 수행했습니다. 준비 완료 시 `view=minecraft:overworld ready=true tickets=1`, 종료 후 `view=none tickets=0`을 확인했습니다. 입력은 QA 명령이 플레이어 시선을 버튼으로 맞춘 뒤 native 패킷 처리기를 호출했습니다. 물리 마우스 클릭 자동화와는 구분합니다.
- Java 21의 JUnit 8개·GameTest 7개·remapJar를 다시 통과했습니다. GameTest는 조준하지 않은 버튼 ID만 보낸 요청의 거절도 검사합니다.
- Minecraft 1.21.8, 오버월드, 적용된 Polymer 팩, 1인칭, HUD 숨김 상태의 framebuffer를 촬영했습니다. 목록은 `(0.5, -60, 0.5)` yaw/pitch `0/0`, 타임라인은 `(-3.5, -60, 0.5)` `16/3`, 전체 배치는 `(-1.25, -60, -0.5)` `0/3`입니다. 위의 이전 UI 이미지와는 촬영 위치가 다릅니다.

[전체 배치](../build/verification/ui-overview-final.png) · [Camera 목록](../build/verification/ui-camera-final.png) · [Entity 목록](../build/verification/ui-entity-final.png) · [씬 타임라인](../build/verification/ui-timeline-final.png) · [키프레임 입력](../build/verification/ui-keyframe-dialog-final.png)

[약 21초 화면 기록](../build/verification/ui-reference-redesign.mp4) · [28단계 조작·저장·복귀 기록](../build/verification/ui-flow.json) · [저장 후 다시 연 속성](../build/verification/ui-saved-dialog-final.png)

## 음악과 팩

- 한글·공백 OGG 두 곡을 사용했습니다. 추가·수정·삭제 후 실제 Polymer 팩 생성에서 곡 수 `2 → 3 → 3 → 2`, 수정된 음원의 바이트 해시 변경, 삭제 후 원래 팩 해시 복구를 확인했습니다.
- ZIP의 OGG·`sounds.json`의 `stream: true`, 공유 ShaderFX·HUD·FOV 셰이더 보존과 회전 전용 파일 부재를 확인했습니다. 기존 사운드 정의는 Polymer의 native sounds JSON 병합을 사용합니다.
- 실제 클라이언트의 첫 접속에서 `packReady=true`, `polymerMain=true`를 확인했습니다. 관전 중 팩 재적용 시 몸으로 복귀하고 티켓이 0개가 되며, 적용 완료 후 두 준비 상태가 다시 true가 되는 것을 기록했습니다. 재생·다음 곡·정지와 음악 우선순위는 실제 사운드 패킷을 검사한 GameTest로 확인했습니다.
- **실제 소리 녹음·청취 검증은 생략했습니다.** ScreenCaptureKit이 권한 오류 `-3801`을 반환했고 사용자가 실패하면 건너뛰도록 허용했습니다. 파일·패킷·화면 검사 결과를 청취 성공으로 간주하지 않았습니다.

[팩 추가·수정·삭제](../build/verification/music-pack-cycle.json) · [실제 팩 적용 상태](../build/verification/pack-client-status.json) · [ZIP 항목](../build/verification/pack-contents.txt)

## 산출물

[배포 JAR](../build/libs/minigame-lib-1.0.2+1.21.8.jar). 테스트 모드·QA 명령은 포함하지 않습니다. Maven 게시·운영 서버 배포는 수행하지 않았습니다. 설치·API·마이그레이션은 [README](../README.md)를 참고하세요.

JAR SHA-256: `da5af560dbf8f2f222f7c359e239400100d0a08303c55a6af4dbfdb3c62ffc39`.
