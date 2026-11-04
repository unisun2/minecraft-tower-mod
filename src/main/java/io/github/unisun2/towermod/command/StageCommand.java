package io.github.unisun2.towermod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.unisun2.towermod.worldgen.Stage;
import io.github.unisun2.towermod.worldgen.StageChunkGenerator;
import io.github.unisun2.towermod.worldgen.StageLayout;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;

/**
 * /stage          현재 위치의 스테이지 정보
 * /stage <번호>   해당 스테이지의 범위, 중심 좌표, 바이옴, 지형 설정
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
		StageChunkGenerator generator = stageGenerator(source);
		if (generator == null) return 0;

		Vec3 pos = source.getPosition();
		int stage = generator.layout().stageAt(Mth.floor(pos.x), Mth.floor(pos.z));
		if (stage == StageLayout.NONE) {
			source.sendSuccess(() -> Component.translatable("commands.towermod.stage.outside"), false);
			return 0;
		}
		return describe(source, stage);
	}

	private static int describe(CommandSourceStack source, int index) {
		StageChunkGenerator generator = stageGenerator(source);
		if (generator == null) return 0;

		StageLayout layout = generator.layout();
		if (index >= layout.count()) {
			source.sendFailure(Component.translatable("commands.towermod.stage.no_such", index, layout.count()));
			return 0;
		}

		Stage stage = generator.stageBiomeSource().stages().get(index);
		String biomes = stage.biomes().stream().map(Holder::getRegisteredName).collect(Collectors.joining(", "));
		String terrain = stage.noiseSettings().getRegisteredName();
		source.sendSuccess(() -> Component.translatable("commands.towermod.stage.info",
				index, layout.count() - 1, biomes, terrain,
				layout.minX(index), layout.maxX(index) - 1, layout.minZ(), layout.maxZ() - 1,
				layout.centerX(index)), false);
		return index + 1;
	}

	private static StageChunkGenerator stageGenerator(CommandSourceStack source) {
		ChunkGenerator generator = source.getLevel().getChunkSource().getGenerator();
		if (generator instanceof StageChunkGenerator stageGenerator) {
			return stageGenerator;
		}
		source.sendFailure(Component.translatable("commands.towermod.stage.not_stage_world"));
		return null;
	}
}
