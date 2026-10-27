package com.wf.gemrender.lens;

import com.wf.gemrender.GemRender;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
*///?}

// TODO 1.20.1, 26.1, Fabric: no AFTER_LEVEL hook wired, as CameraMediumEvents; submitted lenses are dropped there.
@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class LensEvents {
    private LensEvents() {
    }

    //? if >=1.21 <26.1 {
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            Lenses.getInstance()
                    .draw(event.getProjectionMatrix(), event.getModelViewMatrix(), event.getCamera()
                            .getPosition());
        }
    }
    //?}
}
