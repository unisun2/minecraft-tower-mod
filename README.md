# minecraft-tower-mod

스테이지 단위 월드 생성 모드 (Fabric).

- **Minecraft 26.3** / Fabric Loader 0.20.1 / Fabric API 0.165.0+26.3 / **Java 25**
- 한 스테이지 = 바이옴 1~2개, **4000 × 4000** 블록
- 스테이지는 **X축을 따라 일렬**로 나열

![기본 프리셋 배치 미리보기](docs/stage_map.png)

## 배치

0번 스테이지의 중심이 (0, 0)입니다.

| 스테이지 | X 범위 | Z 범위 |
|---|---|---|
| 0 | -2000 ~ 1999 | -2000 ~ 1999 |
| 1 | 2000 ~ 5999 | -2000 ~ 1999 |
| n | n·4000 − 2000 ~ n·4000 + 1999 | -2000 ~ 1999 |

- 바이옴 1개 스테이지: 스테이지 전체가 한 바이옴
- 바이옴 2개 스테이지: 노이즈로 구불구불한 경계를 만들어 두 바이옴이 대략 반씩 차지
- 스테이지 영역 밖: `outside_biome`을 지정하면 그 바이옴, 없으면 가장 가까운 스테이지 바이옴이 이어짐

## 사용법

1. 싱글: 월드 생성 → 월드 탭 → 월드 유형 **"스테이지"** 선택
2. 서버: `server.properties`에 `level-type=towermod\:stages`
3. 게임 내 `/stage` — 현재 위치의 스테이지, `/stage <번호>` — 해당 스테이지 범위/중심 좌표

## 스테이지 구성 바꾸기

`src/main/resources/data/towermod/worldgen/world_preset/stages.json` (데이터팩으로 덮어써도 됨)

```json
"biome_source": {
  "type": "towermod:stages",
  "stage_size": 4000,
  "blend_scale": 384,
  "stages": [
    { "biomes": ["minecraft:plains"] },
    { "biomes": ["minecraft:forest", "minecraft:birch_forest"] }
  ],
  "outside_biome": "minecraft:the_void"
}
```

| 필드 | 기본값 | 설명 |
|---|---|---|
| `stage_size` | 4000 | 스테이지 한 변의 길이(블록) |
| `blend_scale` | 384 | 2바이옴 스테이지에서 바이옴 덩어리 크기(블록) |
| `stages` | (필수) | 순서대로 X+ 방향으로 배치. 각 스테이지 `biomes`는 1~2개 |
| `outside_biome` | 없음 | 스테이지 영역 밖 바이옴 |

## 알아둘 점

- 지형 모양(산/바다/높낮이)은 바닐라 오버월드 노이즈 그대로이고, 바이옴은 지표 블록·나무/구조물·몹·색만 결정합니다. 예를 들어 사막 스테이지에도 산이나 바다가 생길 수 있습니다. 스테이지별 지형이 필요하면 다음 단계로 커스텀 ChunkGenerator/노이즈 설정이 필요합니다.
- 바이옴은 Y와 무관하게 정해지므로 동굴 바이옴(무성한 동굴, 딥 다크)은 생기지 않습니다.
- 스테이지 사이 경계는 직선으로 바뀝니다.

## 빌드

```
./gradlew build      # build/libs/towermod-<version>.jar
./gradlew runClient  # 개발 클라이언트 실행
```
