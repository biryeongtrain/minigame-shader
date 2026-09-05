# Minigame Lib

Fabric 1.21.8·Java 21 서버 라이브러리입니다. 시네마틱, 공간 편집기, OGG 음악과 기존 DisplayHUD·RGBMapUtils·ShaderFX·FOV를 제공합니다. 플레이어에게는 전용 클라이언트 모드 없이 Polymer 리소스팩을 전송합니다.

## DisplayHud

출처 : https://github.com/dorondo0000/DisplayHUD

![2026-01-12+05-23-25](https://github.com/user-attachments/assets/3545fff5-8a65-4b7d-9079-33f1885856c5)

## RGBMapUtils

출처 : https://github.com/JNNGL/vanilla-shaders
https://github.com/biryeongtrain/RGBMapUtils

![showcase_1](img/2026-03-04_22.52.28.png)
![showcase_2](img/2026-03-06_00.15.00.png)


## ShaderFX

[![showcase_3](https://www.youtube.com/watch?v=3GpW_6qgs80)](https://www.youtube.com/watch?v=3GpW_6qgs80)

## 설치와 이전

Fabric API와 Minecraft 1.21.8용 Polymer 0.13.13을 서버에 설치하고 `minigame-lib-1.0.2+1.21.8.jar`를 추가합니다. Maven 좌표는 `kim.biryeong:minigame-lib:1.0.2+1.21.8`입니다. 이 작업에서는 Maven 저장소에 게시하지 않습니다.

기존 Java import의 `kim.biryeong.dantashader`를 `kim.biryeong.minigamelib`로, 리소스 ID의 `dantashader:`를 `minigamelib:`로 변경하세요. 모드 ID는 `minigamelib`입니다. 기존 `shaderfx.json`은 새 `config/minigame-lib/shaderfx.json`이 없을 때 가져옵니다. 원본은 남겨 둡니다.

회전 전용 마커·후처리 셰이더와 `CameraEffects.setCameraRotation/resetCameraRotation` API는 제거했습니다. 시네마틱은 빈 `item_display`의 위치·yaw·pitch를 사용합니다. Fabulous 설정과 회전 셰이더가 필요하지 않습니다. roll은 지원하지 않습니다.

## 시네마틱과 편집

```text
/minigamelib cinematic editor
/minigamelib cinematic list
/minigamelib cinematic play <sceneId> [players]
/minigamelib cinematic stop [players]
/minigamelib cinematic view <dimension> <x> <y> <z>
```

명령과 편집은 권한 레벨 2 이상이 필요합니다. 대상 생략 시 명령 실행자에게 적용됩니다. `view`는 현재 시선으로 한 장소를 관전하는 진단 명령입니다.

공간 패널에서 Camera를 만들고 속성과 키프레임을 편집한 뒤 저장하세요. 첫 키프레임의 시간은 0입니다. 시간 눈금으로 커서를 옮기고 **현재 시점 캡처**를 누르면 편집자의 위치·시선을 가져옵니다. **키프레임 편집**의 `position`에는 `x y z`, `rotation`에는 `yaw pitch`를 입력합니다. 보간은 `LINEAR`, `SMOOTH`, `CUT`입니다. 카메라의 선택적 FOV 범위는 `1 <= fov < 180`입니다.

Scenes에서 저장된 카메라를 연결하고 트랙 시작 시간과 씬 길이를 정합니다. Entity는 바닐라 몹과 item/block/text display를 지원합니다. 장비는 `{"mainhand":"minecraft:diamond_sword"}`처럼 입력합니다. 포즈는 바닐라 `Pose` 이름이며, 애니메이션은 `swing_main_hand`, `swing_off_hand`, `hurt`입니다. 연출용 엔티티를 실제 월드에 추가하거나 기존 엔티티를 변경하지 않습니다.

씬의 음악·화면 큐는 `MUSIC`, `STOP_MUSIC`, `TRANSITION`, `EFFECT`입니다. 음악 ID는 음악 목록의 곡 ID 또는 플레이리스트 이름을 사용합니다. 효과 ID 예시는 `minigamelib:fade`, `minigamelib:transition-hbars`입니다. 모든 시간은 서버 틱입니다. `TRANSITION`은 지정된 마스크가 `duration` 동안 화면을 드러내고, `EFFECT`는 해당 기간에 효과를 표시합니다. 전환 진행률은 개인 `text_display`의 투명도로 전달하므로 일시 정지·시간 이동에도 같은 상태를 표시합니다.

미리보기와 실제 재생은 같은 엔진을 사용합니다. 시간 눈금 선택은 음악을 멈추고 해당 시점의 시각 상태를 표시합니다. 미리보기 종료는 `/minigamelib cinematic stop`입니다. 다시 재생하면 선택한 위치에서 시작하며, 이전에 활성화된 음악 큐는 곡 처음부터 재생합니다.

편집 내용은 초안입니다. 저장은 JSON 파일 하나를 원자적으로 교체하며, 읽은 이후 다른 관리자가 저장했다면 충돌을 알립니다. 새 트랙을 먼저 저장한 뒤 이를 참조하는 씬을 저장하세요. 경로는 다음과 같습니다.

```text
config/minigame-lib/cinematics/cameras/<id>.json
config/minigame-lib/cinematics/entities/<id>.json
config/minigame-lib/cinematics/scenes/<id>.json
```

정의의 `version`은 1입니다. Java와 JSON의 필드 및 검증은 `CinematicData`의 record를 공유합니다. 플레이어의 실제 위치·차원·게임 모드는 유지하며, 종료 시 현재 서버 상태와 이전 FOV를 다시 전송합니다. 서로 다른 차원의 카메라는 컷으로 전환합니다. 초기 청크 준비 제한은 15초입니다.

## 음악

`config/minigame-lib/music` 아래에 OGG Vorbis 파일을 넣고 Polymer 리소스팩을 다시 생성하세요. 하위 폴더와 한글·공백 이름을 지원합니다. 길이는 Vorbis 샘플 수에서 읽으며 서버에 FFmpeg가 필요하지 않습니다. 손상 파일, Vorbis가 아닌 코덱, 다중·연결 Ogg 스트림은 경로와 이유를 기록하고 제외합니다.

```text
/minigamelib music list
/minigamelib music play <trackId|playlist> [players]
/minigamelib music stop [players]
/minigamelib music next [players]
/minigamelib music global play <trackId|playlist>
/minigamelib music global stop
/minigamelib music global next
```

팩 생성 성공 후에만 목록이 바뀝니다. 전송마다 별도 UUID를 사용해 이전 팩의 늦은 완료 응답이 새 팩을 승인하지 못하게 하고, 완료 응답은 Polymer의 기본 팩 상태에도 반영합니다. 클라이언트가 전송된 팩 UUID의 `SUCCESSFULLY_LOADED`를 응답하고 해시가 활성 팩과 일치해야 음악을 재생합니다. `all`은 상대 경로순의 전체 곡을 반복하는 자동 플레이리스트입니다. 사용자 목록은 `music/playlists.json`에 작성합니다.

```json
{
  "lobby": {
    "tracks": ["로비/첫 번째 곡.ogg", "로비/두 번째 곡.ogg"],
    "repeat": true
  }
}
```

`config/minigame-lib/music.json`의 `autoStart` 기본값은 `false`입니다. `true`이면 서버 시작 후 팩을 적용한 청취자가 준비되었을 때 `all`을 전역 재생합니다. 변경은 서버를 다시 시작하면 반영됩니다.

곡 길이는 단조 시계로 계산하며 별도 음악 스레드가 다음 곡의 사운드 패킷을 전송합니다. 늦게 접속하거나 팩을 다시 적용하거나 클라이언트 차원이 바뀐 청취자는 다음 곡부터 합류합니다. 오디오 중간 탐색과 클라이언트 간 샘플 단위 동기화는 지원하지 않습니다. 네트워크와 클라이언트의 디코딩 지연은 발생할 수 있습니다.

시네마틱 음악이 전역 BGM보다 우선합니다. 종료 후 전역 BGM의 다음 곡에 합류합니다. 라이브러리가 시작한 사운드 ID만 정지하므로 다른 플러그인의 효과음은 정지하지 않습니다. 플레이어 집합으로 시작한 음악은 하나의 재생 핸들을 공유하며, `next`는 해당 재생의 다음 곡으로 이동합니다.

## Java API

```java
import kim.biryeong.minigamelib.MinigameLib;
import kim.biryeong.minigamelib.Playback;

var lib = MinigameLib.get(server);
Playback movie = lib.cinematics().play("intro", players);
movie.completion().thenAccept(result -> server.execute(() -> {
    // COMPLETED / CANCELLED / REPLACED / FAILED
    logger.info("Cinematic: {} {}", result.status(), result.detail());
}));
// movie.stop();

Playback playlist = lib.music().play("lobby", players);
Playback global = lib.music().playGlobal("all");
// playlist.stop();
```

시네마틱과 편집 API는 서버 스레드에서 호출합니다. 음악 완료 콜백은 음악 스레드에서도 실행되므로 월드 변경은 `server.execute`로 전달하세요. `CinematicCatalog`와 `CinematicData.ResolvedScene`으로 파일 없이도 동일한 정의를 검증·재생할 수 있습니다.

## 기존 기능

```java
import kim.biryeong.minigamelib.camera.api.CameraEffects;
CameraEffects.setFov(player, 30.0f);
CameraEffects.resetFov(player);
```

관리자 명령은 `/minigamelib fov set <player> <degrees>`, `/minigamelib fov reset <player>`입니다. ShaderFX는 `/shaderfx run`을 유지합니다. 개발 환경에서는 `/displayhudtest`와 `/rgbmapdebug`를 사용할 수 있습니다.

Dynamic FOV는 음수 GameTime 태그로 전달하고 현재 클라이언트가 관전하는 차원의 시간을 함께 동기화합니다. FOV를 초기화하면 정상 월드 시간을 전송합니다. FOV가 활성화된 동안 GameTime 기반 셰이더 애니메이션의 위상에 영향을 줄 수 있습니다. JNNGL의 Dynamic FOV 구현을 바탕으로 했으며 저작권 고지는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)에 포함합니다.

## 빌드와 검증

Java 21에서 실행합니다.

```sh
rtk sh ./gradlew test runGameTest remapJar --console=plain --no-daemon
```

JUnit은 파싱·보간·파일 충돌·음악 길이·재생 순서·팩 적용 상태를 검사합니다. Fabric GameTest는 몸과 입력 보호, 교체·복구, 패널 격리·Dialog 응답, 팩 빌드 실패, 음악 합류·우선순위를 검사합니다. 1.21.8 Fabric의 SNBT 구조물을 `src/gametest/resources/data/minigamelib-test/gametest/structure`에 둡니다. 기존 testing 스킬의 레이아웃 검사기는 이 별도 소스셋과 SNBT 경로를 인식하지 못합니다.

로컬 Copper Golem 서버 JAR 경로를 지정하면 테스트 서버와 QA 전용 명령을 사용할 수 있습니다. 테스트 모드는 배포 JAR에 포함되지 않습니다.

```sh
rtk sh ./gradlew runSmokeServer -PminigameSmoke \
  -PsmokeHarness=/absolute/path/coppergolem-fabric-server-1.21.8-1.0.0-SNAPSHOT.jar
```

런타임 디렉터리는 `build/minigamelib-smoke`입니다. `scripts/copper_mcp.py`는 서버의 로컬 SSE 도구를 호출합니다. 화면은 Copper Golem 1.21.8 vision client의 실제 framebuffer로 확인합니다. 자동 테스트 성공과 실제 화면·소리 검증 결과는 구분해서 기록합니다.

실행 결과와 실제 화면·영상, 생략한 검증은 [검증 기록](docs/verification.md)에 정리했습니다.
