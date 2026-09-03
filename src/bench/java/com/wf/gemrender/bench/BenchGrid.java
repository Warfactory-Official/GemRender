package com.wf.gemrender.bench;

import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.spike.GltfVisual;
import com.wf.gemrender.spike.SpikeAssets;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class BenchGrid {
	public static final int ALTITUDE = 60;

	private static final String ASSET = System.getProperty("gemrender.bench.asset", "radar");

	private BenchGrid() {
	}

	public static ResourceLocation asset() {
		return switch (ASSET) {
			case "pylon" -> SpikeAssets.PYLON;
			case "pylongltf" -> SpikeAssets.PYLON_GLTF;
			default -> SpikeAssets.RADAR;
		};
	}

	public static String assetName() {
		return ASSET;
	}

	public static boolean isGltf() {
		return !"pylon".equals(ASSET);
	}

	public static boolean isPylon() {
		return ASSET.startsWith("pylon");
	}

	public static float clipSeconds() {
		return isPylon() ? 4.0f : 16.5f;
	}

	public static BlockPos origin(Level level) {
		return level.getSharedSpawnPos().offset(3, ALTITUDE, 3);
	}

	public static GemRenderGltfModel model() {
		return SpikeAssets.model(asset());
	}

	public static float spacing() {
		return GltfVisual.spacing(model());
	}

	public static int stride(int count) {
		return GltfVisual.stride(count);
	}

	public static float offsetX(int i, int count) {
		return (i % stride(count)) * spacing();
	}

	public static float offsetZ(int i, int count) {
		return (i / stride(count)) * spacing();
	}

	public static float phaseOffset(int i, float clipSeconds) {
		return clipSeconds * ((i * 0.618033988f) % 1.0f);
	}

	public static void requestModel() {
		GemRenderModels.get(asset());
	}
}
