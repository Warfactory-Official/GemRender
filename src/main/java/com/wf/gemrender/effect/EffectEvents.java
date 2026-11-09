package com.wf.gemrender.effect;

import com.wf.gemrender.GemRender;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
*///?}

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class EffectEvents {
    private EffectEvents() {}

    //? if neoforge && >=1.21 <26.1 {
    @SubscribeEvent
    public static void begin(RenderFrameEvent.Pre event) {
        Effects.beginFrame(event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finish(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            SceneEffects.draw(event.getProjectionMatrix(), event.getModelViewMatrix(), event.getCamera().getPosition());
        }
    }
    //?}
}
