package com.wf.gemrender.spike;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.AnimationPhase;
import com.wf.gemrender.gltf.DutyCycle;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeSpin;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PartsPose;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.render.FrameCost;
import com.wf.gemrender.render.PoseCache;
import com.wf.gemrender.render.PoseLod;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PartsVisual extends AbstractVisual
		implements EffectVisual<PartsEffect>, SimpleDynamicVisual {
	public static final String NO_CLIP = "none";

	private record Layer(String name, GltfAnimation clip, boolean[] recompute, float cycleSeconds,
			float duty) {
	}

	@Nullable
	private final GemRenderPartsModel model;

	private final TransformedInstance[] instances;

	private final Layer[] layers;

	private final AnimationPhase[] phases;

	private final int[] lastBucket;

	private final Matrix4f[] base;

	private final Matrix4f[] transforms;
	private final Matrix4f composed = new Matrix4f();
	private final PartsPose.Scratch scratch = new PartsPose.Scratch();

	private final GltfAnimation[] clips;
	private final float[] times;
	private final boolean[] changed;

	private final double[] worldPositions;

	private boolean reported;

	public PartsVisual(VisualizationContext ctx, PartsEffect effect, float partialTick) {
		super(ctx, (Level) effect.level(), partialTick);
		this.model = SpikeAssets.parts(effect.asset());

		if (model == null) {
			this.instances = new TransformedInstance[0];
			this.layers = new Layer[0];
			this.phases = new AnimationPhase[0];
			this.lastBucket = new int[0];
			this.base = new Matrix4f[0];
			this.transforms = new Matrix4f[0];
			this.clips = new GltfAnimation[0];
			this.times = new float[0];
			this.changed = new boolean[0];
			this.worldPositions = new double[0];
			return;
		}

		int parts = model.partCount();
		int count = effect.count();

		this.layers = layers(model, effect);
		this.instances = new TransformedInstance[count * parts];
		this.phases = new AnimationPhase[count * layers.length];
		this.lastBucket = new int[count * layers.length];
		this.base = new Matrix4f[count];
		this.transforms = model.newTransforms();
		this.clips = new GltfAnimation[layers.length];
		this.times = new float[layers.length];
		this.changed = new boolean[parts];
		this.worldPositions = new double[count * 3];

		Arrays.fill(lastBucket, Integer.MIN_VALUE);

		PartsPose.evaluate(model, (GltfAnimation) null, 0.0f, transforms, scratch);

		float spacing = GltfVisual.spacingOf(model.boundingSphere());
		int stride = GltfVisual.stride(count);

		float span = 0.0f;
		for (Layer layer : layers) {
			span = Math.max(span, layer.clip()
					.duration());
		}

		Vec3i renderOrigin = renderOrigin();
		BlockPos origin = effect.origin();

		for (int copy = 0; copy < count; copy++) {
			int column = copy % stride;
			int row = copy / stride;

			float x = origin.getX() - renderOrigin.getX() + column * spacing;
			float y = origin.getY() - renderOrigin.getY();
			float z = origin.getZ() - renderOrigin.getZ() + row * spacing;

			base[copy] = new Matrix4f().translation(x, y, z);

			worldPositions[copy * 3] = x + renderOrigin.getX();
			worldPositions[copy * 3 + 1] = y + renderOrigin.getY();
			worldPositions[copy * 3 + 2] = z + renderOrigin.getZ();

			float offset = effect.sync() ? 0.0f
					: AnimationPhase.scatterOffset(origin.offset(column, 0, row)
							.asLong(), span);

			for (int layer = 0; layer < layers.length; layer++) {
				phases[copy * layers.length + layer] =
						new AnimationPhase(layers[layer].clip(), offset, 1.0f);
			}

			for (int part = 0; part < parts; part++) {
				Model mesh = model.parts()
						.get(part)
						.model();
				if (mesh == null) {
					continue;
				}

				TransformedInstance instance = instancerProvider()
						.instancer(InstanceTypes.TRANSFORMED, mesh)
						.createInstance();
				instance.colorArgb(0xFFFFFFFF);
				instance.light(LightTexture.FULL_BRIGHT);
				instance.pose.set(base[copy])
						.mul(transforms[part]);
				instance.setChanged();

				instances[copy * parts + part] = instance;
			}
		}

		StringBuilder report = new StringBuilder();
		for (Layer layer : layers) {
			report.append(report.isEmpty() ? "" : " + ")
					.append(layer.name())
					.append('(')
					.append(GemRenderPartsModel.countTrue(layer.recompute()))
					.append(" parts")
					.append(layer.duty() >= 1.0f ? "" : ", " + Math.round(layer.duty() * 100) + "% duty")
					.append(')');
		}

		GemRender.LOGGER.info("Parts visual: {} x {}, {} parts each ({} distinct meshes), {} layer(s): {}, "
				+ "spacing={}, phases={}",
				count, effect.asset(), parts, model.meshCount(), layers.length,
				report.isEmpty() ? "<rest pose>" : report, spacing,
				effect.sync() ? "synchronised" : "scattered");
	}

	private static Layer[] layers(GemRenderPartsModel model, PartsEffect effect) {
		List<Layer> out = new ArrayList<>(2);
		float clipSeconds = 0.0f;

		if (!NO_CLIP.equals(effect.animation())) {
			GltfAnimation clip = model.animationOrAny(effect.animation());
			if (clip != null) {
				clipSeconds = clip.duration();
				out.add(layer(model, clip.name(), clip, 1.0f));
			}
		}

		PoseDriver spin = spin(model, effect, effect.spin());
		if (spin != null) {
			out.add(layer(model, "spin", GltfAnimation.procedural("spin", spin), effect.spinDuty()));
		}

		if (clipSeconds > 0.0f && out.size() > 1) {
			GemRender.LOGGER.info("Parts visual: {} layers kept apart, periods {}s and {}s; the palette path "
					+ "has to merge these into one clip running at the longer of the two.",
					out.size(), clipSeconds, out.get(1)
							.clip()
							.duration());
		}

		return out.toArray(new Layer[0]);
	}

	private static Layer layer(GemRenderPartsModel model, String name, GltfAnimation clip, float duty) {
		return new Layer(name, clip, model.withAncestors(model.drivenBy(clip)), clip.duration(), duty);
	}

	@Nullable
	private static PoseDriver spin(GemRenderPartsModel model, PartsEffect effect, float rate) {
		if (rate == 0.0f) {
			return null;
		}

		NodeTable table = model.layout()
				.nodeTable();
		int slot = effect.spinBone()
				.isEmpty() ? table.firstRootSlot() : table.slotOfName(effect.spinBone());
		if (slot < 0) {
			GemRender.LOGGER.error("Parts visual: no bone named '{}' to spin; the turret will not move.",
					effect.spinBone());
			return null;
		}

		int part = model.slotToPart()[slot];
		if (model.parts()
				.get(part)
				.rootSlot() != slot) {
			GemRender.LOGGER.error("Parts visual: '{}' is baked into part '{}' and cannot move on its own. "
					+ "Declare it in gemrender:gameplay_bones (or -PgameplayBones) so the partition cuts "
					+ "above it, or this run draws a vehicle whose turret never turns.",
					table.nodeName(slot), model.parts()
							.get(part)
							.name());
		}

		GemRender.LOGGER.info("Parts visual: spinning slot {} ('{}') at {} turns/s as its own layer, {}% duty",
				slot, table.nodeName(slot), rate, Math.round(effect.spinDuty() * 100));

		return DutyCycle.of(NodeSpin.aboutY(table, slot, rate), effect.spinDuty());
	}

	@Override
	public void beginFrame(Context ctx) {
		if (model == null || layers.length == 0) {
			return;
		}

		long startNanos = System.nanoTime();
		float time = SpikeClock.seconds(level, ctx.partialTick());

		int parts = model.partCount();
		int writes = 0;

		PoseCache poses = PoseCache.getInstance();
		PoseLod lod = PoseLod.getInstance();
		Vec3 camera = ctx.camera()
				.getPosition();

		for (int copy = 0; copy < base.length; copy++) {
			double dx = worldPositions[copy * 3] - camera.x;
			double dy = worldPositions[copy * 3 + 1] - camera.y;
			double dz = worldPositions[copy * 3 + 2] - camera.z;
			double distanceSquared = dx * dx + dy * dy + dz * dz;

			boolean frozen = lod.frozenAt(distanceSquared);
			float quantum = poses.quantumSeconds(frozen ? 0 : lod.levelAt(distanceSquared));

			boolean any = false;
			Arrays.fill(changed, false);

			for (int layer = 0; layer < layers.length; layer++) {
				Layer current = layers[layer];
				AnimationPhase phase = phases[copy * layers.length + layer];

				float wanted = frozen ? 0.0f
						: DutyCycle.held(phase.timeAt(time), current.cycleSeconds(), current.duty());
				int bucket = quantum <= 0.0f ? Float.floatToIntBits(wanted) : Math.round(wanted / quantum);

				clips[layer] = current.clip();
				times[layer] = quantum <= 0.0f ? wanted : bucket * quantum;

				if (bucket != lastBucket[copy * layers.length + layer]) {
					lastBucket[copy * layers.length + layer] = bucket;
					any = true;
					or(changed, current.recompute());
				}
			}

			if (!any) {
				continue;
			}

			if (copy == 0 && !reported) {
				reported = true;
				for (int layer = 0; layer < layers.length; layer++) {
					GemRender.LOGGER.info("Parts visual: copy 0 layer '{}' (period {}s) evaluated at {}s, "
							+ "clock {}s, quantum {}s", layers[layer].name(), layers[layer].cycleSeconds(),
							times[layer], time, quantum);
				}
			}

			long poseStart = System.nanoTime();
			PartsPose.evaluate(model, clips, times, transforms, changed, scratch);
			FrameCost.getInstance()
					.addPoseNanos(System.nanoTime() - poseStart);

			for (int part = 0; part < parts; part++) {
				TransformedInstance instance = instances[copy * parts + part];
				if (instance == null || !changed[part]) {
					continue;
				}

				composed.set(base[copy])
						.mul(transforms[part]);
				if (instance.pose.equals(composed)) {
					continue;
				}

				instance.pose.set(composed);
				instance.setChanged();
				writes++;
			}
		}

		FrameCost cost = FrameCost.getInstance();
		cost.addInstanceWrites(writes);
		cost.addVisualNanos(System.nanoTime() - startNanos);
	}

	private static void or(boolean[] into, boolean[] from) {
		for (int i = 0; i < into.length; i++) {
			into[i] |= from[i];
		}
	}

	@Override
	protected void _delete() {
		for (TransformedInstance instance : instances) {
			if (instance != null) {
				instance.delete();
			}
		}
	}
}
