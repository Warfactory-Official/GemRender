package com.wf.gemrender.spike;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.AnimationPhase;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeSpin;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.render.GemRenderInstance;
import com.wf.gemrender.render.GemRenderInstanceTypes;
import com.wf.gemrender.render.FrameCost;
import com.wf.gemrender.render.PoseCache;
import com.wf.gemrender.render.PoseLod;

import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GltfVisual extends AbstractVisual implements EffectVisual<GltfEffect>, SimpleDynamicVisual {
	private static final float SPACING_FACTOR = 2.6f;
	private static final float MIN_SPACING = 4.0f;

	private static final float SPACING_OVERRIDE =
			Float.parseFloat(System.getProperty("gemrender.spacing", "0"));

	private static final Vector4f LAST_CULL_SPHERE = new Vector4f();

	public static Vector4f lastCullSphere() {
		synchronized (LAST_CULL_SPHERE) {
			return new Vector4f(LAST_CULL_SPHERE);
		}
	}

	public static float spacingOf(@Nullable Vector4fc sphere) {
		if (SPACING_OVERRIDE > 0.0f) {
			return SPACING_OVERRIDE;
		}
		return sphere == null ? MIN_SPACING : Math.max(MIN_SPACING, sphere.w() * SPACING_FACTOR);
	}

	public static float spacing(@Nullable GemRenderGltfModel gltf) {
		return spacingOf(gltf == null ? null : gltf.model()
				.boundingSphere());
	}

	public static float gridExtentOf(@Nullable Vector4fc sphere, int count) {
		return spacingOf(sphere) * Math.max(1, stride(count) - 1);
	}

	public static int stride(int count) {
		return Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, count))));
	}

	public static float gridExtent(@Nullable GemRenderGltfModel gltf, int count) {
		return spacing(gltf) * Math.max(1, stride(count) - 1);
	}

	@Nullable
	private final GemRenderGltfModel gltf;

	private final AnimationPhase[] phases;

	private final GemRenderInstance[] instances;

	private final double[] worldPositions;

	public GltfVisual(VisualizationContext ctx, GltfEffect effect, float partialTick) {
		super(ctx, (Level) effect.level(), partialTick);
		this.gltf = SpikeAssets.model(effect.asset());

		if (gltf == null) {
			this.instances = new GemRenderInstance[0];
			this.phases = new AnimationPhase[0];
			this.worldPositions = new double[0];
			return;
		}

		this.instances = new GemRenderInstance[effect.count()];
		this.phases = new AnimationPhase[effect.count()];
		this.worldPositions = new double[effect.count() * 3];

		GltfAnimation animation = spun(gltf, PartsVisual.NO_CLIP.equals(effect.animation()) ? null
				: gltf.animationOrAny(effect.animation()), effect);

		var instancer = instancerProvider().instancer(GemRenderInstanceTypes.SKINNED, gltf.model());

		float spacing = spacing(gltf);
		int stride = stride(instances.length);

		Vec3i renderOrigin = renderOrigin();
		BlockPos origin = effect.origin();

		for (int i = 0; i < instances.length; i++) {
			GemRenderInstance instance = instancer.createInstance();

			int column = i % stride;
			int row = i / stride;

			float x = origin.getX() - renderOrigin.getX() + column * spacing;
			float y = origin.getY() - renderOrigin.getY();
			float z = origin.getZ() - renderOrigin.getZ() + row * spacing;

			instance.pose.translation(x, y, z);

			worldPositions[i * 3] = x + renderOrigin.getX();
			worldPositions[i * 3 + 1] = y + renderOrigin.getY();
			worldPositions[i * 3 + 2] = z + renderOrigin.getZ();

			instance.colorArgb(0xFFFFFFFF);

			instance.light(LightTexture.FULL_BRIGHT);
			instance.setChanged();

			phases[i] = effect.sync()
					? AnimationPhase.of(animation)
							.withSpeed(effect.speed())
					: AnimationPhase.scattered(animation, origin.offset(column, 0, row)
							.asLong())
							.withSpeed(effect.speed());

			instances[i] = instance;
		}

		GemRender.LOGGER.info("glTF visual: {} x {}, {} palette slots each, animation={}, spacing={}, "
				+ "phases={}, pose lod {}",
				instances.length, effect.asset(), gltf.jointCount(),
				animation == null ? "<none>" : animation.name(), spacing,
				effect.sync() ? "synchronised" : "scattered", PoseLod.getInstance());
	}

	@Nullable
	private static GltfAnimation spun(GemRenderGltfModel gltf, @Nullable GltfAnimation clip, GltfEffect effect) {
		if (effect.spin() == 0.0f) {
			return clip;
		}

		NodeTable table = gltf.layout()
				.nodeTable();
		int slot = effect.spinNode() >= 0 ? effect.spinNode() : table.firstRootSlot();

		com.wf.gemrender.gltf.PoseDriver spin = com.wf.gemrender.gltf.DutyCycle
				.of(NodeSpin.aboutY(table, slot, effect.spin()), effect.spinDuty());

		GemRender.LOGGER.info("glTF visual: spinning slot {} ('{}') at {} turns/s, {}% duty, {}", slot,
				table.nodeName(slot), effect.spin(), Math.round(effect.spinDuty() * 100),
				clip == null ? "as the whole animation"
						: "merged into '" + clip.name() + "' (" + clip.duration() + "s), which now runs at "
								+ Math.max(clip.duration(), spin.cycleSeconds()) + "s");

		return clip == null ? GltfAnimation.procedural("spin", spin) : clip.with(spin);
	}

	private boolean reported;

	@Override
	public void beginFrame(Context ctx) {
		if (gltf == null) {
			return;
		}

		long startNanos = System.nanoTime();

		float time = SpikeClock.seconds(level, ctx.partialTick());

		PoseCache poses = PoseCache.getInstance();
		PoseLod lod = PoseLod.getInstance();
		Vec3 camera = ctx.camera()
				.getPosition();

		int writes = 0;

		for (int i = 0; i < instances.length; i++) {
			GemRenderInstance instance = instances[i];
			if (instance == null) {
				continue;
			}

			AnimationPhase phase = phases[i];

			double dx = worldPositions[i * 3] - camera.x;
			double dy = worldPositions[i * 3 + 1] - camera.y;
			double dz = worldPositions[i * 3 + 2] - camera.z;
			double distanceSquared = dx * dx + dy * dy + dz * dz;

			boolean frozen = lod.frozenAt(distanceSquared);
			int level = frozen ? 0 : lod.levelAt(distanceSquared);

			if (i == 0 && !reported) {
				reported = true;
				GemRender.LOGGER.info("glTF visual: copy 0 clip '{}' (period {}s) evaluated at {}s, clock {}s",
						phase.clip() == null ? "<none>" : phase.clip()
								.name(),
						phase.clip() == null ? 0.0f : phase.clip()
								.duration(),
						frozen ? 0.0f : phase.timeAt(time), time);
			}

			PoseCache.Pose pose = poses.pose(gltf.layout(), gltf.bounds(), gltf.morphs(),
					frozen ? null : phase.clip(), frozen ? 0.0f : phase.timeAt(time), level);

			Vector4fc sphere = pose.sphere();
			synchronized (LAST_CULL_SPHERE) {
				LAST_CULL_SPHERE.set(sphere);
			}

			if (instance.boneBase != pose.boneBase() || instance.morphBase != pose.morphBase()
					|| !instance.boneSphere.equals(sphere)) {
				instance.boneBase = pose.boneBase();
				instance.morphBase = pose.morphBase();
				instance.boneSphere.set(sphere);

				instance.setChanged();
				writes++;
			}
		}

		FrameCost cost = FrameCost.getInstance();
		cost.addInstanceWrites(writes);
		cost.addVisualNanos(System.nanoTime() - startNanos);
	}

	@Override
	protected void _delete() {
		for (GemRenderInstance instance : instances) {
			if (instance != null) {
				instance.delete();
			}
		}
	}
}
