package com.wf.gemrender.spike;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import com.wf.gemrender.render.BoneBuffer;
import com.wf.gemrender.render.GemRenderInstance;
import com.wf.gemrender.render.GemRenderInstanceTypes;
import com.wf.gemrender.render.FrameCost;
import com.wf.gemrender.render.SkinnedCubeMesh;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class SpikeVisual extends AbstractVisual implements EffectVisual<SpikeEffect>, SimpleDynamicVisual {
	private static final Model MODEL = new SingleMeshModel(SkinnedCubeMesh.INSTANCE, SimpleMaterial.builder()
			.texture(ResourceLocation.withDefaultNamespace("textures/misc/white.png"))
			.mipmap(false)
			.build());

	private static final int GRID_STRIDE = 32;

	private static final float SPACING = 2.0f;

	public static float gridExtent(int count) {
		return SPACING * Math.max(1, Math.min(count, GRID_STRIDE) - 1);
	}

	private final SpikeEffect effect;
	private final GemRenderInstance[] instances;

	private final Matrix4f[] palette = { new Matrix4f(), new Matrix4f() };

	private final Vector4f sphere = new Vector4f();

	public SpikeVisual(VisualizationContext ctx, SpikeEffect effect, float partialTick) {
		super(ctx, (Level) effect.level(), partialTick);
		this.effect = effect;
		this.instances = new GemRenderInstance[effect.count()];

		var instancer = instancerProvider().instancer(GemRenderInstanceTypes.SKINNED, MODEL);

		Vec3i renderOrigin = renderOrigin();
		BlockPos origin = effect.origin();

		for (int i = 0; i < instances.length; i++) {
			GemRenderInstance instance = instancer.createInstance();

			float x = origin.getX() - renderOrigin.getX() + (i % GRID_STRIDE) * SPACING;
			float y = origin.getY() - renderOrigin.getY();
			float z = origin.getZ() - renderOrigin.getZ() + (i / GRID_STRIDE) * SPACING;

			instance.pose.translation(x, y, z);
			instance.colorArgb(0xFF000000 | hueForIndex(i));

			instance.light(LightTexture.FULL_BRIGHT);
			instance.setChanged();

			instances[i] = instance;
		}
	}

	@Override
	public void beginFrame(Context ctx) {
		long startNanos = System.nanoTime();

		float time = SpikeClock.seconds(level, ctx.partialTick());

		int writes = 0;

		for (int i = 0; i < instances.length; i++) {
			GemRenderInstance instance = instances[i];
			if (instance == null) {
				continue;
			}

			float angle = Mth.sin(time + i * 0.15f) * 0.6f;

			palette[SkinnedCubeMesh.JOINT_LOWER].identity();

			palette[SkinnedCubeMesh.JOINT_UPPER].translation(0.0f, 0.5f, 0.0f)
					.rotateX(angle)
					.translate(0.0f, -0.5f, 0.0f);

			int base = BoneBuffer.getInstance()
					.addPalette((Matrix4fc[]) palette);

			SkinnedCubeMesh.BOUNDS.evaluate(palette, sphere);

			if (instance.boneBase != base || !instance.boneSphere.equals(sphere)) {
				instance.boneBase = base;
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

	private static int hueForIndex(int i) {
		float hue = (i % GRID_STRIDE) / (float) GRID_STRIDE;
		int r = (int) (127 + 127 * Mth.cos(hue * Mth.TWO_PI));
		int g = (int) (127 + 127 * Mth.cos((hue + 0.33f) * Mth.TWO_PI));
		int b = (int) (127 + 127 * Mth.cos((hue + 0.66f) * Mth.TWO_PI));
		return (r << 16) | (g << 8) | b;
	}
}
