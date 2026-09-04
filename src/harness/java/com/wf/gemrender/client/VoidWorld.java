package com.wf.gemrender.client;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
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
		LevelSettings settings = new LevelSettings(name, GameType.SPECTATOR, false, Difficulty.PEACEFUL,
				true, new GameRules(), WorldDataConfiguration.DEFAULT);

		WorldOptions options = new WorldOptions(0L, false, false);

		Function<RegistryAccess, WorldDimensions> dimensions = access -> {
			HolderGetter<Biome> biomes = access.lookupOrThrow(Registries.BIOME);
			FlatLevelGeneratorSettings flat = new FlatLevelGeneratorSettings(Optional.empty(),
					biomes.getOrThrow(Biomes.THE_VOID), List.of());
			flat.updateLayers();
			return WorldPresets.createNormalWorldDimensions(access)
					.replaceOverworldGenerator(access, new FlatLevelSource(flat));
		};

		mc.createWorldOpenFlows()
				.createFreshLevel(name, settings, options, dimensions, mc.screen);
	}
}
