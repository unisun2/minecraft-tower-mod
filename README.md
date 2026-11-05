# minecraft-tower-mod

스테이지 단위 월드 생성 모드 (Fabric).

- **Minecraft 26.3** / Fabric Loader 0.20.1 / Fabric API 0.165.0+26.3 / **Java 25**
- 한 스테이지 = 바이옴 1~2개 + **스테이지 고유 지형**, **4000 × 4000** 블록
- 스테이지는 **X축을 따라 일렬**로 나열, 스테이지 사이에 **100청크(1600블록) 마진**
- 스테이지 지형은 **32블록 높여서** 생성 (스테이지 해수면 63 → 95)
- 스테이지 바깥은 스테이지보다 조금 낮은 **물(수면 88)**로 채움 → 바다 위에 솟은 4000×4000 섬
- 기본 프리셋은 **모든 바이옴을 시드에 따라 자동 배정** (바이옴 중복 없음, 해안만 중복 허용)

![배치 예시](docs/stage_map.png)

## 바이옴 배정 규칙

| 분류 | 태그 | 규칙 |
|---|---|---|
| 육지 | `towermod:stage/land` (= `#minecraft:is_overworld`의 나머지) | 육지끼리 2개씩 |
| 바다 | `towermod:stage/ocean` (= `#minecraft:is_ocean`) | 항상 **해안 1개와 세트** |
| 해안 | `towermod:stage/coast` (= `#minecraft:is_beach` + `stony_shore`) | 바다의 짝으로만 사용, **중복 허용** |
| 네더 | `towermod:stage/nether` (= `#minecraft:is_nether`) | 네더끼리 2개씩 |
| 엔드 | `towermod:stage/end` (= `#minecraft:is_end`) | 엔드끼리 2개씩 |
| 제외 | `towermod:stage/excluded` (기본 비어 있음) | 스테이지에 안 씀 |

- 해안을 뺀 모든 바이옴은 정확히 한 번씩 쓰입니다. 같은 분류 안에서 개수가 홀수면 하나는 단독 스테이지가 됩니다.
- 바다의 짝 해안은 온도가 비슷한 것 중에서 고릅니다 (얼어붙은 바다 → 눈 덮인 해변/돌 해안).
- 순서는 **육지 → 바다 → 네더 → 엔드**로 고정입니다. 각 묶음 안의 순서와 짝은 시드에 따라 섞입니다 (0번 스테이지 = 스폰은 항상 육지). 바닐라 기준 약 36개 스테이지가 나옵니다 (육지 약 21 → 바다 9 → 네더 3 → 엔드 3).
- 여러 분류 태그에 들어 있는 바이옴은 제외 → 네더 → 엔드 → 바다 → 해안 → 육지 순으로 앞선 분류를 따릅니다. 모드 바이옴도 태그만 맞으면 자동으로 들어갑니다.
- 직접 쓴 스테이지 목록도 같은 규칙으로 검사해서, 어기면 로그에 `Stage rule violated` 경고를 남깁니다.
- 확정된 스테이지 목록은 `level.dat`에 저장되므로, 나중에 모드나 태그가 바뀌어도 이미 만든 월드의 배치는 그대로입니다.

## 배치

0번 스테이지의 중심이 (0, 0)입니다. 주기 = 4000 + 1600 = 5600 블록.

| 구역 | X 범위 | Z 범위 |
|---|---|---|
| 스테이지 0 | -2000 ~ 1999 | -2000 ~ 1999 |
| 마진 | 2000 ~ 3599 | — |
| 스테이지 1 | 3600 ~ 7599 | -2000 ~ 1999 |
| 스테이지 n | n·5600 − 2000 ~ n·5600 + 1999 | -2000 ~ 1999 |

- 모든 경계가 청크 경계와 일치합니다 (한 청크는 항상 한 스테이지 또는 바깥에만 속함).
- 마진과 스테이지 영역 밖은 바다입니다: 기반암 → 석재 → 모래 3칸(y 53~55) → 물(y 56~87). 바이옴은 `deep_ocean`.
- 바이옴 2개 스테이지는 노이즈로 구불구불한 경계를 만들어 대략 반씩 나눕니다.

## 스테이지 지형

| 분류 | 기본 지형 | 비고 |
|---|---|---|
| 육지 | `overworld` / `large_biomes` / `amplified` 중 무작위 | +32 |
| 바다 | `overworld`, `ocean_terrain_offset` -40 | 해수면은 95 그대로, 지형만 40 더 낮춰서 대부분 물에 잠김 |
| 네더 | `nether` | 기반암 바닥(y 0)·천장(y ~160), 용암 바다(y 64) |
| 엔드 | `end` | 떠 있는 섬들. 섬 아래 빈 공간은 바깥 바다로 채워서 바다 위로 섬이 솟음 |

스테이지 안에서도 월드 바닥부터 처음 블록이 나올 때까지의 빈 공간은 바깥 바다(석재+모래+물)로 채웁니다. 보통 지형은 바닥이 막혀 있어 영향이 없고, 엔드처럼 떠 있는 지형이나 네더 아래(y < 0)가 채워집니다.

## 구조

| 클래스 | 역할 |
|---|---|
| `StageChunkGenerator` (`towermod:stages`) | 설정 전체. 시드가 정해지면 스테이지 확정 → 스테이지마다 바닐라 `NoiseBasedChunkGenerator`와 `RandomState`를 만들고 청크별로 위임 |
| `StageBiomeSource` | 확정된 스테이지로 바이옴 배치 |
| `AutoStages`, `StagePlanner` | 자동 배정 (분류별 짝짓기, 해안 고르기, 순서 섞기) |
| `StageRules`, `StageCategory`, `BiomeCategories` | 배정 규칙, 태그 기반 분류 |
| `TerrainShift` | 노이즈 설정의 지형 높이 이동 (`y_clamped_gradient`, 해수면, 지표 규칙 `y_above`, 생성 범위) |
| `OutsideFill` | 바깥 바다 설정 |
| `StageLayout` | 좌표 → 스테이지 계산 |

`StageLayout`, `StagePlanner`, `StageRules`, `StageCategory`는 마인크래프트 의존성 없는 순수 Java입니다.

## 사용법

1. 싱글: 월드 생성 → 월드 탭 → 월드 유형 **"스테이지"** 선택
2. 서버: `server.properties`에 `level-type=towermod\:stages`
3. 게임 내 명령어 (F3 화면에도 스테이지 표시)
   - `/stage` — 현재 위치의 스테이지
   - `/stage <번호>` — 해당 스테이지 범위/중심/바이옴/지형
   - `/stage list` — 전체 스테이지 바이옴 목록

## 설정

`src/main/resources/data/towermod/worldgen/world_preset/stages.json` (데이터팩으로 덮어써도 됨)

```json
"generator": {
  "type": "towermod:stages",
  "stage_size": 4000,
  "margin_chunks": 100,
  "blend_scale": 384,
  "stage_height_offset": 32,
  "margin_biome": "minecraft:deep_ocean",
  "outside": { "water_level": 88, "floor_y": 56 },
  "stages": {
    "land_terrain": ["minecraft:overworld", "minecraft:large_biomes", "minecraft:amplified"],
    "ocean_terrain": "minecraft:overworld",
    "ocean_terrain_offset": -40,
    "nether_terrain": "minecraft:nether",
    "end_terrain": "minecraft:end"
  }
}
```

`stages`에 객체를 주면 자동 배정, 배열을 주면 직접 지정입니다:

```json
"stages": [
  { "biomes": ["minecraft:plains", "minecraft:forest"], "noise_settings": "minecraft:overworld" },
  { "biomes": ["minecraft:warm_ocean", "minecraft:beach"], "noise_settings": "minecraft:overworld", "terrain_offset": -40 },
  { "biomes": ["minecraft:crimson_forest", "minecraft:warped_forest"], "noise_settings": "minecraft:nether" }
]
```

| 필드 | 기본값 | 설명 |
|---|---|---|
| `stage_size` | 4000 | 스테이지 한 변(블록). 32의 배수 |
| `margin_chunks` | 100 | 스테이지 사이 마진(청크) |
| `blend_scale` | 384 | 2바이옴 스테이지의 바이옴 덩어리 크기(블록) |
| `stage_height_offset` | 32 | 스테이지 지형과 해수면을 올리는 높이. 64 이하 권장 (월드 천장 320) |
| `margin_biome` | (필수) | 바깥 바이옴 |
| `outside.water_level` | 88 | 바깥 수면 (바닐라 해수면과 같은 의미: 물 맨 윗칸 = 87) |
| `outside.floor_y` | 56 | 바깥 바다 바닥. 이 높이부터 물 |
| `outside.block` / `top_block` / `fluid` | 석재 / 모래 / 물 | 바닥 블록, 바닥 위 3칸 블록, 채울 액체 |
| `stages[].biomes` | (필수) | 1~2개 |
| `stages[].noise_settings` | (필수) | 그 스테이지의 지형 |
| `stages[].terrain_offset` | 0 | 해수면은 두고 지형만 추가로 올림(+)/내림(-) |
| `stages.land_terrain` 외 | — | 자동 배정 시 분류별 지형 (`ocean_terrain_offset` 기본 -40) |

### 스테이지 전용 지형 만들기

바닐라 버전 jar의 `data/minecraft/worldgen/noise_settings/overworld.json`을 `data/towermod/worldgen/noise_settings/<이름>.json`으로 복사해서 `noise_router`(지형 모양)와 `surface_rule`(지표 블록)을 고친 뒤 `noise_settings`에 `towermod:<이름>`으로 지정하면 됩니다.

## 알아둘 점

- 네더·엔드 스테이지도 오버월드 차원 안에 있습니다. 피글린·호글린은 좀비화되고, 물은 정상적으로 쓸 수 있습니다.
- 동굴 바이옴(무성한 동굴, 점적석 동굴, 딥 다크)과 강도 육지 바이옴으로 취급되어 지표에 스테이지로 나옵니다. 빼고 싶으면 `towermod:stage/excluded` 태그에 넣으세요.
- 스테이지 가장자리는 바다로 잘린 절벽입니다. 스테이지 안 바다(수면 95)가 가장자리에 닿으면 바깥 바다(88)보다 높아서, 블록 업데이트가 일어나면 폭포처럼 흘러내립니다.
- 가장자리 근처 구조물(마을 등)은 일부가 바깥 바다 쪽으로 튀어나올 수 있습니다. 바깥에는 바다 구조물(난파선, 해저 유적, 해저 신전)이 생길 수 있습니다.
- 높이를 올리면 일부 바이옴의 절대 높이 기반 장식(배드랜드 띠, 늪 물웅덩이 등)도 같이 올라갑니다. 기반암·심층암 위치는 그대로입니다.
- 월드 전체 해수면(물고기 스폰 등 기준)은 바깥 수면(`outside.water_level`)입니다.

## 빌드

```
./gradlew build      # build/libs/towermod-<version>.jar
./gradlew runClient  # 개발 클라이언트 실행
```
