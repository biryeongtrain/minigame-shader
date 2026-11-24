# R2 Maven 저장소 게시 가이드

Gradle 프로젝트의 라이브러리를 Cloudflare R2에 있는 Maven 저장소에 게시하는 방법입니다. 이 문서는 R2 키와 함께 전달합니다. 키는 이 문서나 저장소에 넣지 말고 별도 채널로 받으세요.

확인일: 2026-11-22

## 저장소 정보

| 항목 | 값 |
|---|---|
| 저장 위치 | R2 버킷 `maven`, 경로 `releases/` (rclone 표기 `r2:maven/releases`) |
| 공개 주소(읽기 전용) | `https://repo.biryeong.kim/releases/` (Cloudflare 커스텀 도메인) |
| 업로드 도구 | `rclone` (S3 호환, remote 이름 `r2`) |
| 게시 명령 | `./gradlew publishToR2` |

파일은 표준 Maven 레이아웃으로 저장됩니다. `group=com.example`, artifactId `my-lib`, 버전 `1.0.0`이면 `releases/com/example/my-lib/1.0.0/`에 들어가고 공개 URL은 `https://repo.biryeong.kim/releases/com/example/my-lib/1.0.0/my-lib-1.0.0.jar`입니다. 버킷에는 다른 프로젝트의 아티팩트도 함께 있습니다.

## 동작 방식

공개 주소는 정적 파일만 내려주므로 Gradle이 HTTP로 직접 올릴 수 없습니다. 그래서 두 단계로 나눕니다.

1. Gradle `maven-publish`가 `build/staging-repo/`(로컬 파일 Maven 저장소)에 게시합니다.
2. `rclone copy`로 그 폴더를 R2에 복사합니다.

`publishToR2` 한 번이 아래를 차례로 실행합니다.

1. `build` (테스트 포함, `-PskipBuild=true`로 생략 가능)
2. `r2Stage`: staging 폴더를 비우고 R2에 있는 기존 `maven-metadata.xml*`을 내려받습니다. 그래야 새 버전이 기존 버전 목록에 병합됩니다.
3. `publishAllPublicationsToStagingRepository`: jar, pom, Gradle module, 체크섬을 staging에 씁니다.
4. 같은 버전이 R2에 이미 있으면 중단합니다. 게시된 버전은 바꾸지 않습니다.
5. `rclone copy`로 업로드합니다. `copy`이므로 버킷의 다른 파일은 지워지지 않습니다.
6. 공개 URL에서 `.pom`과 `.jar`에 HEAD 요청을 보내 200인지 확인합니다. 버전의 `+`는 `%2B`로 인코딩해 확인합니다.

## 처음 한 번: rclone 설정

필요한 것: rclone 1.6x 이상(`brew install rclone`), 프로젝트를 빌드할 JDK.

전달받는 값은 Access Key ID, Secret Access Key, Cloudflare 계정 ID(엔드포인트)입니다. remote 이름은 `r2`로 합니다. 다른 이름을 쓰면 매번 `-Pr2Remote=<이름>`을 붙여야 합니다.

```sh
rclone config create r2 s3 \
  provider=Cloudflare \
  access_key_id=<ACCESS_KEY_ID> \
  secret_access_key=<SECRET_ACCESS_KEY> \
  endpoint=https://<ACCOUNT_ID>.r2.cloudflarestorage.com \
  acl=private
```

셸 기록에 키를 남기기 싫으면 인자 없이 `rclone config`를 실행해 대화형으로 입력합니다. 설정 파일 위치는 `rclone config file`로 확인합니다. 이 파일은 공유하거나 저장소에 넣지 마세요.

파일 대신 환경 변수만 써도 됩니다(CI에 적합).

```sh
export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID=...
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=...
export RCLONE_CONFIG_R2_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
```

스크립트가 `--s3-no-check-bucket`을 붙이므로 키에 버킷 생성·조회 권한은 필요 없습니다. `maven` 버킷의 Object Read & Write 권한이면 됩니다.

연결 확인:

```sh
rclone lsf r2:maven/releases/ --s3-no-check-bucket
# 기존 그룹 폴더가 보이면 정상
```

## 프로젝트에 붙이기

### 1. 게시 스크립트 추가

아래 내용을 프로젝트의 `gradle/publish-r2.gradle`로 저장합니다.

```groovy
// 원클릭 Maven 게시: ./gradlew publishToR2
//   build(테스트 포함) → 로컬 staging 저장소에 게시 → rclone으로 R2 업로드 → 공개 URL 검증
// 이미 R2에 있는 버전은 덮어쓰지 않고 실패한다(버전은 불변). 새 버전 번호를 쓰거나 -PforceUpload 로 명시적으로 덮어쓴다.
//
// 옵션(-P):  r2Remote=r2  r2Bucket=maven  r2Path=releases  publicBase=https://repo.biryeong.kim
//           r2ArtifactId=<id>  artifactId 가 archives_base_name·project.name 과 다를 때
//           dryRun=true      업로드 없이 staging 까지만 수행하고 올라갈 파일을 출력
//           skipBuild=true   build(테스트) 생략. 이미 검증된 경우에만
//           forceUpload=true 이미 존재하는 버전을 덮어씀(권장하지 않음)
// 전제: rclone 에 R2 remote 가 설정되어 있어야 한다. 키는 rclone 설정에만 두고 이 저장소에 넣지 않는다.

def r2 = { String key, String fallback -> project.findProperty(key)?.toString() ?: fallback }
def r2Remote = r2('r2Remote', 'r2')
def r2Bucket = r2('r2Bucket', 'maven')
def r2Path = r2('r2Path', 'releases')
def publicBase = r2('publicBase', 'https://repo.biryeong.kim')
def flag = { String key -> project.findProperty(key)?.toString()?.equalsIgnoreCase('true') }

def stagingDir = layout.buildDirectory.dir('staging-repo')
def remoteRoot = "${r2Remote}:${r2Bucket}/${r2Path}".toString()

def run = { List<String> cmd, boolean allowFail = false ->
    def p = new ProcessBuilder(cmd.collect { it.toString() }).redirectErrorStream(true).start()
    def out = p.inputStream.getText('UTF-8')
    int code = p.waitFor()
    if (code != 0 && !allowFail) throw new GradleException("명령 실패(${code}): ${cmd.join(' ')}\n${out}")
    return [code: code, out: out]
}

tasks.register('r2Stage') {
    group = 'publishing'
    description = '로컬 staging 저장소를 비우고, R2의 maven-metadata 를 받아 온 뒤 새 버전을 게시한다.'
    if (!flag('skipBuild')) dependsOn 'build'
    doLast {
        def dir = stagingDir.get().asFile
        project.delete(dir)
        dir.mkdirs()
        if (run(['rclone', 'version'], true).code != 0) throw new GradleException('rclone 이 설치되어 있지 않습니다 (brew install rclone).')
        // 기존 메타데이터를 먼저 가져와 게시가 버전 목록을 덮어쓰지 않고 병합하게 한다.
        def pull = run(['rclone', 'copy', remoteRoot, dir.absolutePath, '--include', 'maven-metadata.xml*', '--s3-no-check-bucket'], true)
        if (pull.code != 0) throw new GradleException("R2 접근 실패 (${remoteRoot}). rclone 설정을 확인하세요.\n${pull.out}")
    }
}
tasks.matching { it.name == 'publishAllPublicationsToStagingRepository' }.configureEach {
    mustRunAfter 'r2Stage'
}

tasks.register('publishToR2') {
    group = 'publishing'
    description = "빌드 후 Maven 아티팩트를 ${remoteRoot} 에 업로드하고 ${publicBase}/${r2Path}/ 에서 확인한다."
    dependsOn 'r2Stage', 'publishAllPublicationsToStagingRepository'
    doLast {
        def dir = stagingDir.get().asFile
        def group = project.group.toString().replace('.', '/')
        def artifact = (project.findProperty('r2ArtifactId') ?: project.findProperty('archives_base_name') ?: project.name).toString()
        def version = project.version.toString()
        def versionPath = "${group}/${artifact}/${version}".toString()
        def local = new File(dir, versionPath)
        if (!local.isDirectory() || local.list().length == 0) throw new GradleException("게시 결과가 없습니다: ${local}")

        def existing = run(['rclone', 'lsf', "${remoteRoot}/${versionPath}", '--s3-no-check-bucket'], true)
        boolean published = existing.code == 0 && existing.out.trim()
        if (published && !flag('forceUpload')) {
            def msg = "이미 게시된 버전입니다: ${artifact}:${version}. 새 버전 번호로 올리세요 (gradle.properties 의 mod_version)."
            if (!flag('dryRun')) throw new GradleException(msg)
            println "경고: ${msg}"
        }

        println "업로드 대상 (${local.list().length}개): ${versionPath}"
        local.list().sort().each { println "  ${it}" }
        if (flag('dryRun')) { println "dryRun: 업로드하지 않았습니다. staging = ${dir}"; return }

        // copy 는 R2 의 다른 파일을 지우지 않는다.
        run(['rclone', 'copy', dir.absolutePath, remoteRoot, '--s3-no-check-bucket'])

        // 공개 URL 검증: 인코딩된 + 를 포함해 실제로 내려받아 본다.
        def base = "${publicBase}/${r2Path}/${versionPath}".replace('+', '%2B')
        def client = java.net.http.HttpClient.newHttpClient()
        def failures = []
        ["${artifact}-${version}.pom", "${artifact}-${version}.jar"].each { name ->
            def url = "${base}/${name.replace('+', '%2B')}"
            def res = client.send(java.net.http.HttpRequest.newBuilder(URI.create(url)).method('HEAD',
                    java.net.http.HttpRequest.BodyPublishers.noBody()).build(), java.net.http.HttpResponse.BodyHandlers.discarding())
            println "  ${res.statusCode()} ${url}"
            if (res.statusCode() != 200) failures << url
        }
        if (failures) throw new GradleException("업로드는 했지만 공개 URL 확인에 실패했습니다:\n${failures.join('\n')}")
        println "게시 완료: ${project.group}:${artifact}:${version}"
    }
}
```

### 2. `build.gradle` 설정

```groovy
plugins {
    id 'java'            // 또는 java-library, 사용하는 플러그인
    id 'maven-publish'
}

group = 'com.example'    // Maven groupId. 저장 경로가 된다
version = '1.0.0'        // 이미 게시된 버전은 다시 쓸 수 없다

java {
    withSourcesJar()     // 선택. sources jar 도 함께 올린다
}

publishing {
    publications {
        create("mavenJava", MavenPublication) {
            artifactId = 'my-lib'
            from components.java
        }
    }
    repositories {
        // publishToR2 가 쓰는 로컬 staging 저장소. 이름은 반드시 staging
        maven {
            name = "staging"
            url = layout.buildDirectory.dir("staging-repo")
        }
    }
}

apply from: 'gradle/publish-r2.gradle'
```

지켜야 할 조건:

- 저장소 이름은 `staging`이어야 합니다. 스크립트가 `publishAllPublicationsToStagingRepository` 태스크에 의존합니다.
- 스크립트는 artifactId를 `-Pr2ArtifactId` → `archives_base_name` 속성 → `project.name` 순서로 찾습니다. publication의 `artifactId`와 다르면 게시 결과를 찾지 못해 실패합니다. 다르게 쓸 때는 `gradle.properties`에 `r2ArtifactId=my-lib`을 넣으세요.
- 검증 단계는 `<artifactId>-<version>.jar`를 확인합니다. jar 패키징이 아닌 publication(BOM, platform 등)은 이 검증에서 실패하므로 스크립트의 확인 대상 목록을 고쳐야 합니다.
- `build` 태스크가 있어야 합니다. 없으면 `-PskipBuild=true`로 실행합니다.
- 멀티 모듈 프로젝트는 게시할 모듈마다 `publishing` 블록과 `apply from`을 넣고 `./gradlew :<모듈>:publishToR2`로 실행합니다. 각 모듈이 staging 폴더를 따로 씁니다.
- `publishing.repositories`에 `https://repo.biryeong.kim/releases/`를 업로드 대상으로 넣지 마세요. 읽기 전용이라 `./gradlew publish`가 실패합니다.

### 3. 처음 실행

```sh
./gradlew publishToR2 -PdryRun=true
```

업로드할 파일 목록이 나오고 `dryRun: 업로드하지 않았습니다`로 끝나면 설정이 맞습니다. 이 단계도 R2에서 메타데이터를 읽어 오므로 rclone 설정이 필요합니다.

## 릴리즈할 때마다

1. 작업 트리가 깨끗한지, 올바른 브랜치인지 확인합니다.
2. 버전을 올립니다. 호환이 깨지면 major, 기능 추가는 minor, 수정은 patch.
3. README 등에 적힌 버전·좌표를 새 버전으로 바꾸고 릴리즈 커밋으로 묶습니다.
4. `./gradlew publishToR2 -PdryRun=true`로 올라갈 파일을 확인합니다.
5. `./gradlew publishToR2`로 게시합니다. 마지막에 `200 ...pom`, `200 ...jar`, `게시 완료: <group>:<artifact>:<version>`이 나오면 성공입니다.
6. 게시가 성공한 뒤에 태그를 만들고 푸시합니다. 실패한 게시에 태그가 남지 않게 하기 위해서입니다.

## 옵션 (`-P`)

| 옵션 | 기본값 | 용도 |
|---|---|---|
| `r2Remote` | `r2` | rclone remote 이름 |
| `r2Bucket` | `maven` | 버킷 |
| `r2Path` | `releases` | 버킷 안 경로 |
| `publicBase` | `https://repo.biryeong.kim` | 검증에 쓰는 공개 주소 |
| `r2ArtifactId` | `archives_base_name` 또는 `project.name` | 게시 결과를 찾을 artifactId |
| `dryRun=true` | 꺼짐 | staging까지만 하고 올릴 파일 목록만 출력 |
| `skipBuild=true` | 꺼짐 | 빌드·테스트 생략. 같은 커밋을 이미 검증했을 때만 |
| `forceUpload=true` | 꺼짐 | 기존 버전 덮어쓰기. 쓰지 마세요 |

## 사용하는 쪽 설정

```groovy
repositories {
    maven { url "https://repo.biryeong.kim/releases/" }
}
dependencies {
    implementation "com.example:my-lib:1.0.0"
}
```

## 지켜야 할 것

- 게시된 버전은 고치지 않습니다. 잘못 올렸으면 patch 버전을 새로 올립니다. `forceUpload`로 덮어쓰면 이미 받아간 쪽의 Gradle 캐시·체크섬과 어긋납니다.
- 버킷은 여러 프로젝트가 함께 씁니다. R2의 파일을 지우지 않고, 업로드는 항상 `rclone copy`로 합니다. `rclone sync`나 `delete`는 쓰지 않습니다.
- 다른 사람이 쓰는 `group`/artifactId와 겹치지 않게 합니다. 처음 게시하기 전에 `rclone lsf r2:maven/releases/<group 경로>/ --s3-no-check-bucket`으로 확인하세요.
- 키는 rclone 설정이나 환경 변수에만 둡니다. `gradle.properties`, 저장소, 이슈, 채팅에 붙여 넣지 마세요.
- 같은 아티팩트를 두 사람이 동시에 게시하지 않습니다. `maven-metadata.xml`을 받아서 병합한 뒤 덮어쓰는 방식이라, 동시에 올리면 한쪽 버전이 목록에서 빠질 수 있습니다.

## 문제 해결

| 증상 | 원인과 조치 |
|---|---|
| `rclone 이 설치되어 있지 않습니다` | rclone을 설치하고 `PATH`에 있는지 확인합니다. |
| `R2 접근 실패 (r2:maven/releases)` | remote 이름이 `r2`인지, 키·엔드포인트가 맞는지 `rclone lsf`로 확인합니다. |
| `Task 'publishAllPublicationsToStagingRepository' not found` | `publishing.repositories`에 `name = "staging"` 저장소가 없습니다. |
| `게시 결과가 없습니다` | artifactId가 스크립트가 찾는 이름과 다릅니다. `r2ArtifactId`를 지정하세요. |
| `이미 게시된 버전입니다` | 버전을 올립니다. |
| 업로드 후 공개 URL 확인 실패 | 잠시 뒤 `curl -I <URL>`로 다시 확인합니다. 계속 404면 `rclone lsf`로 경로를 봅니다. |
| 사용하는 쪽에서 새 버전을 못 찾음 | `maven-metadata.xml`에 버전이 들어갔는지 확인하고, `--refresh-dependencies`로 다시 받습니다. |

## 키 인수인계 체크리스트

- [ ] 받는 사람 전용 R2 API 토큰을 새로 발급했다 (`maven` 버킷, Object Read & Write).
- [ ] Access Key ID, Secret Access Key, 계정 ID를 이 문서와 다른 채널로 전달했다.
- [ ] 받는 사람이 `rclone lsf r2:maven/releases/ --s3-no-check-bucket`에 성공했다.
- [ ] 받는 사람이 자기 프로젝트에서 `./gradlew publishToR2 -PdryRun=true`를 실행해 봤다.
- [ ] 키를 회수할 때 Cloudflare 대시보드에서 토큰을 폐기하는 방법을 알고 있다.
