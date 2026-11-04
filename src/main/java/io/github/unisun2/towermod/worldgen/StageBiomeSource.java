package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * X축으로 일렬로 이어지는 스테이지들에 바이옴을 배치하는 BiomeSource.
 *
 * <ul>
 *   <li>바이옴 1개 스테이지: 스테이지 전체가 그 바이옴.</li>
 *   <li>바이옴 2개 스테이지: 노이즈로 경계가 구불구불하게 두 바이옴이 나뉜다.</li>
 *   <li>스테이지 영역 밖: {@code outside_biome}이 있으면 그 바이옴, 없으면 가장 가까운 스테이지를 연장.</li>
 * </ul>
 *
 * 바이옴은 높이(Y)와 무관하게 결정되므로 동굴 바이옴(lush_caves 등)은 따로 생기지 않는다.
 */
public class StageBiomeSource extends BiomeSource {
	public static final int DEFAULT_STAGE_SIZE = 4000;
	public static final int DEFAULT_BLEND_SCALE = 384;

	public static final MapCodec<StageBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(64, 1 << 20).optionalFieldOf("stage_size", DEFAULT_STAGE_SIZE)
					.forGetter(StageBiomeSource::stageSize),
			Codec.intRange(16, 1 << 16).optionalFieldOf("blend_scale", DEFAULT_BLEND_SCALE)
					.forGetter(StageBiomeSource::blendScale),
			Stage.CODEC.listOf().validate(StageBiomeSource::checkStages).fieldOf("stages")
					.forGetter(StageBiomeSource::stages),
			Biome.CODEC.optionalFieldOf("outside_biome")
					.forGetter(StageBiomeSource::outsideBiome)
	).apply(instance, StageBiomeSource::new));

	private final int blendScale;
	private final List<Stage> stages;
	private final Optional<Holder<Biome>> outsideBiome;
	private final StageLayout layout;

	public StageBiomeSource(int stageSize, int blendScale, List<Stage> stages, Optional<Holder<Biome>> outsideBiome) {
		this.blendScale = blendScale;
		this.stages = List.copyOf(stages);
		this.outsideBiome = outsideBiome;
		this.layout = new StageLayout(stageSize, this.stages.size());
	}

	private static DataResult<List<Stage>> checkStages(List<Stage> stages) {
		return stages.isEmpty() ? DataResult.error(() -> "At least one stage is required") : DataResult.success(stages);
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return Stream.concat(
				stages.stream().flatMap(stage -> stage.biomes().stream()),
				outsideBiome.stream()
		).distinct();
	}

	/** x, y, z 는 쿼트 좌표(블록 >> 2). */
	@Override
	public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
		int blockX = QuartPos.toBlock(x);
		int blockZ = QuartPos.toBlock(z);

		int index = layout.rawIndexAt(blockX);
		if (!layout.contains(blockX, blockZ)) {
			if (outsideBiome.isPresent()) {
				return outsideBiome.get();
			}
			index = layout.clampIndex(index);
		}
		return biomeInStage(index, blockX, blockZ);
	}

	private Holder<Biome> biomeInStage(int index, int blockX, int blockZ) {
		List<Holder<Biome>> biomes = stages.get(index).biomes();
		if (biomes.size() == 1) {
			return biomes.getFirst();
		}
		return StageLayout.splitNoise(index, blockX, blockZ, blendScale) < 0.0 ? biomes.get(0) : biomes.get(1);
	}

	public int stageSize() {
		return layout.size();
	}

	public int blendScale() {
		return blendScale;
	}

	public List<Stage> stages() {
		return stages;
	}

	public Optional<Holder<Biome>> outsideBiome() {
		return outsideBiome;
	}

	public StageLayout layout() {
		return layout;
	}
}
