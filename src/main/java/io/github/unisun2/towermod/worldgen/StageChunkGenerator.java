package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.chunk.LevelChunkSection;
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
 * 각 스테이지의 지형은 {@code stage_height_offset}만큼 위로 올려서 만든다({@link TerrainShift}).
 *
 * <p>스테이지 바깥(마진 + 영역 밖)은 {@link OutsideFill}에 따라 바닥과 물로 채운다.
 *
 * <p>바닐라는 {@code NoiseBasedChunkGenerator}가 아닌 생성기에는 더미 {@link RandomState}를 넘기므로,
 * 스테이지별 RandomState는 시드를 받는 {@link #createState} 시점에 직접 만든다.
 */
public class StageChunkGenerator extends ChunkGenerator {
	public static final int DEFAULT_HEIGHT_OFFSET = 32;

	public static final MapCodec<StageChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			StageBiomeSource.CODEC.forGetter(StageChunkGenerator::stageBiomeSource),
			Codec.intRange(-256, 256).optionalFieldOf("stage_height_offset", DEFAULT_HEIGHT_OFFSET)
					.forGetter(StageChunkGenerator::heightOffset),
			OutsideFill.CODEC.optionalFieldOf("outside", OutsideFill.defaults())
					.forGetter(StageChunkGenerator::outside),
			RegistryOps.retrieveGetter(Registries.NOISE)
	).apply(instance, StageChunkGenerator::new));

	private final StageBiomeSource stageBiomeSource;
	private final StageLayout layout;
	private final int heightOffset;
	private final OutsideFill outside;
	private final HolderGetter<NormalNoise.NoiseParameters> noises;
	/** 높이를 올린 스테이지별 노이즈 설정. */
	private final NoiseGeneratorSettings[] stageSettings;
	private final NoiseBasedChunkGenerator[] delegates;
	private final int minY;
	private final int genDepth;

	private volatile Seeded seeded;

	private record Seeded(long seed, RandomState[] states) {
	}

	public StageChunkGenerator(StageBiomeSource biomeSource, int heightOffset, OutsideFill outside,
			HolderGetter<NormalNoise.NoiseParameters> noises) {
		super(biomeSource);
		this.stageBiomeSource = biomeSource;
		this.layout = biomeSource.layout();
		this.heightOffset = heightOffset;
		this.outside = outside;
		this.noises = noises;

		List<Stage> stages = biomeSource.stages();
		this.stageSettings = new NoiseGeneratorSettings[stages.size()];
		this.delegates = new NoiseBasedChunkGenerator[stages.size()];

		// 같은 원본 설정은 한 번만 변환해서 공유한다 (RandomState 공유에도 쓰임).
		Map<NoiseGeneratorSettings, NoiseGeneratorSettings> shifted = new IdentityHashMap<>();
		int bottom = Integer.MAX_VALUE;
		int top = Integer.MIN_VALUE;
		for (int i = 0; i < delegates.length; i++) {
			NoiseGeneratorSettings original = stages.get(i).noiseSettings().value();
			NoiseGeneratorSettings settings = shifted.computeIfAbsent(original, s -> TerrainShift.shift(s, heightOffset));
			stageSettings[i] = settings;

			NoiseBasedChunkGenerator delegate = new NoiseBasedChunkGenerator(biomeSource, Holder.direct(settings));
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

	public int heightOffset() {
		return heightOffset;
	}

	public OutsideFill outside() {
		return outside;
	}

	/** 높이를 올린 뒤 해당 스테이지의 해수면. */
	public int stageSeaLevel(int stage) {
		return stageSettings[stage].seaLevel();
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

		Map<NoiseGeneratorSettings, RandomState> cache = new IdentityHashMap<>();
		RandomState[] states = new RandomState[delegates.length];
		for (int i = 0; i < states.length; i++) {
			states[i] = cache.computeIfAbsent(stageSettings[i], s -> RandomState.create(s, noises, seed));
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
			fillOutside(chunk);
			return CompletableFuture.completedFuture(chunk);
		}
		return delegates[stage].fillFromNoise(blender, state(stage), structureManager, chunk);
	}

	/** 바깥 청크: 모든 기둥이 같으므로 층 단위로 채운다. */
	private void fillOutside(ChunkAccess chunk) {
		int bottom = chunk.getMinY();
		int topExclusive = Math.min(outside.waterLevel(), bottom + chunk.getHeight());

		for (int y = bottom; y < topExclusive; y++) {
			BlockState state = outside.stateAt(y, bottom);
			if (state.isAir()) continue;
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
			int localY = y & 15;
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					section.setBlockState(x, localY, z, state);
				}
			}
		}

		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		int floorTop = Math.min(outside.floorY(), topExclusive) - 1;
		int surfaceTop = topExclusive - 1;
		if (surfaceTop < bottom) return;
		BlockState floorState = outside.stateAt(Math.max(floorTop, bottom), bottom);
		BlockState surfaceState = outside.stateAt(surfaceTop, bottom);
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (floorTop >= bottom) oceanFloor.update(x, floorTop, z, floorState);
				worldSurface.update(x, surfaceTop, z, surfaceState);
			}
		}
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
		if (stage == StageLayout.NONE) return; // 바깥 바닥은 동굴/협곡 없음
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
		if (stage != StageLayout.NONE) {
			return delegates[stage].getBaseHeight(x, z, type, level, state(stage));
		}

		int bottom = level.getMinY();
		int topExclusive = Math.min(outside.waterLevel(), bottom + level.getHeight());
		Predicate<BlockState> opaque = type.isOpaque();
		for (int y = topExclusive - 1; y >= bottom; y--) {
			if (opaque.test(outside.stateAt(y, bottom))) return y + 1;
		}
		return bottom;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		int stage = layout.stageAt(x, z);
		if (stage != StageLayout.NONE) {
			return delegates[stage].getBaseColumn(x, z, level, state(stage));
		}

		int bottom = level.getMinY();
		int topExclusive = Math.min(outside.waterLevel(), bottom + level.getHeight());
		BlockState[] column = new BlockState[Math.max(0, topExclusive - bottom)];
		for (int i = 0; i < column.length; i++) {
			column[i] = outside.stateAt(bottom + i, bottom);
		}
		return new NoiseColumn(bottom, column);
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
		int stage = layout.stageAt(pos.getX(), pos.getZ());
		if (stage == StageLayout.NONE) {
			info.add("Stage: outside (water level " + outside.waterLevel() + ")");
			return;
		}
		info.add("Stage: " + stage + " (" + stageBiomeSource.stages().get(stage).noiseSettings().getRegisteredName()
				+ ", +" + heightOffset + ", sea " + stageSeaLevel(stage) + ")");
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

	/**
	 * 월드 단위 해수면(물고기 스폰 등)은 바깥 물 높이 기준.
	 * 각 스테이지 지형 안의 바다 높이는 자기 설정(올린 해수면)을 따른다.
	 */
	@Override
	public int getSeaLevel() {
		return outside.waterLevel();
	}
}
