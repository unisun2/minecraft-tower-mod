package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 스테이지마다 다른 지형을 만드는 ChunkGenerator.
 *
 * <p>스테이지마다 바닐라 {@link NoiseBasedChunkGenerator}를 하나씩 만들어 두고, 청크가 속한 스테이지의
 * 생성기로 작업을 넘긴다. 스테이지 경계는 청크 경계와 일치하므로 한 청크는 정확히 한 스테이지에 속한다.
 * 마진 청크는 아무것도 만들지 않는다(공허).
 *
 * <p>바닐라는 {@code NoiseBasedChunkGenerator}가 아닌 생성기에는 더미 {@link RandomState}를 넘기므로,
 * 스테이지별 RandomState는 시드를 받는 {@link #createState} 시점에 직접 만든다.
 */
public class StageChunkGenerator extends ChunkGenerator {
	public static final MapCodec<StageChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			StageBiomeSource.CODEC.forGetter(StageChunkGenerator::stageBiomeSource),
			RegistryOps.retrieveGetter(Registries.NOISE)
	).apply(instance, StageChunkGenerator::new));

	private final StageBiomeSource stageBiomeSource;
	private final StageLayout layout;
	private final HolderGetter<NormalNoise.NoiseParameters> noises;
	private final NoiseBasedChunkGenerator[] delegates;
	private final int minY;
	private final int genDepth;

	private volatile Seeded seeded;

	private record Seeded(long seed, RandomState[] states) {
	}

	public StageChunkGenerator(StageBiomeSource biomeSource, HolderGetter<NormalNoise.NoiseParameters> noises) {
		super(biomeSource);
		this.stageBiomeSource = biomeSource;
		this.layout = biomeSource.layout();
		this.noises = noises;

		List<Stage> stages = biomeSource.stages();
		this.delegates = new NoiseBasedChunkGenerator[stages.size()];
		int bottom = Integer.MAX_VALUE;
		int top = Integer.MIN_VALUE;
		for (int i = 0; i < delegates.length; i++) {
			NoiseBasedChunkGenerator delegate = new NoiseBasedChunkGenerator(biomeSource, stages.get(i).noiseSettings());
			delegates[i] = delegate;
			bottom = Math.min(bottom, delegate.getMinY());
			top = Math.max(top, delegate.getMinY() + delegate.getGenDepth());
		}
		this.minY = bottom;
		this.genDepth = top - bottom;
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	public StageBiomeSource stageBiomeSource() {
		return stageBiomeSource;
	}

	public StageLayout layout() {
		return layout;
	}

	// ---- 시드 / RandomState ----

	@Override
	public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
		ensureSeeded(seed);
		return super.createState(structureSets, randomState, seed);
	}

	private void ensureSeeded(long seed) {
		Seeded current = seeded;
		if (current != null && current.seed() == seed) return;

		// 같은 노이즈 설정을 쓰는 스테이지끼리는 RandomState를 공유한다.
		Map<NoiseGeneratorSettings, RandomState> cache = new IdentityHashMap<>();
		RandomState[] states = new RandomState[delegates.length];
		for (int i = 0; i < states.length; i++) {
			NoiseGeneratorSettings settings = stageBiomeSource.stages().get(i).noiseSettings().value();
			states[i] = cache.computeIfAbsent(settings, s -> RandomState.create(s, noises, seed));
		}
		seeded = new Seeded(seed, states);
	}

	private RandomState state(int stage) {
		Seeded current = seeded;
		if (current == null) {
			throw new IllegalStateException("StageChunkGenerator was used before createState() provided the world seed");
		}
		return current.states()[stage];
	}

	private int stageOf(ChunkPos pos) {
		return layout.stageAt(pos.getMinBlockX(), pos.getMinBlockZ());
	}

	// ---- 청크 생성 단계 ----

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		int stage = stageOf(chunk.getPos());
		if (stage == StageLayout.NONE) {
			return CompletableFuture.completedFuture(chunk); // 마진: 공허
		}
		return delegates[stage].fillFromNoise(blender, state(stage), structureManager, chunk);
	}

	@Override
	public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
		int stage = stageOf(chunk.getPos());
		if (stage == StageLayout.NONE) return;
		delegates[stage].buildSurface(level, structureManager, state(stage), chunk);
	}

	@Override
	public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState, BiomeManager biomeManager,
			StructureManager structureManager, ChunkAccess chunk) {
		int stage = stageOf(chunk.getPos());
		if (stage == StageLayout.NONE) return;
		delegates[stage].applyCarvers(level, seed, state(stage), biomeManager, structureManager, chunk);
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion level) {
		int stage = stageOf(level.getCenter());
		if (stage == StageLayout.NONE) return;
		delegates[stage].spawnOriginalMobs(level);
	}

	// ---- 높이 조회 (구조물 배치, 스폰 등) ----

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		int stage = layout.stageAt(x, z);
		if (stage == StageLayout.NONE) {
			return level.getMinY();
		}
		return delegates[stage].getBaseHeight(x, z, type, level, state(stage));
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		int stage = layout.stageAt(x, z);
		if (stage == StageLayout.NONE) {
			return new NoiseColumn(level.getMinY(), new BlockState[0]);
		}
		return delegates[stage].getBaseColumn(x, z, level, state(stage));
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
		int stage = layout.stageAt(pos.getX(), pos.getZ());
		if (stage == StageLayout.NONE) {
			info.add("Stage: margin");
			return;
		}
		info.add("Stage: " + stage + " (" + stageBiomeSource.stages().get(stage).noiseSettings().getRegisteredName() + ")");
		delegates[stage].addDebugScreenInfo(info, state(stage), pos);
	}

	// ---- 월드 전체 값 ----

	@Override
	public int getGenDepth() {
		return genDepth;
	}

	@Override
	public int getMinY() {
		return minY;
	}

	/** 월드 단위 해수면(몹 스폰 등)은 0번 스테이지 기준. 각 스테이지 지형 안의 물 높이는 자기 설정을 따른다. */
	@Override
	public int getSeaLevel() {
		return delegates[0].getSeaLevel();
	}
}
