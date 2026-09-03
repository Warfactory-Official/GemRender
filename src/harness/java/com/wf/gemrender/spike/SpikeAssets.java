package com.wf.gemrender.spike;

import org.jetbrains.annotations.Nullable;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GemRenderPartsModel;

import net.minecraft.resources.ResourceLocation;

public final class SpikeAssets {
	public static final ResourceLocation RADAR = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/radar/radar.gltf");

	public static final ResourceLocation RIG = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/rig/rig.glb");

	public static final ResourceLocation MORPH = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/morph/morph.glb");

	public static final ResourceLocation GLASS = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/glass/glass.glb");

	public static final ResourceLocation PBR = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/pbr/pbr.glb");

	public static final ResourceLocation PYLON = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/pylon/pylon.geo.json");

	public static final ResourceLocation PYLON_GLTF = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/pylon/pylon.gltf");

	public static ResourceLocation vehicle(String name) {
		return ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID,
				"models/vehicles/" + name + "/" + name + ".geo.json");
	}

	private static final ModelCache.Handle<?>[] DECLARED = {
			GemRenderModels.handle(RADAR), GemRenderModels.handle(RIG), GemRenderModels.handle(MORPH),
			GemRenderModels.handle(GLASS), GemRenderModels.handle(PBR), GemRenderModels.handle(PYLON),
			GemRenderModels.handle(PYLON_GLTF) };

	private SpikeAssets() {
	}

	@Nullable
	public static GemRenderGltfModel model(ResourceLocation asset) {
		return GemRenderModels.get(asset);
	}

	@Nullable
	public static GemRenderPartsModel parts(ResourceLocation asset) {
		return GemRenderModels.parts(asset);
	}
}
