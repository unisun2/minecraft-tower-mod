package io.github.unisun2.towermod.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.unisun2.towermod.TowerMod;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
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
import net.minecraft.world.level.biome.Biome;
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
 * 스테이지 월드 생성기.
 *
 * <ul>
 *   <li>스테이지 목록: 직접 쓴 목록, 또는 {@link AutoStages}로 시드에 따라 모든 바이옴을 자동 배정.
 *       어느 쪽이든 {@link StageRules}로 검사한다. 목록은 시드가 정해지는 {@link #createState}에서 확정된다.</li>
 *   <li>스테이지마다 바닐라 {@link NoiseBasedChunkGenerator}를 하나씩 두고, 청크가 속한 스테이지의 생성기로
 *       작업을 넘긴다. 스테이지 경계는 청크 경계와 일치한다.</li>
 *   <li>스테이지 지형은 {@code stage_height_offset}만큼(+ 스테이지별 {@code terrain_offset}) 올려서 만든다.</li>
 *   <li>스테이지 바깥은 {@link OutsideFill}의 바다. 스테이지 안에서도 바닥이 비어 있는 기둥
 *       (엔드 섬, 네더 아래 등)은 같은 바다로 채운다.</li>
 * </ul>
 *
 * <p>바닐라는 {@code NoiseBasedChunkGenerator}가 아닌 생성기에는 더미 {@link RandomState}를 넘기므로,
 * 스테이지별 RandomState는 직접 만든다.
 */
public class StageChunkGenerator extends ChunkGenerator {
	public static final int DEFAULT_STAGE_SIZE = 4000;
	public static final int DEFAULT_MARGIN_CHUNKS = 100;
	public static final int DEFAULT_BLEND_SCALE = 384;
	public static final int DEFAULT_HEIGHT_OFFSET = 32;

	private static final Codec<Either<List<Stage>, AutoStages>> STAGES_CODEC = Codec.either(
			Stage.CODEC.listOf().validate(StageChunkGenerator::nonEmpty),
			AutoStages.CODEC);

	public static final MapCodec<StageChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(64, 1 << 20).validate(StageChunkGenerator::checkStageSize)
					.optionalFieldOf("stage_size", DEFAULT_STAGE_SIZE).forGetter(StageChunkGenerator::stageSize),
			Codec.intRange(0, 1 << 16).optionalFieldOf("margin_chunks", DEFAULT_MARGIN_CHUNKS)
					.forGetter(StageChunkGenerator::marginChunks),
			Codec.intRange(16, 1 << 16).optionalFieldOf("blend_scale", DEFAULT_BLEND_SCALE)
					.forGetter(StageChunkGenerator::blendScale),
			Codec.intRange(-256, 256).optionalFieldOf("stage_height_offset", DEFAULT_HEIGHT_OFFSET)
					.forGetter(StageChunkGenerator::heightOffset),
			Biome.CODEC.fieldOf("margin_biome").forGetter(StageChunkGenerator::marginBiome),
			OutsideFill.CODEC.optionalFieldOf("outside", OutsideFill.defaults()).forGetter(StageChunkGenerator::outside),
			STAGES_CODEC.fieldOf("stages").forGetter(StageChunkGenerator::encodedStages),
			RegistryOps.retrieveGetter(Registries.NOISE),
			RegistryOps.retrieveGetter(Registries.BIOME)
	).apply(instance, StageChunkGenerator::new));

	private final int stageSize;
	private final int marginChunks;
	private final int heightOffset;
	private final OutsideFill outside;
	private final Either<List<Stage>, AutoStages> plan;
	private final HolderGetter<NormalNoise.NoiseParameters> noises;
	private final HolderGetter<Biome> biomes;
	private final StageBiomeSource stageBiomeSource;

	private volatile Resolved resolved;

	/** 시드가 정해진 뒤 확정되는 모든 것. */
	private record Resolved(long seed, List<Stage> stages, StageLayout layout, NoiseGeneratorSettings[] settings,
			NoiseBasedChunkGenerator[] delegates, RandomState[] states, int minY, int genDepth) {
	}

	public StageChunkGenerator(int stageSize, int marginChunks, int blendScale, int heightOffset, Holder<Biome> marginBiome,
			OutsideFill outside, Either<List<Stage>, AutoStages> plan,
			HolderGetter<NormalNoise.NoiseParameters> noises, HolderGetter<Biome> biomes) {
		super(new StageBiomeSource(blendScale, marginBiome));
		this.stageSize = stageSize;
		this.marginChunks = marginChunks;
		this.heightOffset = heightOffset;
		this.outside = outside;
		this.plan = plan;
		this.noises = noises;
		this.biomes = biomes;
		this.stageBiomeSource = (StageBiomeSource) getBiomeSource();
		this.stageBiomeSource.attach(this);
	}

	private static DataResult<Integer> checkStageSize(int size) {
		return size % 32 == 0
				? DataResult.success(size)
				: DataResult.error(() -> "stage_size must be a multiple of 32 (chunk aligned), got " + size);
	}

	private static DataResult<List<Stage>> nonEmpty(List<Stage> stages) {
		return stages.isEmpty() ? DataResult.error(() -> "At least one stage is required") : DataResult.success(stages);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	// ---- 설정 값 ----

	public int stageSize() {
		return stageSize;
	}

	public int marginChunks() {
		return marginChunks;
	}

	public int blendScale() {
		return stageBiomeSource.blendScale();
	}

	public int heightOffset() {
		return heightOffset;
	}

	public Holder<Biome> marginBiome() {
		return stageBiomeSource.marginBiome();
	}

	public OutsideFill outside() {
		return outside;
	}

	public StageBiomeSource stageBiomeSource() {
		return stageBiomeSource;
	}

	/**
	 * 저장할 때는 확정된 스테이지 목록을 그대로 쓴다. 그래서 나중에 바이옴 태그나 모드 구성이 바뀌어도
	 * 이미 만든 월드의 스테이지 배치는 변하지 않는다.
	 */
	private Either<List<Stage>, AutoStages> encodedStages() {
		Resolved current = resolved;
		return current != null ? Either.left(current.stages()) : plan;
	}

	/** 시드가 정해지기 전 possibleBiomes 용 후보 (자동 모드면 태그의 모든 바이옴). */
	Stream<Holder<Biome>> candidateBiomes() {
		return plan.map(
				list -> list.stream().flatMap(stage -> stage.biomes().stream()),
				auto -> {
					try {
						return BiomeCategories.collect(biomes).values().stream().flatMap(List::stream);
					} catch (IllegalStateException unboundTags) {
						return Stream.empty();
					}
				});
	}

	// ---- 확정된 스테이지 ----

	public boolean isResolved() {
		return resolved != null;
	}

	public StageLayout layout() {
		return resolved().layout();
	}

	public List<Stage> stages() {
		return resolved().stages();
	}

	/** 높이를 올린 뒤 해당 스테이지의 해수면. */
	public int stageSeaLevel(int stage) {
		return resolved().settings()[stage].seaLevel();
	}

	private Resolved resolved() {
		Resolved current = resolved;
		if (current == null) {
			throw new IllegalStateException("StageChunkGenerator was used before createState() provided the world seed");
		}
		return current;
	}

	@Override
	public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
		resolve(seed); // possibleBiomes()가 처음 계산되기 전에 스테이지를 확정해야 한다
		return super.createState(structureSets, randomState, seed);
	}

	private synchronized void resolve(long seed) {
		Resolved current = resolved;
		if (current != null && current.seed() == seed) return;

		List<Stage> stages = plan.map(list -> list, auto -> auto.generate(biomes, seed));

		List<String> problems = StageRules.check(
				stages.stream().map(Stage::biomes).toList(), BiomeCategories::of, Holder::getRegisteredName);
		for (String problem : problems) {
			TowerMod.LOGGER.warn("Stage rule violated: {}", problem);
		}

		StageLayout layout = new StageLayout(stageSize, marginChunks * 16, stages.size());

		// 같은 (원본 설정, 지형 이동량)은 한 번만 변환하고, 같은 변환 결과는 RandomState도 공유한다.
		Map<NoiseGeneratorSettings, Map<Integer, NoiseGeneratorSettings>> shiftCache = new IdentityHashMap<>();
		Map<NoiseGeneratorSettings, RandomState> stateCache = new IdentityHashMap<>();
		NoiseGeneratorSettings[] settings = new NoiseGeneratorSettings[stages.size()];
		NoiseBasedChunkGenerator[] delegates = new NoiseBasedChunkGenerator[stages.size()];
		RandomState[] states = new RandomState[stages.size()];
		int bottom = Integer.MAX_VALUE;
		int top = Integer.MIN_VALUE;
		for (int i = 0; i < stages.size(); i++) {
			Stage stage = stages.get(i);
			int terrainDy = heightOffset + stage.terrainOffset();
			settings[i] = shiftCache
					.computeIfAbsent(stage.noiseSettings().value(), s -> new HashMap<>())
					.computeIfAbsent(terrainDy, dy -> TerrainShift.shift(stage.noiseSettings().value(), dy, heightOffset));
			delegates[i] = new NoiseBasedChunkGenerator(stageBiomeSource, Holder.direct(settings[i]));
			states[i] = stateCache.computeIfAbsent(settings[i], s -> RandomState.create(s, noises, seed));
			bottom = Math.min(bottom, delegates[i].getMinY());
			top = Math.max(top, delegates[i].getMinY() + delegates[i].getGenDepth());
		}

		stageBiomeSource.bind(layout, stages);
		resolved = new Resolved(seed, List.copyOf(stages), layout, settings, delegates, states, bottom, top - bottom);
		TowerMod.LOGGER.info("Stage world: {} stages{}", stages.size(), problems.isEmpty() ? "" : " (" + problems.size() + " rule warnings)");
	}

	private static int stageOf(Resolved r, ChunkPos pos) {
		return r.layout().stageAt(pos.getMinBlockX(), pos.getMinBlockZ());
	}

	// ---- 청크 생성 단계 ----

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		Resolved r = resolved();
		int stage = stageOf(r, chunk.getPos());
		if (stage == StageLayout.NONE) {
			fillOutside(chunk);
			return CompletableFuture.completedFuture(chunk);
		}
		return r.delegates()[stage].fillFromNoise(blender, r.states()[stage], structureManager, chunk)
				.thenApply(filled -> {
					fillUnderTerrain(filled);
					return filled;
				});
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

	/**
	 * 스테이지 청크에서 월드 바닥부터 처음 블록이 나올 때까지의 빈 공간을 바깥 바다로 채운다.
	 * 보통 지형은 맨 아래가 막혀 있어 아무 일도 없고, 엔드 섬처럼 떠 있는 지형은 바다 위로 솟게 된다.
	 */
	private void fillUnderTerrain(ChunkAccess chunk) {
		int bottom = chunk.getMinY();
		int topExclusive = Math.min(outside.waterLevel(), bottom + chunk.getHeight());
		Heightmap oceanFloor = null;
		Heightmap worldSurface = null;

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = bottom; y < topExclusive; y++) {
					LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
					int localY = y & 15;
					if (!section.getBlockState(x, localY, z).isAir()) break;
					BlockState state = outside.stateAt(y, bottom);
					if (state.isAir()) break;
					section.setBlockState(x, localY, z, state);

					if (oceanFloor == null) {
						oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
						worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
					}
					oceanFloor.update(x, y, z, state);
					worldSurface.update(x, y, z, state);
				}
			}
		}
	}

	@Override
	public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
		Resolved r = resolved();
		int stage = stageOf(r, chunk.getPos());
		if (stage == StageLayout.NONE) return;
		r.delegates()[stage].buildSurface(level, structureManager, r.states()[stage], chunk);
	}

	@Override
	public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState, BiomeManager biomeManager,
			StructureManager structureManager, ChunkAccess chunk) {
		Resolved r = resolved();
		int stage = stageOf(r, chunk.getPos());
		if (stage == StageLayout.NONE) return; // 바깥 바닥은 동굴/협곡 없음
		r.delegates()[stage].applyCarvers(level, seed, r.states()[stage], biomeManager, structureManager, chunk);
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion level) {
		Resolved r = resolved();
		int stage = stageOf(r, level.getCenter());
		if (stage == StageLayout.NONE) return;
		r.delegates()[stage].spawnOriginalMobs(level);
	}

	// ---- 높이 조회 (구조물 배치, 스폰 등) ----

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		Resolved r = resolved();
		int stage = r.layout().stageAt(x, z);
		if (stage != StageLayout.NONE) {
			return r.delegates()[stage].getBaseHeight(x, z, type, level, r.states()[stage]);
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
		Resolved r = resolved();
		int stage = r.layout().stageAt(x, z);
		if (stage != StageLayout.NONE) {
			return r.delegates()[stage].getBaseColumn(x, z, level, r.states()[stage]);
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
		Resolved r = resolved;
		if (r == null) return;
		int stage = r.layout().stageAt(pos.getX(), pos.getZ());
		if (stage == StageLayout.NONE) {
			info.add("Stage: outside (water level " + outside.waterLevel() + ")");
			return;
		}
		Stage s = r.stages().get(stage);
		info.add("Stage: " + stage + " (" + s.noiseSettings().getRegisteredName()
				+ ", terrain +" + (heightOffset + s.terrainOffset()) + ", sea " + r.settings()[stage].seaLevel() + ")");
		r.delegates()[stage].addDebugScreenInfo(info, r.states()[stage], pos);
	}

	// ---- 월드 전체 값 ----

	@Override
	public int getGenDepth() {
		Resolved r = resolved;
		return r != null ? r.genDepth() : 384;
	}

	@Override
	public int getMinY() {
		Resolved r = resolved;
		return r != null ? r.minY() : -64;
	}

	/**
	 * 월드 단위 해수면(물고기 스폰 등)은 바깥 수면 기준.
	 * 각 스테이지 지형 안의 바다 높이는 자기 설정(올린 해수면)을 따른다.
	 */
	@Override
	public int getSeaLevel() {
		return outside.waterLevel();
	}
}
