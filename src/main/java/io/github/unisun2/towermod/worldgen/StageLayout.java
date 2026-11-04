package io.github.unisun2.towermod.worldgen;

/**
 * 스테이지 배치 계산 (마인크래프트 의존성 없는 순수 로직).
 *
 * <p>스테이지는 X축을 따라 일렬로 놓이고, 스테이지 사이에는 {@code margin} 블록의 빈 공간(마진)이 있다.
 * 0번 스테이지의 중심이 (0, 0)이다.
 * <pre>
 *   period  = size + margin
 *   stage i : X ∈ [i·period − size/2, i·period + size/2),  Z ∈ [−size/2, size/2)
 * </pre>
 * 기본값(size 4000, margin 100청크 = 1600)이면 0번은 X −2000..1999, 마진 2000..3599, 1번은 X 3600..7599 ...
 *
 * <p>size 는 32의 배수, margin 은 16의 배수여야 한다. 그래야 모든 경계가 청크 경계와 일치해서
 * 한 청크는 항상 한 스테이지(또는 마진)에만 속한다.
 */
public final class StageLayout {
	/** 스테이지 밖(마진 또는 전체 영역 밖). */
	public static final int NONE = -1;

	private final int size;
	private final int half;
	private final int margin;
	private final int period;
	private final int count;

	public StageLayout(int size, int margin, int count) {
		if (size <= 0 || size % 32 != 0) throw new IllegalArgumentException("size must be a positive multiple of 32: " + size);
		if (margin < 0 || margin % 16 != 0) throw new IllegalArgumentException("margin must be a non-negative multiple of 16: " + margin);
		if (count <= 0) throw new IllegalArgumentException("count must be positive: " + count);
		this.size = size;
		this.half = size / 2;
		this.margin = margin;
		this.period = size + margin;
		this.count = count;
	}

	public int size() {
		return size;
	}

	public int margin() {
		return margin;
	}

	public int count() {
		return count;
	}

	/** 블록 X 좌표가 속한 "스테이지 + 뒤따르는 마진" 칸의 번호. 범위를 벗어날 수 있다. */
	public int rawIndexAt(int blockX) {
		return Math.floorDiv(blockX + half, period);
	}

	/** 블록 좌표가 속한 스테이지 번호, 마진이거나 영역 밖이면 {@link #NONE}. */
	public int stageAt(int blockX, int blockZ) {
		if (blockZ < minZ() || blockZ >= maxZ()) return NONE;
		int index = rawIndexAt(blockX);
		if (index < 0 || index >= count) return NONE;
		int local = blockX + half - index * period;
		return local < size ? index : NONE;
	}

	public boolean contains(int blockX, int blockZ) {
		return stageAt(blockX, blockZ) != NONE;
	}

	public int minX(int index) {
		return index * period - half;
	}

	/** 배타적 상한. */
	public int maxX(int index) {
		return minX(index) + size;
	}

	public int centerX(int index) {
		return index * period;
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
