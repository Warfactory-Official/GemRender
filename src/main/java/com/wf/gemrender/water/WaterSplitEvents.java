package com.wf.gemrender.water;

import com.wf.gemrender.GemRender;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class WaterSplitEvents {
	private WaterSplitEvents() {
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			WaterSplit.getInstance()
					.onAfterEntities(event);
		} else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
			WaterSplit.getInstance()
					.onAfterTranslucent(event);
		}
	}
}
