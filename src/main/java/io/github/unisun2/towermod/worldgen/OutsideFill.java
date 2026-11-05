package io.github.unisun2.towermod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 스테이지 바깥(마진 + 영역 밖)을 채우는 방식. 아래에서부터:
 * <pre>
 *   minY                         : 기반암
 *   minY+1 .. floor_y-4          : block      (기본 석재)
 *   floor_y-3 .. floor_y-1       : top_block  (기본 모래, 3칸)
 *   floor_y .. water_level-1     : fluid      (기본 물)
 *   water_level ..               : 공기
 * </pre>
 * {@code water_level}은 바닐라 해수면과 같은 의미다 (63이면 물 맨 윗칸은 y=62).
 */
public record OutsideFill(int waterLevel, int floorY, BlockState block, BlockState topBlock, BlockState fluid) {
	public static final int TOP_DEPTH = 3;
	public static final int DEFAULT_WATER_LEVEL = 88;
	public static final int DEFAULT_FLOOR_Y = 56;

	public static final Codec<OutsideFill> CODEC = RecordCodecBuilder.<OutsideFill>create(instance -> instance.group(
			Codec.INT.optionalFieldOf("water_level", DEFAULT_WATER_LEVEL).forGetter(OutsideFill::waterLevel),
			Codec.INT.optionalFieldOf("floor_y", DEFAULT_FLOOR_Y).forGetter(OutsideFill::floorY),
			BlockState.CODEC.optionalFieldOf("block", Blocks.STONE.defaultBlockState()).forGetter(OutsideFill::block),
			BlockState.CODEC.optionalFieldOf("top_block", Blocks.SAND.defaultBlockState()).forGetter(OutsideFill::topBlock),
			BlockState.CODEC.optionalFieldOf("fluid", Blocks.WATER.defaultBlockState()).forGetter(OutsideFill::fluid)
	).apply(instance, OutsideFill::new)).validate(OutsideFill::check);

	public static OutsideFill defaults() {
		return new OutsideFill(DEFAULT_WATER_LEVEL, DEFAULT_FLOOR_Y,
				Blocks.STONE.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.WATER.defaultBlockState());
	}

	private static DataResult<OutsideFill> check(OutsideFill fill) {
		return fill.floorY() <= fill.waterLevel()
				? DataResult.success(fill)
				: DataResult.error(() -> "outside.floor_y (" + fill.floorY() + ") must not be above outside.water_level (" + fill.waterLevel() + ")");
	}

	/** 바깥 칸의 y 위치 블록. {@code minY}는 월드 최저 높이. */
	public BlockState stateAt(int y, int minY) {
		if (y == minY) return Blocks.BEDROCK.defaultBlockState();
		if (y < floorY - TOP_DEPTH) return block;
		if (y < floorY) return topBlock;
		if (y < waterLevel) return fluid;
		return Blocks.AIR.defaultBlockState();
	}
}
