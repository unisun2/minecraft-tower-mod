package io.github.unisun2.towermod.worldgen;

/**
 * 스테이지 구성용 바이옴 분류. 실제 분류는 바이옴 태그 {@code towermod:stage/<이름>}으로 정한다
 * ({@link BiomeCategories}). 여러 태그에 들어 있으면 선언 순서가 앞선 쪽이 우선이다.
 */
public enum StageCategory {
	/** 스테이지에 쓰지 않음. */
	EXCLUDED("excluded"),
	NETHER("nether"),
	END("end"),
	/** 바다. 항상 해안 하나와 짝을 이룬다. */
	OCEAN("ocean"),
	/** 해안. 바다의 짝으로만 쓰이며 여러 번 쓸 수 있다. */
	COAST("coast"),
	/** 나머지 오버월드 바이옴. */
	LAND("land");

	private final String tagName;

	StageCategory(String tagName) {
		this.tagName = tagName;
	}

	public String tagName() {
		return tagName;
	}

	/** 이 분류의 바이옴이 여러 스테이지에 중복으로 쓰여도 되는지. */
	public boolean reusable() {
		return this == COAST;
	}

	/** 두 바이옴이 한 스테이지를 이룰 수 있는지. */
	public static boolean canPair(StageCategory a, StageCategory b) {
		if (a == EXCLUDED || b == EXCLUDED) return false;
		if (a == OCEAN || b == OCEAN) return (a == OCEAN && b == COAST) || (a == COAST && b == OCEAN);
		if (a == COAST || b == COAST) return false;
		return a == b;
	}

	/** 바이옴 하나만으로 스테이지가 될 수 있는지. */
	public boolean canStandAlone() {
		return this == LAND || this == NETHER || this == END;
	}
}
