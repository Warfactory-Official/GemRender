package com.wf.gemrender.ui;

import com.wf.gemrender.GemRender;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
final class UiEvents {
    private UiEvents() {
    }

    @SubscribeEvent
    static void onEndClientResourceReload(
            dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent event) {
        Canvas.reload();
    }
}
