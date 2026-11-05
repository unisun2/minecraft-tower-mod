# minecraft-tower-mod

스테이지 단위 월드 생성 모드 (Fabric).

- **Minecraft 26.3** / Fabric Loader 0.20.1 / Fabric API 0.165.0+26.3 / **Java 25**
- 한 스테이지 = 바이옴 1~2개 + **스테이지 고유 지형**, **4000 × 4000** 블록
- 스테이지는 **X축을 따라 일렬**로 나열, 스테이지 사이에 **100청크(1600블록) 마진**
- 스테이지 지형은 **32블록 높여서** 생성 (스테이지 해수면 63 → 95)
- 스테이지 바깥은 스테이지보다 조금 낮은 **물(수면 88)**로 채움 → 바다 위에 솟은 4000×4000 섬

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
- 마진과 스테이지 영역 밖(Z ±2000 바깥, 첫 스테이지 앞, 마지막 스테이지 뒤)은 바다입니다: 기반암 → 석재 → 모래 3칸(y 53~55) → 물(y 56~87). 바이옴은 `deep_ocean`이라 해초·켈프·난파선·해저 유적 등이 생깁니다.
- 바이옴 2개 스테이지는 노이즈로 구불구불한 경계를 만들어 대략 반씩 나눕니다.

## 구조

- `StageChunkGenerator` (`towermod:stages`): 스테이지마다 바닐라 `NoiseBasedChunkGenerator`를 하나씩 두고, 청크가 속한 스테이지의 생성기로 지형·지표·동굴·몹 생성을 넘깁니다. 스테이지별 `RandomState`는 월드 시드로 직접 만듭니다. 바깥 청크는 직접 바닥+물로 채웁니다.
- `TerrainShift`: 노이즈 설정의 지형을 위로 옮김. 지형 높이를 정하는 `y_clamped_gradient`(지표 깊이 기울기, 위/아래 슬라이드)를 모두 dy만큼 옮기고, 해수면과 지표 규칙의 `y_above` 절대 높이도 같이 옮깁니다.
- `OutsideFill`: 바깥 바다 설정.
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
  "stage_height_offset": 32,
  "margin_biome": "minecraft:deep_ocean",
  "outside": {
    "water_level": 88,
    "floor_y": 56,
    "block": { "Name": "minecraft:stone" },
    "top_block": { "Name": "minecraft:sand" },
    "fluid": { "Name": "minecraft:water", "Properties": { "level": "0" } }
  },
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
| `stage_height_offset` | 32 | 스테이지 지형을 올리는 높이(블록, 음수면 내림). 64 이하 권장 (월드 천장 320) |
| `margin_biome` | (필수) | 바깥 바이옴 |
| `outside.water_level` | 88 | 바깥 수면 (바닐라 해수면과 같은 의미: 물 맨 윗칸 = 87) |
| `outside.floor_y` | 56 | 바깥 바다 바닥. 이 높이부터 물 |
| `outside.block` / `top_block` / `fluid` | 석재 / 모래 / 물 | 바닥 블록, 바닥 위 3칸 블록, 채울 액체 |
| `stages[].biomes` | (필수) | 1~2개 |
| `stages[].noise_settings` | (필수) | 그 스테이지의 지형. 바닐라 `minecraft:overworld`, `large_biomes`, `amplified` 등 또는 직접 만든 `data/<ns>/worldgen/noise_settings/*.json` |

월드를 만든 뒤에는 설정이 `level.dat`에 저장되므로, 프리셋을 바꿔도 기존 월드에는 적용되지 않습니다.

### 스테이지 전용 지형 만들기

바닐라 버전 jar의 `data/minecraft/worldgen/noise_settings/overworld.json`을 `data/towermod/worldgen/noise_settings/<이름>.json`으로 복사해서 `noise_router`(지형 모양)와 `surface_rule`(지표 블록)을 고친 뒤 `"noise_settings": "towermod:<이름>"`으로 지정하면 됩니다.

## 알아둘 점

- 바닐라 노이즈 설정을 쓰는 스테이지는 그 설정의 지형(산/바다 포함)이 그대로 나오고, 바이옴은 지표 블록·나무·구조물·몹만 결정합니다.
- 바이옴은 Y와 무관하므로 동굴 바이옴(무성한 동굴, 딥 다크)은 생기지 않습니다.
- 스테이지 가장자리는 바다로 잘린 절벽입니다. 스테이지 안 바다(수면 95)가 가장자리에 닿으면 바깥 바다(88)보다 높아서, 블록 업데이트가 일어나면 폭포처럼 흘러내립니다.
- 가장자리 근처 구조물(마을 등)은 일부가 바깥 바다 쪽으로 튀어나올 수 있습니다. 바깥에는 바다 구조물(난파선, 해저 유적, 해저 신전)이 생길 수 있습니다.
- 높이를 올리면 일부 바이옴의 절대 높이 기반 장식(배드랜드 띠, 늪 물웅덩이 등)도 같이 올라갑니다. 기반암·심층암 위치는 그대로입니다.
- `floating_islands`처럼 아래가 비어 있는 지형은 바깥 바다와 맞닿으면 물벽이 생기므로 이 모드에선 권장하지 않습니다.
- 월드 전체 해수면(물고기 스폰 등 기준)은 바깥 수면(`outside.water_level`)입니다.

## 빌드

```
./gradlew build      # build/libs/towermod-<version>.jar
./gradlew runClient  # 개발 클라이언트 실행
```
