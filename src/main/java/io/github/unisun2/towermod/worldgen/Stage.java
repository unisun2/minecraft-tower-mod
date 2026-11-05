package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * 하나의 스테이지 = 바이옴 1~2개 + 지형(노이즈 설정) 1개.
 *
 * <pre>{ "biomes": ["minecraft:ocean", "minecraft:beach"], "noise_settings": "minecraft:overworld", "terrain_offset": -40 }</pre>
 *
 * {@code terrain_offset}은 해수면은 그대로 두고 지형만 더 올리거나(+) 내리는(-) 값이다.
 * 바다 스테이지는 지형을 내려서 대부분 물에 잠기게 만든다.
 */
public record Stage(List<Holder<Biome>> biomes, Holder<NoiseGeneratorSettings> noiseSettings, int terrainOffset) {
	public static final int MAX_BIOMES = 2;

	public static final Codec<Stage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Biome.CODEC.listOf().validate(Stage::checkSize).fieldOf("biomes").forGetter(Stage::biomes),
			NoiseGeneratorSettings.CODEC.fieldOf("noise_settings").forGetter(Stage::noiseSettings),
			Codec.intRange(-256, 256).optionalFieldOf("terrain_offset", 0).forGetter(Stage::terrainOffset)
	).apply(instance, Stage::new));

	public Stage {
		biomes = List.copyOf(biomes);
	}

	private static DataResult<List<Holder<Biome>>> checkSize(List<Holder<Biome>> biomes) {
		if (biomes.isEmpty() || biomes.size() > MAX_BIOMES) {
			return DataResult.error(() -> "A stage must have 1 to " + MAX_BIOMES + " biomes, got " + biomes.size());
		}
		return DataResult.success(biomes);
	}
}
