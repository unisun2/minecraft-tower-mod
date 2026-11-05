package io.github.unisun2.towermod.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 스테이지 구성 규칙 검사 (마인크래프트 의존성 없는 순수 로직).
 * <ul>
 *   <li>해안이 아닌 바이옴은 한 번만 쓸 수 있다.</li>
 *   <li>네더는 네더끼리, 엔드는 엔드끼리, 육지는 육지끼리.</li>
 *   <li>바다는 해안과 한 세트여야 하고, 해안은 바다의 짝으로만 쓴다.</li>
 * </ul>
 */
public final class StageRules {
	private StageRules() {
	}

	/** 규칙 위반 목록 (비어 있으면 통과). */
	public static <T> List<String> check(List<List<T>> stages, Function<T, StageCategory> category, Function<T, String> name) {
		List<String> problems = new ArrayList<>();
		Map<String, Integer> firstUse = new HashMap<>();

		for (int i = 0; i < stages.size(); i++) {
			List<T> biomes = stages.get(i);
			for (T biome : biomes) {
				StageCategory c = category.apply(biome);
				if (c == StageCategory.EXCLUDED) {
					problems.add("stage " + i + ": " + name.apply(biome) + " is excluded from stages");
				}
				if (!c.reusable()) {
					Integer previous = firstUse.putIfAbsent(name.apply(biome), i);
					if (previous != null) {
						problems.add("stage " + i + ": " + name.apply(biome) + " is already used by stage " + previous);
					}
				}
			}

			if (biomes.size() == 1) {
				StageCategory c = category.apply(biomes.getFirst());
				if (!c.canStandAlone()) {
					problems.add("stage " + i + ": " + name.apply(biomes.getFirst()) + " (" + c.tagName() + ") cannot be a stage on its own"
							+ (c == StageCategory.OCEAN ? " - pair it with a coast" : ""));
				}
			} else if (biomes.size() == 2) {
				StageCategory a = category.apply(biomes.get(0));
				StageCategory b = category.apply(biomes.get(1));
				if (!StageCategory.canPair(a, b)) {
					problems.add("stage " + i + ": " + name.apply(biomes.get(0)) + " (" + a.tagName() + ") and "
							+ name.apply(biomes.get(1)) + " (" + b.tagName() + ") cannot share a stage");
				}
			}
		}
		return problems;
	}
}
