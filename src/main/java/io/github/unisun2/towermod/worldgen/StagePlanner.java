package io.github.unisun2.towermod.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * 분류된 바이옴들로 스테이지 목록을 만든다 (마인크래프트 의존성 없는 순수 로직).
 *
 * <ul>
 *   <li>해안을 뺀 모든 바이옴은 정확히 한 번씩 쓰인다.</li>
 *   <li>육지·네더·엔드는 같은 분류끼리 둘씩 짝짓는다 (개수가 홀수면 하나는 단독 스테이지).</li>
 *   <li>바다는 하나씩 해안과 짝짓는다. 해안은 온도가 가까운 것 중에서 고르며 중복될 수 있다.</li>
 *   <li>0번 스테이지(스폰)는 항상 육지 스테이지이고, 나머지 순서는 시드에 따라 섞는다.</li>
 * </ul>
 * 같은 시드와 같은 입력이면 항상 같은 결과가 나온다.
 */
public final class StagePlanner {
	/** 해안 후보: 가장 가까운 온도 차이 + 이 값 이내의 해안 중에서 무작위. */
	static final double COAST_TEMPERATURE_TOLERANCE = 0.35;

	private StagePlanner() {
	}

	public record Planned<T>(List<T> biomes, StageCategory category) {
		public Planned {
			biomes = List.copyOf(biomes);
		}
	}

	/**
	 * @param groups      분류별 바이옴 (각 목록은 이름순 등 결정적인 순서여야 한다)
	 * @param temperature 바이옴 기본 온도 (해안 고르기용)
	 */
	public static <T> List<Planned<T>> plan(Map<StageCategory, List<T>> groups, ToDoubleFunction<T> temperature, long seed) {
		Random random = new Random(seed ^ 0x7A3E_5EED_51A6_E5L);

		List<Planned<T>> land = pairUp(shuffled(groups, StageCategory.LAND, random), StageCategory.LAND);
		List<Planned<T>> nether = pairUp(shuffled(groups, StageCategory.NETHER, random), StageCategory.NETHER);
		List<Planned<T>> end = pairUp(shuffled(groups, StageCategory.END, random), StageCategory.END);
		List<T> coasts = groups.getOrDefault(StageCategory.COAST, List.of());
		List<Planned<T>> ocean = new ArrayList<>();
		for (T sea : shuffled(groups, StageCategory.OCEAN, random)) {
			ocean.add(coasts.isEmpty()
					? new Planned<>(List.of(sea), StageCategory.OCEAN)
					: new Planned<>(List.of(sea, pickCoast(sea, coasts, temperature, random)), StageCategory.OCEAN));
		}

		List<Planned<T>> result = new ArrayList<>();
		if (!land.isEmpty()) {
			result.add(land.removeFirst());
		}
		List<Planned<T>> rest = new ArrayList<>();
		rest.addAll(land);
		rest.addAll(ocean);
		rest.addAll(nether);
		rest.addAll(end);
		Collections.shuffle(rest, random);
		result.addAll(rest);
		return result;
	}

	private static <T> List<T> shuffled(Map<StageCategory, List<T>> groups, StageCategory category, Random random) {
		List<T> list = new ArrayList<>(groups.getOrDefault(category, List.of()));
		Collections.shuffle(list, random);
		return list;
	}

	private static <T> List<Planned<T>> pairUp(List<T> biomes, StageCategory category) {
		List<Planned<T>> stages = new ArrayList<>();
		for (int i = 0; i < biomes.size(); i += 2) {
			stages.add(new Planned<>(biomes.subList(i, Math.min(i + 2, biomes.size())), category));
		}
		return stages;
	}

	static <T> T pickCoast(T sea, List<T> coasts, ToDoubleFunction<T> temperature, Random random) {
		double seaTemperature = temperature.applyAsDouble(sea);
		double best = Double.MAX_VALUE;
		for (T coast : coasts) {
			best = Math.min(best, Math.abs(temperature.applyAsDouble(coast) - seaTemperature));
		}
		List<T> near = new ArrayList<>();
		for (T coast : coasts) {
			if (Math.abs(temperature.applyAsDouble(coast) - seaTemperature) <= best + COAST_TEMPERATURE_TOLERANCE) {
				near.add(coast);
			}
		}
		return near.get(random.nextInt(near.size()));
	}
}
