package com.wf.gemrender.spike;

import com.wf.gemrender.Ids;
import com.wf.gemrender.rope.GemRenderRopeTypes;
import com.wf.gemrender.rope.RopeInstance;
import com.wf.gemrender.rope.RopeModels;

import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class RopeSpikeVisual extends AbstractVisual implements EffectVisual<RopeSpikeEffect> {
	private static final ResourceLocation TEXTURE =
			Ids.vanilla("textures/block/chain.png");
	private static final ResourceLocation SMOKE = Ids.vanilla("textures/misc/white.png");
	private static final boolean ABSORBANCE = "absorbance".equals(System.getProperty("gemrender.ropematerial"));

	private final List<RopeInstance> ropes = new ArrayList<>();

	public RopeSpikeVisual(VisualizationContext ctx, RopeSpikeEffect effect, float partialTick) {
		super(ctx, (Level) effect.level(), partialTick);

		Model model = ABSORBANCE
				? RopeModels.absorbance(SMOKE, RopeSpikeEffect.RINGS, RopeSpikeEffect.SIDES)
				: RopeModels.cutout(TEXTURE, RopeSpikeEffect.RINGS, RopeSpikeEffect.SIDES);
		Instancer<RopeInstance> instancer = ctx.instancerProvider()
				.instancer(GemRenderRopeTypes.ROPE, model);

		Vec3i renderOrigin = ctx.renderOrigin();
		BlockPos at = effect.origin();

		float x = at.getX() - renderOrigin.getX();
		float y = at.getY() - renderOrigin.getY();
		float z = at.getZ() - renderOrigin.getZ();

		int count = effect.count();
		float span = RopeSpikeEffect.SPAN;

		for (int i = 0; i < count; i++) {
			// Half the ropes swing between two posts, half hang straight down like a mooring chain.
			boolean hanging = (i & 1) == 1;
			float lane = (i / 2) - (count / 4.0f);

			RopeInstance rope = instancer.createInstance();

			if (hanging) {
				rope.between(x + lane * 1.5f, y + span, z + 3.0f,
								x + lane * 1.5f, y, z + 3.0f)
						.slack(1.0f + (i % 7) * 0.01f)
						// A phase from the lane, not a random: a random looks identical on frame one
						// and jumps every time the visual is rebuilt.
						.sway(RopeSpikeEffect.SWAY, 0.9f, lane * 1.7f, 0.6f);
			} else {
				rope.between(x + lane * 1.5f, y + span, z,
								x + lane * 1.5f + span, y + span, z)
						.slack(1.02f + (i % 9) * 0.06f)
						.sway(RopeSpikeEffect.SWAY * 0.25f, 0.5f, lane * 2.3f, 1.0f);
			}

			if (ABSORBANCE) {
				rope.color(220, 220, 220, 120);
			}
			rope.radius(RopeSpikeEffect.RADIUS)
					.tiling(Math.max(1.0f, span))
					.litUniformly(0xF000F0)
					.refresh()
					.setChanged();

			ropes.add(rope);
		}
	}

	@Override
	protected void _delete() {
		for (RopeInstance rope : ropes) {
			rope.delete();
		}
	}
}
