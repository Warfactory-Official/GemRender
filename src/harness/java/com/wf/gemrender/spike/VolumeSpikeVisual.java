package com.wf.gemrender.spike;

import com.wf.gemrender.volume.GemRenderVolumeTypes;
import com.wf.gemrender.volume.Volume;
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

/**
 * One {@link Volume} and one {@link VolumeInstance} per cloud in the grid.
 *
 * <p>The centres are pushed every frame even though the grid does not move, because the thing they are
 * measured against does: Flywheel's render origin shifts as the camera does, and an instance holding a
 * position computed against a stale origin is drawn at the difference. The harness teleports the camera
 * one tick after staging the scene, so writing the centre once put every cloud tens of blocks to the side
 * -- which looks exactly like the volume failing to draw, since the box lands off frame.
 */
public final class VolumeSpikeVisual extends AbstractVisual
		implements EffectVisual<VolumeSpikeEffect>, SimpleDynamicVisual {

	private final Volume[] volumes;

	private final VolumeInstance[] instances;

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
		centres = new BlockPos[count];

		for (int i = 0; i < count; i++) {
			volumes[i] = Volume.create(style)
					.extent(size, size * 0.7f, size)
					.seed(i * 7.31f);

			centres[i] = effect.origin()
					.offset((i % side - side / 2) * spacing, Math.round(size * 0.8f),
							(i / side - side / 2) * spacing);

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
