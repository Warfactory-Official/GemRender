package com.wf.gemrender.spike;

import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.AnimationPhase;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;

import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.component.ShadowComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public final class SpikeEntityVisual extends GemRenderEntityVisual<Entity> {
	private static final AtomicInteger BUILT = new AtomicInteger();

	private static final AtomicInteger DRAWN = new AtomicInteger();

	private static final AtomicInteger WAITING = new AtomicInteger();

	private static final AtomicInteger DELETED = new AtomicInteger();

	private final ResourceLocation asset;

	private final String clipName;

	private final boolean sync;

	@Nullable
	private AnimationPhase phase;

	public SpikeEntityVisual(VisualizationContext ctx, Entity entity, float partialTick,
			ResourceLocation asset, String clipName, boolean sync) {
		super(ctx, entity, partialTick, GemRenderModels.handle(asset));
		this.asset = asset;
		this.clipName = clipName;
		this.sync = sync;

		addComponent(new ShadowComponent(ctx, entity).radius(1.0f));

		BUILT.incrementAndGet();
	}

	public static int built() {
		return BUILT.get();
	}

	public static int drawn() {
		return DRAWN.get();
	}

	public static int waited() {
		return WAITING.get();
	}

	public static int live() {
		return BUILT.get() - DELETED.get();
	}

	public static void reset() {
		BUILT.set(0);
		DRAWN.set(0);
		WAITING.set(0);
		DELETED.set(0);
	}

	@Override
	protected void _delete() {
		super._delete();
		DELETED.incrementAndGet();
	}

	@Override
	public void beginFrame(DynamicVisual.Context ctx) {
		super.beginFrame(ctx);

		if (model() == null) {
			WAITING.incrementAndGet();
			return;
		}

		DRAWN.incrementAndGet();

		com.wf.gemrender.render.PoseCache.Pose pose = pose();
		if (pose != null) {
			GltfVisual.recordCullSphere(pose.sphere());
		}
	}

	@Override
	protected void animate(float partialTick, GltfAnimation[] clips, float[] times) {
		GemRenderGltfModel model = model();
		if (model == null) {
			return;
		}

		if (phase == null) {
			GltfAnimation clip = PartsVisual.NO_CLIP.equals(clipName) ? null
					: model.animationOrAny(clipName);

			phase = sync ? AnimationPhase.of(clip) : AnimationPhase.scattered(clip, entity.getId());

			GemRender.LOGGER.info("Entity visual: {} on entity {}, clip '{}', {}", asset, entity.getId(),
					clip == null ? "<none>" : clip.name(), sync ? "synchronised" : "scattered");
		}

		clips[0] = phase.clip();
		times[0] = phase.timeAt(SpikeClock.seconds(level, partialTick));
	}
}
