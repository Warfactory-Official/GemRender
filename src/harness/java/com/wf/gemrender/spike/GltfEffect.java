package com.wf.gemrender.spike;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;

public record GltfEffect(LevelAccessor level, BlockPos origin, ResourceLocation asset, int count,
		String animation, boolean sync, float speed, float spin, int spinNode, float spinDuty)
		implements Effect {
	public GltfEffect(LevelAccessor level, BlockPos origin, ResourceLocation asset, int count, String animation) {
		this(level, origin, asset, count, animation, false, 1.0f, 0.0f, 0, 1.0f);
	}

	public GltfEffect(LevelAccessor level, BlockPos origin, ResourceLocation asset, int count,
			String animation, boolean sync, float speed) {
		this(level, origin, asset, count, animation, sync, speed, 0.0f, 0, 1.0f);
	}

	public GltfEffect(LevelAccessor level, BlockPos origin, ResourceLocation asset, int count,
			String animation, boolean sync, float speed, float spin, int spinNode) {
		this(level, origin, asset, count, animation, sync, speed, spin, spinNode, 1.0f);
	}

	@Override
	public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
		return new GltfVisual(ctx, this, partialTick);
	}
}
