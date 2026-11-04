package io.github.unisun2.towermod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.unisun2.towermod.worldgen.Stage;
import io.github.unisun2.towermod.worldgen.StageBiomeSource;
import io.github.unisun2.towermod.worldgen.StageLayout;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.phys.Vec3;

/**
 * /stage          현재 위치의 스테이지 정보
 * /stage <번호>   해당 스테이지의 범위와 중심 좌표
 */
public final class StageCommand {
	private StageCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("stage")
				.executes(ctx -> current(ctx.getSource()))
				.then(Commands.argument("index", IntegerArgumentType.integer(0))
						.executes(ctx -> describe(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index")))));
	}

	private static int current(CommandSourceStack source) {
		StageBiomeSource biomes = stageSource(source);
		if (biomes == null) return 0;

		Vec3 pos = source.getPosition();
		int x = Mth.floor(pos.x);
		int z = Mth.floor(pos.z);
		StageLayout layout = biomes.layout();
		if (!layout.contains(x, z)) {
			source.sendSuccess(() -> Component.translatable("commands.towermod.stage.outside"), false);
			return 0;
		}
		return describe(source, layout.rawIndexAt(x));
	}

	private static int describe(CommandSourceStack source, int index) {
		StageBiomeSource biomes = stageSource(source);
		if (biomes == null) return 0;

		StageLayout layout = biomes.layout();
		if (index >= layout.count()) {
			source.sendFailure(Component.translatable("commands.towermod.stage.no_such", index, layout.count()));
			return 0;
		}

		Stage stage = biomes.stages().get(index);
		String names = stage.biomes().stream().map(Holder::getRegisteredName).collect(Collectors.joining(", "));
		source.sendSuccess(() -> Component.translatable("commands.towermod.stage.info",
				index, layout.count() - 1, names,
				layout.minX(index), layout.maxX(index) - 1, layout.minZ(), layout.maxZ() - 1,
				layout.centerX(index)), false);
		return index + 1;
	}

	private static StageBiomeSource stageSource(CommandSourceStack source) {
		BiomeSource biomeSource = source.getLevel().getChunkSource().getGenerator().getBiomeSource();
		if (biomeSource instanceof StageBiomeSource stageBiomeSource) {
			return stageBiomeSource;
		}
		source.sendFailure(Component.translatable("commands.towermod.stage.not_stage_world"));
		return null;
	}
}
