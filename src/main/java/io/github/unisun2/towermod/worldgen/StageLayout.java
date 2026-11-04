package io.github.unisun2.towermod.worldgen;

/**
 * 스테이지 배치 계산 (마인크래프트 의존성 없는 순수 로직).
 *
 * <p>스테이지는 X축을 따라 일렬로 놓인다. 각 스테이지는 {@code size × size} 블록이며,
 * 0번 스테이지의 중심이 (0, 0)이다.
 * <pre>
 *   stage i : X ∈ [i·size − size/2, i·size + size/2),  Z ∈ [−size/2, size/2)
 * </pre>
 * 기본값 size = 4000 이면 0번은 X −2000..1999, 1번은 X 2000..5999 ...
 */
public final class StageLayout {
	private final int size;
	private final int half;
	private final int count;

	public StageLayout(int size, int count) {
		if (size <= 0) throw new IllegalArgumentException("size must be positive");
		if (count <= 0) throw new IllegalArgumentException("count must be positive");
		this.size = size;
		this.half = size / 2;
		this.count = count;
	}

	public int size() {
		return size;
	}

	public int count() {
		return count;
	}

	/** 블록 X 좌표가 속한 스테이지 번호. 범위를 벗어나면 음수이거나 {@code count} 이상일 수 있다. */
	public int rawIndexAt(int blockX) {
		return Math.floorDiv(blockX + half, size);
	}

	/** 해당 좌표가 실제 스테이지 영역(4000×4000 칸들의 줄) 안에 있는지. */
	public boolean contains(int blockX, int blockZ) {
		int index = rawIndexAt(blockX);
		return index >= 0 && index < count && blockZ >= minZ() && blockZ < maxZ();
	}

	/** 범위를 벗어난 번호를 가장 가까운 스테이지로 고정. */
	public int clampIndex(int index) {
		return Math.max(0, Math.min(count - 1, index));
	}

	public int minX(int index) {
		return index * size - half;
	}

	/** 배타적 상한. */
	public int maxX(int index) {
		return minX(index) + size;
	}

	public int centerX(int index) {
		return minX(index) + half;
	}

	public int minZ() {
		return -half;
	}

	/** 배타적 상한. */
	public int maxZ() {
		return size - half;
	}

	/**
	 * 스테이지 안에서 두 바이옴을 나누는 2D 값 노이즈. 결과는 대략 [-1, 1]이며 평균 0이라
	 * 임계값 0으로 나누면 두 바이옴이 비슷한 넓이를 차지한다.
	 *
	 * @param scale 큰 덩어리의 크기(블록). 클수록 바이옴 덩어리가 커진다.
	 */
	public static double splitNoise(int stageIndex, int blockX, int blockZ, int scale) {
		double coarse = valueNoise(stageIndex, blockX, blockZ, scale, 0x5EEDL);
		double fine = valueNoise(stageIndex, blockX, blockZ, Math.max(1, scale / 3), 0xB10DL);
		return (coarse + 0.35 * fine) / 1.35;
	}

	private static double valueNoise(int stageIndex, int x, int z, int scale, long salt) {
		int cx = Math.floorDiv(x, scale);
		int cz = Math.floorDiv(z, scale);
		double fx = smooth((x - (double) cx * scale) / scale);
		double fz = smooth((z - (double) cz * scale) / scale);

		double v00 = lattice(stageIndex, cx, cz, salt);
		double v10 = lattice(stageIndex, cx + 1, cz, salt);
		double v01 = lattice(stageIndex, cx, cz + 1, salt);
		double v11 = lattice(stageIndex, cx + 1, cz + 1, salt);

		double a = v00 + (v10 - v00) * fx;
		double b = v01 + (v11 - v01) * fx;
		return a + (b - a) * fz;
	}

	private static double smooth(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	/** 격자점마다 [-1, 1] 의사난수. */
	private static double lattice(int stageIndex, int cx, int cz, long salt) {
		long h = salt;
		h = mix(h ^ stageIndex);
		h = mix(h ^ cx);
		h = mix(h ^ cz);
		return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
	}

	/** SplitMix64 finalizer. */
	private static long mix(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
