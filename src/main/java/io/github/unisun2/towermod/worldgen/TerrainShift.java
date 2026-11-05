package io.github.unisun2.towermod.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.unisun2.towermod.TowerMod;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * 노이즈 설정의 지형을 통째로 위/아래로 옮긴다.
 *
 * <p>바닐라 지형에서 "높이"를 정하는 건 {@code y_clamped_gradient}들(지표 깊이 기울기, 위/아래 슬라이드)이고,
 * 나머지 노이즈는 Y 기준점이 없다. 그래서 라우터 안의 모든 {@code y_clamped_gradient}를 dy만큼 옮기면
 * 지형 모양은 그대로 dy만큼 올라간다. 해수면과 지표 규칙의 절대 높이 조건({@code y_above})도 같이 옮긴다.
 * 기반암·심층암 경계(vertical_gradient)는 월드 바닥 기준이므로 건드리지 않는다.
 */
final class TerrainShift {
	private static final Identifier Y_CLAMPED_GRADIENT = Identifier.withDefaultNamespace("y_clamped_gradient");
	private static final String Y_ABOVE = "minecraft:y_above";

	private TerrainShift() {
	}

	/**
	 * @param terrainDy 지형(밀도 기울기, 지표 규칙 높이)을 옮길 양
	 * @param seaDy     해수면을 옮길 양
	 */
	static NoiseGeneratorSettings shift(NoiseGeneratorSettings settings, int terrainDy, int seaDy) {
		if (terrainDy == 0 && seaDy == 0) return settings;

		NoiseRouter router = terrainDy == 0 ? settings.noiseRouter()
				: settings.noiseRouter().mapAll(function -> shiftGradient(function, terrainDy));
		SurfaceRules.RuleSource surface = terrainDy == 0 ? settings.surfaceRule()
				: shiftSurfaceRule(settings.surfaceRule(), terrainDy);
		// 해수면이 생성 바닥 이하이면(end, floating_islands 등) "바다 없음"이라는 뜻이라 그대로 둔다.
		int seaLevel = settings.seaLevel() > settings.noiseSettings().minY() ? settings.seaLevel() + seaDy : settings.seaLevel();

		// 지형을 올리면 생성 범위 위쪽도 늘려서 천장/윗면이 잘리지 않게 한다 (월드 높이로 다시 잘림).
		NoiseSettings noise = settings.noiseSettings();
		int extra = Math.max(0, Math.max(terrainDy, seaDy));
		extra = (extra + 15) / 16 * 16;
		if (extra > 0) {
			noise = new NoiseSettings(noise.minY(), noise.height() + extra, noise.noiseSizeHorizontal(), noise.noiseSizeVertical());
		}

		return new NoiseGeneratorSettings(
				noise,
				settings.defaultBlock(),
				settings.defaultFluid(),
				router,
				surface,
				settings.spawnTarget(),
				seaLevel,
				settings.disableMobGeneration(),
				settings.aquifersEnabled(),
				settings.oreVeinsEnabled(),
				settings.useLegacyRandomSource());
	}

	private static DensityFunction shiftGradient(DensityFunction function, int dy) {
		Identifier type = BuiltInRegistries.DENSITY_FUNCTION_TYPE.getKey(function.codec().codec());
		if (!Y_CLAMPED_GRADIENT.equals(type)) return function;

		// YClampedGradient 레코드는 외부에서 접근할 수 없으므로 코덱으로 값을 꺼낸다.
		JsonObject json = DensityFunction.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, function).getOrThrow().getAsJsonObject();
		return DensityFunctions.yClampedGradient(
				json.get("from_y").getAsInt() + dy,
				json.get("to_y").getAsInt() + dy,
				json.get("from_value").getAsDouble(),
				json.get("to_value").getAsDouble());
	}

	private static SurfaceRules.RuleSource shiftSurfaceRule(SurfaceRules.RuleSource rule, int dy) {
		try {
			JsonElement json = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow();
			shiftYAbove(json, dy);
			return SurfaceRules.RuleSource.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		} catch (RuntimeException e) {
			TowerMod.LOGGER.warn("Could not shift surface rule height; keeping the original rule", e);
			return rule;
		}
	}

	/** {"type":"minecraft:y_above","anchor":{"absolute":N}} 의 N을 옮긴다. */
	private static void shiftYAbove(JsonElement element, int dy) {
		if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(child -> shiftYAbove(child, dy));
			return;
		}
		if (!element.isJsonObject()) return;

		JsonObject object = element.getAsJsonObject();
		JsonElement type = object.get("type");
		if (type != null && type.isJsonPrimitive() && Y_ABOVE.equals(type.getAsString())) {
			JsonElement anchor = object.get("anchor");
			if (anchor != null && anchor.isJsonObject() && anchor.getAsJsonObject().has("absolute")) {
				JsonObject a = anchor.getAsJsonObject();
				a.addProperty("absolute", a.get("absolute").getAsInt() + dy);
			}
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			shiftYAbove(entry.getValue(), dy);
		}
	}
}
