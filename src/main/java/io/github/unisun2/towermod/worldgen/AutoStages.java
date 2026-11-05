package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * 모든 스테이지 바이옴(태그 {@code towermod:stage/*})으로 스테이지를 자동 구성하는 설정.
 * 짝짓기 규칙은 {@link StagePlanner}, 지형은 분류별로 여기서 정한다.
 *
 * <pre>
 * "stages": {
 *   "land_terrain": ["minecraft:overworld", "minecraft:large_biomes", "minecraft:amplified"],
 *   "ocean_terrain": "minecraft:overworld",
 *   "ocean_terrain_offset": -40,
 *   "nether_terrain": "minecraft:nether",
 *   "end_terrain": "minecraft:end"
 * }
 * </pre>
 */
public record AutoStages(
		List<Holder<NoiseGeneratorSettings>> landTerrain,
		Holder<NoiseGeneratorSettings> oceanTerrain,
		int oceanTerrainOffset,
		Holder<NoiseGeneratorSettings> netherTerrain,
		Holder<NoiseGeneratorSettings> endTerrain) {

	public static final int DEFAULT_OCEAN_TERRAIN_OFFSET = -40;

	public static final Codec<AutoStages> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			NoiseGeneratorSettings.CODEC.listOf().validate(AutoStages::nonEmpty).fieldOf("land_terrain").forGetter(AutoStages::landTerrain),
			NoiseGeneratorSettings.CODEC.fieldOf("ocean_terrain").forGetter(AutoStages::oceanTerrain),
			Codec.intRange(-256, 256).optionalFieldOf("ocean_terrain_offset", DEFAULT_OCEAN_TERRAIN_OFFSET).forGetter(AutoStages::oceanTerrainOffset),
			NoiseGeneratorSettings.CODEC.fieldOf("nether_terrain").forGetter(AutoStages::netherTerrain),
			NoiseGeneratorSettings.CODEC.fieldOf("end_terrain").forGetter(AutoStages::endTerrain)
	).apply(instance, AutoStages::new));

	public AutoStages {
		landTerrain = List.copyOf(landTerrain);
	}

	private static DataResult<List<Holder<NoiseGeneratorSettings>>> nonEmpty(List<Holder<NoiseGeneratorSettings>> list) {
		return list.isEmpty() ? DataResult.error(() -> "land_terrain must not be empty") : DataResult.success(list);
	}

	/** 시드로 스테이지 목록을 만든다. 같은 시드 + 같은 바이옴 태그면 항상 같은 결과. */
	public List<Stage> generate(HolderGetter<Biome> biomes, long seed) {
		Map<StageCategory, List<Holder<Biome>>> groups = BiomeCategories.collect(biomes);
		List<StagePlanner.Planned<Holder<Biome>>> plan =
				StagePlanner.plan(groups, biome -> biome.value().getBaseTemperature(), seed);

		Random terrainRandom = new Random(seed ^ 0x7E44A1_4L);
		List<Stage> stages = new ArrayList<>(plan.size());
		for (StagePlanner.Planned<Holder<Biome>> planned : plan) {
			stages.add(switch (planned.category()) {
				case OCEAN -> new Stage(planned.biomes(), oceanTerrain, oceanTerrainOffset);
				case NETHER -> new Stage(planned.biomes(), netherTerrain, 0);
				case END -> new Stage(planned.biomes(), endTerrain, 0);
				default -> new Stage(planned.biomes(), landTerrain.get(terrainRandom.nextInt(landTerrain.size())), 0);
			});
		}
		return stages;
	}
}
