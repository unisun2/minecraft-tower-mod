package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * X축으로 일렬로 이어지는 스테이지들에 바이옴을 배치하는 BiomeSource.
 * 보통은 {@link StageChunkGenerator}가 내부에서 만들어 쓴다 (같은 필드를 공유).
 *
 * <ul>
 *   <li>바이옴 1개 스테이지: 스테이지 전체가 그 바이옴.</li>
 *   <li>바이옴 2개 스테이지: 노이즈로 경계가 구불구불하게 두 바이옴이 나뉜다.</li>
 *   <li>마진과 스테이지 영역 밖: {@code margin_biome}.</li>
 * </ul>
 *
 * 바이옴은 높이(Y)와 무관하게 결정되므로 동굴 바이옴(lush_caves 등)은 따로 생기지 않는다.
 */
public class StageBiomeSource extends BiomeSource {
	public static final int DEFAULT_STAGE_SIZE = 4000;
	public static final int DEFAULT_MARGIN_CHUNKS = 100;
	public static final int DEFAULT_BLEND_SCALE = 384;

	public static final MapCodec<StageBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(64, 1 << 20).validate(StageBiomeSource::checkStageSize)
					.optionalFieldOf("stage_size", DEFAULT_STAGE_SIZE)
					.forGetter(StageBiomeSource::stageSize),
			Codec.intRange(0, 1 << 16).optionalFieldOf("margin_chunks", DEFAULT_MARGIN_CHUNKS)
					.forGetter(StageBiomeSource::marginChunks),
			Codec.intRange(16, 1 << 16).optionalFieldOf("blend_scale", DEFAULT_BLEND_SCALE)
					.forGetter(StageBiomeSource::blendScale),
			Stage.CODEC.listOf().validate(StageBiomeSource::checkStages).fieldOf("stages")
					.forGetter(StageBiomeSource::stages),
			Biome.CODEC.fieldOf("margin_biome")
					.forGetter(StageBiomeSource::marginBiome)
	).apply(instance, StageBiomeSource::new));

	private final int blendScale;
	private final List<Stage> stages;
	private final Holder<Biome> marginBiome;
	private final StageLayout layout;

	public StageBiomeSource(int stageSize, int marginChunks, int blendScale, List<Stage> stages, Holder<Biome> marginBiome) {
		this.blendScale = blendScale;
		this.stages = List.copyOf(stages);
		this.marginBiome = marginBiome;
		this.layout = new StageLayout(stageSize, marginChunks * 16, this.stages.size());
	}

	private static DataResult<Integer> checkStageSize(int size) {
		return size % 32 == 0
				? DataResult.success(size)
				: DataResult.error(() -> "stage_size must be a multiple of 32 (chunk aligned), got " + size);
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
				Stream.of(marginBiome)
		).distinct();
	}

	/** x, y, z 는 쿼트 좌표(블록 >> 2). */
	@Override
	public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
		int blockX = QuartPos.toBlock(x);
		int blockZ = QuartPos.toBlock(z);

		int index = layout.stageAt(blockX, blockZ);
		if (index == StageLayout.NONE) {
			return marginBiome;
		}

		List<Holder<Biome>> biomes = stages.get(index).biomes();
		if (biomes.size() == 1) {
			return biomes.getFirst();
		}
		return StageLayout.splitNoise(index, blockX, blockZ, blendScale) < 0.0 ? biomes.get(0) : biomes.get(1);
	}

	public int stageSize() {
		return layout.size();
	}

	public int marginChunks() {
		return layout.margin() / 16;
	}

	public int blendScale() {
		return blendScale;
	}

	public List<Stage> stages() {
		return stages;
	}

	public Holder<Biome> marginBiome() {
		return marginBiome;
	}

	public StageLayout layout() {
		return layout;
	}
}
