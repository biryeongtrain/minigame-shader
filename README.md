# Danta-Shader
미니게임(단타) 에서 사용될 여러 쉐이더를 한 프로젝트로 합친 모드입니다.

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

## How to use

```groovy
repositories {
    maven ( url "https://repo.biryeong.kim/releases/")
}

dependencies {
    implementation include 'kim.biryeong:danta-shader:1.0.0'
}
 
```

ShaderFX : just use command `/shaderfx run`
RGBMapUtils, DisplayHud : 테스트 커멘드 확인해서 예시 참조