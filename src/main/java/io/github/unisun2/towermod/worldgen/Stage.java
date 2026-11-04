package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * 하나의 스테이지 = 1~2개의 바이옴.
 *
 * <pre>{ "biomes": ["minecraft:desert", "minecraft:badlands"] }</pre>
 */
public record Stage(List<Holder<Biome>> biomes) {
	public static final int MAX_BIOMES = 2;

	public static final Codec<Stage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Biome.CODEC.listOf().validate(Stage::checkSize).fieldOf("biomes").forGetter(Stage::biomes)
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
