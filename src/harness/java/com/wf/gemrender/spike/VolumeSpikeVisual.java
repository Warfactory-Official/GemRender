package com.wf.gemrender.spike;

import com.wf.gemrender.volume.GemRenderVolumeTypes;
import com.wf.gemrender.volume.Volume;
import com.wf.gemrender.volume.VolumeField;
import com.wf.gemrender.volume.VolumeInstance;
import com.wf.gemrender.volume.VolumeModels;
import com.wf.gemrender.volume.VolumeStyle;

import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

public final class VolumeSpikeVisual extends AbstractVisual
		implements EffectVisual<VolumeSpikeEffect>, SimpleDynamicVisual {

	private final Volume[] volumes;

	private final VolumeInstance[] instances;

	private final VolumeField[] fields;

	private final BlockPos[] centres;

	public VolumeSpikeVisual(VisualizationContext ctx, VolumeSpikeEffect effect, float partialTick) {
		super(ctx, (Level) effect.level(), partialTick);

		Instancer<VolumeInstance> instancer = ctx.instancerProvider()
				.instancer(GemRenderVolumeTypes.VOLUME, VolumeModels.cloud());

		VolumeStyle style = VolumeSpikeEffect.style();

		int count = effect.count();
		int side = (int) Math.ceil(Math.sqrt(count));
		int spacing = effect.spacing();
		float size = VolumeSpikeEffect.size();

		volumes = new Volume[count];
		instances = new VolumeInstance[count];
		fields = new VolumeField[count];
		centres = new BlockPos[count];

		for (int i = 0; i < count; i++) {
			volumes[i] = Volume.create(style)
					.extent(size, size * 0.7f, size)
					.seed(i * 7.31f);

			centres[i] = effect.origin()
					.offset((i % side - side / 2) * spacing, Math.round(size * 0.8f),
							(i / side - side / 2) * spacing);

			if (VolumeSpikeEffect.CELLS) {
				fields[i] = VolumeField.create();
				if (fields[i] != null) {
					buildCross(fields[i], size, size * 0.7f, size);
					volumes[i].field(fields[i]);
				}
			}

			instances[i] = instancer.createInstance();
			instances[i].volume(volumes[i].slot());
		}

		push();
	}

	@Override
	public void beginFrame(Context context) {
		push();
	}

	private void push() {
		Vec3i renderOrigin = renderOrigin();

		for (int i = 0; i < instances.length; i++) {
			instances[i].center(centres[i].getX() - renderOrigin.getX(),
					centres[i].getY() - renderOrigin.getY(),
					centres[i].getZ() - renderOrigin.getZ());
			instances[i].setChanged();
		}
	}

	private static void buildCross(VolumeField field, float ex, float ey, float ez) {

		field.begin(-ex, -ey, -ez, ex, ey, ez);

		float tx = ex * 0.35f;
		float ty = ey * 0.35f;
		float tz = ez * 0.35f;

		field.addBox(-ex, -ty, -tz, ex, ty, tz);
		field.addBox(-tx, -ey, -tz, tx, ey, tz);
		field.addBox(-tx, -ty, -ez, tx, ty, ez);
		field.commit();
	}

	public int size() {
		return volumes.length;
	}

	@Override
	protected void _delete() {
		for (VolumeInstance instance : instances) {
			if (instance != null) {
				instance.delete();
			}
		}
		for (Volume volume : volumes) {
			if (volume != null) {

				volume.close();
			}
		}
	}
}
