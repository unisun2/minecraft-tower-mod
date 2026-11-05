package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * X축으로 일렬로 이어지는 스테이지들에 바이옴을 배치하는 BiomeSource.
 * 설정은 모두 {@link StageChunkGenerator}가 가지고 있고, 이 클래스는 그 생성기가 만들어 쓰는 "보기"다.
 * 스테이지 목록은 시드가 정해진 뒤({@code createState}) 생성기가 {@link #bind}로 넘겨준다.
 *
 * <ul>
 *   <li>바이옴 1개 스테이지: 스테이지 전체가 그 바이옴.</li>
 *   <li>바이옴 2개 스테이지: 노이즈로 경계가 구불구불하게 두 바이옴이 나뉜다.</li>
 *   <li>마진과 스테이지 영역 밖: {@code margin_biome} (기본 deep_ocean).</li>
 * </ul>
 *
 * 바이옴은 높이(Y)와 무관하게 결정되므로 동굴 바이옴도 지표에 그대로 깔린다.
 */
public class StageBiomeSource extends BiomeSource {
	/** 단독으로 쓰일 때는 생성기 설정을 그대로 읽는다. */
	public static final MapCodec<StageBiomeSource> CODEC =
			StageChunkGenerator.CODEC.xmap(StageChunkGenerator::stageBiomeSource, StageBiomeSource::generator);

	private final int blendScale;
	private final Holder<Biome> marginBiome;
	private StageChunkGenerator generator;
	private volatile Bound bound;

	private record Bound(StageLayout layout, List<Stage> stages) {
	}

	StageBiomeSource(int blendScale, Holder<Biome> marginBiome) {
		this.blendScale = blendScale;
		this.marginBiome = marginBiome;
	}

	void attach(StageChunkGenerator generator) {
		this.generator = generator;
	}

	void bind(StageLayout layout, List<Stage> stages) {
		this.bound = new Bound(layout, List.copyOf(stages));
	}

	public StageChunkGenerator generator() {
		return generator;
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		Bound current = bound;
		Stream<Holder<Biome>> stageBiomes = current != null
				? current.stages().stream().flatMap(stage -> stage.biomes().stream())
				: generator.candidateBiomes();
		return Stream.concat(stageBiomes, Stream.of(marginBiome)).distinct();
	}

	/** x, y, z 는 쿼트 좌표(블록 >> 2). */
	@Override
	public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
		Bound current = bound;
		if (current == null) {
			return marginBiome; // 시드가 정해지기 전에는 스테이지가 없다
		}

		int blockX = QuartPos.toBlock(x);
		int blockZ = QuartPos.toBlock(z);
		int index = current.layout().stageAt(blockX, blockZ);
		if (index == StageLayout.NONE) {
			return marginBiome;
		}

		List<Holder<Biome>> biomes = current.stages().get(index).biomes();
		if (biomes.size() == 1) {
			return biomes.getFirst();
		}
		return StageLayout.splitNoise(index, blockX, blockZ, blendScale) < 0.0 ? biomes.get(0) : biomes.get(1);
	}

	public int blendScale() {
		return blendScale;
	}

	public Holder<Biome> marginBiome() {
		return marginBiome;
	}
}
