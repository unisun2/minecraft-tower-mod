package io.github.unisun2.towermod;

import io.github.unisun2.towermod.command.StageCommand;
import io.github.unisun2.towermod.worldgen.StageBiomeSource;
import io.github.unisun2.towermod.worldgen.StageChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TowerMod implements ModInitializer {
	public static final String MOD_ID = "towermod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.BIOME_SOURCE, id("stages"), StageBiomeSource.CODEC);
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("stages"), StageChunkGenerator.CODEC);
		StageCommand.register();
		LOGGER.info("Tower mod: stage world generation registered");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
