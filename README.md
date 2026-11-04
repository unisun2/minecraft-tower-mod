# minecraft-tower-mod

스테이지 단위 월드 생성 모드 (Fabric).

- **Minecraft 26.3** / Fabric Loader 0.20.1 / Fabric API 0.165.0+26.3 / **Java 25**
- 한 스테이지 = 바이옴 1~2개 + **스테이지 고유 지형**, **4000 × 4000** 블록
- 스테이지는 **X축을 따라 일렬**로 나열, 스테이지 사이에 **100청크(1600블록) 마진**(공허)

![기본 프리셋 배치 미리보기](docs/stage_map.png)

## 배치

0번 스테이지의 중심이 (0, 0)입니다. 주기 = 4000 + 1600 = 5600 블록.

| 구역 | X 범위 | Z 범위 |
|---|---|---|
| 스테이지 0 | -2000 ~ 1999 | -2000 ~ 1999 |
| 마진 | 2000 ~ 3599 | — |
| 스테이지 1 | 3600 ~ 7599 | -2000 ~ 1999 |
| 스테이지 n | n·5600 − 2000 ~ n·5600 + 1999 | -2000 ~ 1999 |

- 모든 경계가 청크 경계와 일치합니다 (한 청크는 항상 한 스테이지 또는 마진에만 속함).
- 마진과 스테이지 영역 밖(Z ±2000 바깥, 첫 스테이지 앞, 마지막 스테이지 뒤)은 블록이 없는 공허입니다. 즉 각 스테이지는 공허에 떠 있는 4000×4000 섬입니다.
- 바이옴 2개 스테이지는 노이즈로 구불구불한 경계를 만들어 대략 반씩 나눕니다.

## 구조

- `StageChunkGenerator` (`towermod:stages`): 스테이지마다 바닐라 `NoiseBasedChunkGenerator`를 하나씩 두고, 청크가 속한 스테이지의 생성기로 지형·지표·동굴·몹 생성을 넘깁니다. 스테이지별 `RandomState`는 월드 시드로 직접 만듭니다.
- `StageBiomeSource`: 스테이지 바이옴 배치 (생성기 내부에서 같은 설정으로 생성).
- `StageLayout`: 좌표 → 스테이지 계산 (순수 Java).

## 사용법

1. 싱글: 월드 생성 → 월드 탭 → 월드 유형 **"스테이지"** 선택
2. 서버: `server.properties`에 `level-type=towermod\:stages`
3. 게임 내 `/stage` — 현재 위치의 스테이지, `/stage <번호>` — 해당 스테이지 범위/중심/바이옴/지형 (F3 화면에도 스테이지 표시)

## 스테이지 구성 바꾸기

`src/main/resources/data/towermod/worldgen/world_preset/stages.json` (데이터팩으로 덮어써도 됨)

```json
"generator": {
  "type": "towermod:stages",
  "stage_size": 4000,
  "margin_chunks": 100,
  "blend_scale": 384,
  "margin_biome": "minecraft:the_void",
  "stages": [
    { "biomes": ["minecraft:plains"], "noise_settings": "minecraft:overworld" },
    { "biomes": ["minecraft:jungle", "minecraft:bamboo_jungle"], "noise_settings": "minecraft:amplified" }
  ]
}
```

| 필드 | 기본값 | 설명 |
|---|---|---|
| `stage_size` | 4000 | 스테이지 한 변(블록). 32의 배수 |
| `margin_chunks` | 100 | 스테이지 사이 마진(청크) |
| `blend_scale` | 384 | 2바이옴 스테이지의 바이옴 덩어리 크기(블록) |
| `margin_biome` | (필수) | 마진/영역 밖 바이옴 |
| `stages[].biomes` | (필수) | 1~2개 |
| `stages[].noise_settings` | (필수) | 그 스테이지의 지형. 바닐라 `minecraft:overworld`, `large_biomes`, `amplified`, `floating_islands`, `caves` 등 또는 직접 만든 `data/<ns>/worldgen/noise_settings/*.json` |

월드를 만든 뒤에는 설정이 `level.dat`에 저장되므로, 프리셋을 바꿔도 기존 월드에는 적용되지 않습니다.

### 스테이지 전용 지형 만들기

바닐라 버전 jar의 `data/minecraft/worldgen/noise_settings/overworld.json`을 `data/towermod/worldgen/noise_settings/<이름>.json`으로 복사해서 `noise_router`(지형 모양)와 `surface_rule`(지표 블록)을 고친 뒤 `"noise_settings": "towermod:<이름>"`으로 지정하면 됩니다.

## 알아둘 점

- 바닐라 노이즈 설정을 쓰는 스테이지는 그 설정의 지형(산/바다 포함)이 그대로 나오고, 바이옴은 지표 블록·나무·구조물·몹만 결정합니다.
- 바이옴은 Y와 무관하므로 동굴 바이옴(무성한 동굴, 딥 다크)은 생기지 않습니다.
- 스테이지 가장자리는 공허 쪽으로 잘린 절벽입니다. 가장자리 근처 구조물(마을 등)은 일부가 마진 쪽으로 튀어나올 수 있습니다.
- 마진은 구조물 바이옴이 아니라 거기서 시작하는 구조물은 없습니다. 엔더 요새 위치가 마진에 걸리면 그 요새는 생성되지 않습니다.
- 월드 전체의 해수면(몹 스폰 기준)은 0번 스테이지 설정을 따릅니다.

## 빌드

```
./gradlew build      # build/libs/towermod-<version>.jar
./gradlew runClient  # 개발 클라이언트 실행
```
