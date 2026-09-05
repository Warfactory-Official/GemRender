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

	/**
	 * A three-armed cross of boxes filling the volume's own [-size, size] cube, standing in for the box
	 * cells a real gas cloud decomposes into. Nothing about it is ellipsoidal, so the row fails loudly if
	 * the field is ignored.
	 */
	private static void buildCross(VolumeField field, float ex, float ey, float ez) {
		// The field's bounds must be the volume's own extents: the shader maps local/extent onto the grid,
		// so a grid built over a different box comes out sheared along whichever axis disagrees.
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
				// Closing the volume releases its field tile too.
				volume.close();
			}
		}
	}
}
