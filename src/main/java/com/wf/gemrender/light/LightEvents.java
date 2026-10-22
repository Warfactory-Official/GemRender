package com.wf.gemrender.light;

import com.wf.gemrender.GemRender;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class LightEvents {
    private LightEvents() {
    }

    //? if >=26.2 {
    /*@SubscribeEvent
    public static void onEndClientResourceReload(
            net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent event) {
        LightCookies.reload();
    }

    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        IrTarget.afterSky();
    }

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        LightGlare.draw(event.getModelViewMatrix(), event.getLevelRenderState().cameraRenderState.projectionMatrix);
    }
    *///?} else {
    @SubscribeEvent
    public static void onEndClientResourceReload(
            dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent event) {
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
    //?}
}
