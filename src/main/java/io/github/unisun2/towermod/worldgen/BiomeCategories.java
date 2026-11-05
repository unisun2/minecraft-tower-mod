package io.github.unisun2.towermod.worldgen;

import io.github.unisun2.towermod.TowerMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * 바이옴 태그 {@code towermod:stage/<분류>}로 바이옴을 {@link StageCategory}로 나눈다.
 * 태그는 월드 로딩이 끝난 뒤(태그 바인딩 이후)에만 읽을 수 있다.
 */
public final class BiomeCategories {
	private static final Map<StageCategory, TagKey<Biome>> TAGS = new EnumMap<>(StageCategory.class);

	static {
		for (StageCategory category : StageCategory.values()) {
			TAGS.put(category, TagKey.create(Registries.BIOME, TowerMod.id("stage/" + category.tagName())));
		}
	}

	private BiomeCategories() {
	}

	public static TagKey<Biome> tag(StageCategory category) {
		return TAGS.get(category);
	}

	/** 태그 우선순위(enum 선언 순서)대로 분류. 어느 태그에도 없으면 EXCLUDED. */
	public static StageCategory of(Holder<Biome> biome) {
		for (StageCategory category : StageCategory.values()) {
			if (biome.is(TAGS.get(category))) return category;
		}
		return StageCategory.EXCLUDED;
	}

	/** 분류 태그에 들어 있는 모든 바이옴을 분류별로, 이름순으로. EXCLUDED는 포함하지 않는다. */
	public static Map<StageCategory, List<Holder<Biome>>> collect(HolderGetter<Biome> biomes) {
		Map<StageCategory, List<Holder<Biome>>> groups = new EnumMap<>(StageCategory.class);
		Set<String> seen = new HashSet<>();
		for (StageCategory category : StageCategory.values()) {
			if (category == StageCategory.EXCLUDED) continue;
			biomes.get(TAGS.get(category)).ifPresent(set -> set.stream().forEach(biome -> {
				StageCategory actual = of(biome);
				if (actual != StageCategory.EXCLUDED && seen.add(biome.getRegisteredName())) {
					groups.computeIfAbsent(actual, c -> new ArrayList<>()).add(biome);
				}
			}));
		}
		groups.values().forEach(list -> list.sort(Comparator.comparing(Holder::getRegisteredName)));
		return groups;
	}
}
