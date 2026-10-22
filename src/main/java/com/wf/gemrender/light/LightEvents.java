package com.wf.gemrender.light;

import com.wf.gemrender.GemRender;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class LightEvents {
    private LightEvents() {
    }

    @SubscribeEvent
    public static void onEndClientResourceReload(EndClientResourceReloadEvent event) {
        LightCookies.reload();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            IrTarget.afterSky();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            LightGlare.draw(event.getModelViewMatrix(), event.getProjectionMatrix());
        }
    }
}
