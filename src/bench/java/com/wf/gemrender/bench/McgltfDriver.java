package com.wf.gemrender.bench;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.modularmods.mcgltf.IGltfModelReceiver;
import com.modularmods.mcgltf.MCglTF;
import com.modularmods.mcgltf.RenderedGltfModel;
import com.modularmods.mcgltf.RenderedGltfScene;
import com.modularmods.mcgltf.animation.GltfAnimationCreator;
import com.modularmods.mcgltf.animation.InterpolatedChannel;
import com.wf.gemrender.GemRender;

import de.javagl.jgltf.model.AnimationModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class McgltfDriver implements IGltfModelReceiver, BenchDriver {
	private static final int LIGHT_U = 240;
	private static final int LIGHT_V = 240;
	private static final int OVERLAY_U = 0;
	private static final int OVERLAY_V = 0;

	private final int count;

	private RenderedGltfScene scene;
	private List<InterpolatedChannel> channels = List.of();

	public McgltfDriver(int count) {
		this.count = count;
	}

	@Override
	public String name() {
		return "mcgltf";
	}

	public void register() {
		MCglTF.getInstance().addGltfModelReceiver(this);
	}

	@Override
	public ResourceLocation getModelLocation() {
		return BenchGrid.asset();
	}

	@Override
	public void onReceiveSharedModel(RenderedGltfModel renderedModel) {
		if (renderedModel.renderedGltfScenes.isEmpty()) {
			GemRender.LOGGER.error("Bench: MCglTF returned a model with no scenes.");
			return;
		}
		this.scene = renderedModel.renderedGltfScenes.get(0);

		List<InterpolatedChannel> built = new ArrayList<>();
		for (AnimationModel animation : renderedModel.gltfModel.getAnimationModels()) {
			built.addAll(GltfAnimationCreator.createGltfAnimation(animation));
		}
		this.channels = built;

		GemRender.LOGGER.info("Bench: MCglTF loaded {} with {} scenes, {} animation channels.",
				BenchGrid.asset(), renderedModel.renderedGltfScenes.size(), built.size());
	}

	@Override
	public boolean load() {
		return scene != null;
	}

	@Override
	public void render(Matrix4f modelView, Matrix4f unused, float animationSeconds) {
		if (scene == null) {
			return;
		}

		float spacing = BenchGrid.spacing();
		float clip = BenchGrid.clipSeconds();
		int stride = BenchGrid.stride(count);
		Matrix4f pose = new Matrix4f();
		Matrix3f normal = new Matrix3f();

		Minecraft mc = Minecraft.getInstance();
		mc.gameRenderer.lightTexture().turnOnLightLayer();

		GL13.glActiveTexture(GL13.GL_TEXTURE2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, MCglTF.getInstance().getLightTexture().getId());
		GL13.glActiveTexture(GL13.GL_TEXTURE0);

		GL20.glVertexAttrib4f(RenderedGltfModel.vaColor, 1.0f, 1.0f, 1.0f, 1.0f);
		GL30.glVertexAttribI2i(RenderedGltfModel.vaUV1, OVERLAY_U, OVERLAY_V);
		GL30.glVertexAttribI2i(RenderedGltfModel.vaUV2, LIGHT_U, LIGHT_V);

		for (int i = 0; i < count; i++) {
			float t = animationSeconds + BenchGrid.phaseOffset(i, clip);
			for (InterpolatedChannel channel : channels) {
				channel.update(t % clip);
			}

			pose.set(modelView).translate((i % stride) * spacing, 0.0f, (i / stride) * spacing);
			pose.normal(normal);

			RenderedGltfModel.setCurrentPose(pose);
			RenderedGltfModel.setCurrentNormal(normal);
			scene.renderForVanilla();
		}

		mc.gameRenderer.lightTexture().turnOffLightLayer();
	}

	@Override
	public void close() {
		MCglTF.getInstance().removeGltfModelReceiver(this);
	}
}
