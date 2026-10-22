package com.wf.gemrender.ui;

import com.wf.gemrender.GemRender;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
final class UiEvents {
    private UiEvents() {
    }

    //? if >=26.2 {
    /*@SubscribeEvent
    static void onEndClientResourceReload(
            net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent event) {
        Canvas.reload();
    }

    /^* After {@code GameRenderer.render}: this frame's GUI layers are drawn. ^/
    @SubscribeEvent
    static void onRenderFramePost(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        Canvas.endFrame();
    }
    *///?} else {
    @SubscribeEvent
    static void onEndClientResourceReload(
            dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent event) {
        Canvas.reload();
    }
    //?}
}
