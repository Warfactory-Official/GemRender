package com.wf.gemrender.client;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class VoidWorld {
	private VoidWorld() {
	}

	public static boolean exists(Minecraft mc, String name) {
		try {
			return mc.getLevelSource()
					.levelExists(name);
		} catch (Exception failed) {
			return false;
		}
	}

	public static void create(Minecraft mc, String name) {
		WorldOptions options = new WorldOptions(0L, false, false);

		//? if >=26.1 {
		/*LevelSettings settings = new LevelSettings(name, GameType.SPECTATOR,
				new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
				WorldDataConfiguration.DEFAULT);
*///?} else {
		LevelSettings settings = new LevelSettings(name, GameType.SPECTATOR, false, Difficulty.PEACEFUL,
				true, new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
		//?}

		//? if >=26.1 {
		/*Function<HolderLookup.Provider, WorldDimensions> dimensions = access -> {
*///?} else {
		Function<net.minecraft.core.RegistryAccess, WorldDimensions> dimensions = access -> {
		//?}
			HolderGetter<Biome> biomes = access.lookupOrThrow(Registries.BIOME);
			FlatLevelGeneratorSettings flat = new FlatLevelGeneratorSettings(Optional.empty(),
					biomes.getOrThrow(Biomes.THE_VOID), List.of());
			flat.updateLayers();
			return WorldPresets.createNormalWorldDimensions(access)
					.replaceOverworldGenerator(access, new FlatLevelSource(flat));
		};

		//? if >=1.21 {
		mc.createWorldOpenFlows()
				.createFreshLevel(name, settings, options, dimensions, mc.screen);
		//?} else {
		/*mc.createWorldOpenFlows()
				.createFreshLevel(name, settings, options, dimensions);
*///?}
	}
}
