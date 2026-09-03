package com.wf.gemrender.bench;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import org.joml.Matrix4f;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.bedrock.animation.Animations;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.bedrock.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.bedrock.animation.BedrockModelBoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.bedrock.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.bedrock.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.bedrock.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.resource.GsonUtil;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.ZYXBoneTransformFactory;
import com.maydaymemory.mae.blend.AdditiveBlender;
import com.maydaymemory.mae.blend.SimpleAdditiveBlender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.spike.SpikeAssets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class SbmDriver implements BenchDriver {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/pylon/pylon.png");

	private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);

	private final int count;

	private final AdditiveBlender blender =
			new SimpleAdditiveBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

	private BedrockModel model;
	private BedrockAnimation clip;
	private boolean failed;

	public SbmDriver(int count) {
		this.count = count;
	}

	@Override
	public String name() {
		return "sbm";
	}

	@Override
	public boolean load() {
		if (model != null) {
			return true;
		}
		if (failed) {
			return false;
		}

		try {
			ResourceLocation geometry = SpikeAssets.PYLON;
			BedrockModelPOJO pojo = read(geometry, BedrockModelPOJO.class);
			model = new BedrockModel(pojo);

			BedrockAnimationFile file = read(sibling(geometry, ".animation.json"),
					BedrockAnimationFile.class);
			List<BedrockAnimation> clips = Animations.createAnimation(file,
					new BedrockModelBoneIndexProvider(model));
			if (clips.isEmpty()) {
				throw new IllegalStateException("no clips in the animation file");
			}
			clip = clips.get(0);

			GemRender.LOGGER.info("Bench: SBM loaded {} with {} bones and {} clip(s); playing '{}'.",
					geometry, model.getBoneIndexes().size(), clips.size(), clip.getName());
			return true;
		} catch (IOException | RuntimeException e) {
			GemRender.LOGGER.error("Bench: SBM could not load the pylon", e);
			failed = true;
			return false;
		}
	}

	private static ResourceLocation sibling(ResourceLocation location, String suffix) {
		String path = location.getPath();
		return ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
				path.substring(0, path.length() - ".geo.json".length()) + suffix);
	}

	private static <T> T read(ResourceLocation location, Class<T> type) throws IOException {
		try (Reader reader = Minecraft.getInstance()
				.getResourceManager()
				.getResourceOrThrow(location)
				.openAsReader()) {
			return GsonUtil.GSON.fromJson(reader, type);
		}
	}

	@Override
	public void render(Matrix4f unused, Matrix4f pose, float animationSeconds) {
		if (model == null || count == 0) {
			return;
		}

		float spacing = BenchGrid.spacing();
		float clipSeconds = BenchGrid.clipSeconds();
		int stride = BenchGrid.stride(count);

		MultiBufferSource.BufferSource buffers = Minecraft.getInstance()
				.renderBuffers()
				.bufferSource();
		VertexConsumer buffer = buffers.getBuffer(RENDER_TYPE);
		PoseStack poseStack = new PoseStack();

		Pose bind = model.getBindPose();

		for (int i = 0; i < count; i++) {
			float t = (animationSeconds + BenchGrid.phaseOffset(i, clipSeconds)) % clipSeconds;
			model.applyPose(blender.blend(bind, clip.evaluate(t)));

			PoseStack.Pose last = poseStack.last();
			last.pose().set(pose).translate((i % stride) * spacing, 0.0f, (i / stride) * spacing);
			last.pose().normal(last.normal());

			model.renderToBuffer(poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		}

		buffers.endBatch();
	}

	@Override
	public void close() {
	}
}
