package com.wf.gemrender.light;

import com.wf.gemrender.GemRender;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class LightEvents {
    private LightEvents() {
    }

    //? if >=26.1 {
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
        //? if >=26.2 {
        /^LightGlare.draw(event.getModelViewMatrix(), event.getLevelRenderState().cameraRenderState.projectionMatrix);
        ^///?} else {
        com.wf.gemrender.render.Vanilla.intoMainTarget(() -> LightGlare.draw(event.getModelViewMatrix(),
                event.getLevelRenderState().cameraRenderState.projectionMatrix));
        //?}
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
            //? if >=1.21 {
            LightGlare.draw(event.getModelViewMatrix(), event.getProjectionMatrix());
            //?} else {
            /*LightGlare.draw(event.getPoseStack().last().pose(), event.getProjectionMatrix());
            *///?}
        }
    }
    //?}
}
