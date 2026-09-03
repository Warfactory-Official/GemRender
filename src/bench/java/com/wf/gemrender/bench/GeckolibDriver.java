package com.wf.gemrender.bench;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.GemRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

public final class GeckolibDriver implements BenchDriver {
	private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "geo/pylon.geo.json");
	private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "animations/pylon.animation.json");
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			GemRender.MOD_ID, "models/pylon/pylon.png");

	private static final RawAnimation CLIP =
			RawAnimation.begin().thenLoop("animation.pylon.running_loop");

	private final int count;
	private final Pylon[] copies;
	private final PylonRenderer renderer;

	private boolean seeded;

	public GeckolibDriver(int count) {
		this.count = count;
		this.copies = new Pylon[count];
		for (int i = 0; i < count; i++) {
			copies[i] = new Pylon(i);
		}
		this.renderer = new PylonRenderer(new PylonModel());
	}

	@Override
	public String name() {
		return "geckolib";
	}

	@Override
	public boolean load() {
		return !software.bernie.geckolib.cache.GeckoLibCache.getBakedModels().isEmpty();
	}

	@Override
	public void render(Matrix4f unused, Matrix4f pose, float animationSeconds) {
		if (count == 0) {
			return;
		}

		float spacing = BenchGrid.spacing();
		int stride = BenchGrid.stride(count);

		if (!seeded) {
			seed();
			seeded = true;
		}

		Minecraft mc = Minecraft.getInstance();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		PoseStack poseStack = new PoseStack();
		float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);

		for (int i = 0; i < count; i++) {
			PoseStack.Pose last = poseStack.last();
			last.pose().set(pose).translate((i % stride) * spacing, 0.0f, (i / stride) * spacing);
			last.pose().normal(last.normal());

			renderer.forCopy(i);
			renderer.render(poseStack, copies[i], buffers, null, null, LightTexture.FULL_BRIGHT,
					partialTick);
		}

		buffers.endBatch();
	}

	private void seed() {
		double now = RenderUtil.getCurrentTick();
		float clip = BenchGrid.clipSeconds();
		for (int i = 0; i < count; i++) {
			AnimatableManager<Pylon> manager = copies[i].getAnimatableInstanceCache()
					.getManagerForId(i);
			manager.startedAt(now - BenchGrid.phaseOffset(i, clip) * 20.0f);
		}
		GemRender.LOGGER.info("Bench: GeckoLib seeded {} copies with phases spread over {}s.", count, clip);
	}

	@Override
	public void close() {
	}

	private static final class Pylon implements GeoAnimatable {
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
		private final int index;

		Pylon(int index) {
			this.index = index;
		}

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
			controllers.add(new AnimationController<>(this, state -> state.setAndContinue(CLIP)));
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}

		@Override
		public double getTick(Object object) {
			return RenderUtil.getCurrentTick();
		}
	}

	private static final class PylonModel extends GeoModel<Pylon> {
		@Override
		public ResourceLocation getModelResource(Pylon animatable) {
			return MODEL;
		}

		@Override
		public ResourceLocation getTextureResource(Pylon animatable) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(Pylon animatable) {
			return ANIMATION;
		}
	}

	private static final class PylonRenderer extends GeoObjectRenderer<Pylon> {
		private long copy;

		PylonRenderer(GeoModel<Pylon> model) {
			super(model);
		}

		void forCopy(int index) {
			this.copy = index;
		}

		@Override
		public long getInstanceId(Pylon animatable) {
			return copy;
		}
	}
}
