package com.wf.gemrender.water;

import com.wf.gemrender.GemRender;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

//? if neoforge {
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class WaterSplitEvents {
    private WaterSplitEvents() {
    }

    //? if >=26.1 {
	/*@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onAfterEntities(RenderLevelStageEvent.AfterOpaqueFeatures event) {
		WaterSplit.getInstance()
				.onAfterEntities(event);
	}

	@SubscribeEvent
	public static void onAfterTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
		WaterSplit.getInstance()
				.onAfterTranslucent(event);
	}

	@SubscribeEvent
	public static void onAfterWeather(RenderLevelStageEvent.AfterWeather event) {
		WaterSplit.getInstance()
				.onAfterWeather(event);
	}
*///?} else {
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            WaterSplit.getInstance()
                    .onAfterEntities(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            WaterSplit.getInstance()
                    .onAfterTranslucent(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {

            WaterSplit.getInstance()
                    .onAfterWeather(event);
        }
    }
    //?}
}
