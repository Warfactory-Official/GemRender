package com.wf.gemrender.direct;

import com.wf.gemrender.GemRender;

import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
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
public final class DirectEvents {
    private DirectEvents() {
    }

    //? if >=26.1 {

    /*@SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        com.wf.gemrender.render.Vanilla.intoMainTarget(() -> DirectRenderer.flush(DirectPass.LEVEL));
    }

    @SubscribeEvent
    public static void onAfterTranslucentFeatures(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        com.wf.gemrender.render.Vanilla.intoMainTarget(() -> DirectRenderer.flush(DirectPass.LEVEL));
    }

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        com.wf.gemrender.render.Vanilla.intoMainTarget(() -> DirectRenderer.flush(DirectPass.LEVEL));
    }
*///?} else {
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                || stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            DirectRenderer.flush(DirectPass.LEVEL);
        }
    }
    //?}

    @SubscribeEvent
    public static void onEndClientResourceReload(EndClientResourceReloadEvent event) {
        if (event.error()
                .isPresent()) {
            return;
        }

        ResidentModels.freeAll();
        DirectRenderer.freeAll();
        DirectProgram.getInstance()
                .delete();
    }

    //? if >=26.1 {
	
	/*@SubscribeEvent
	public static void onRegisterSpecialModelRenderers(
			net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) {
		event.register(GemRenderItemRenderer.TYPE_ID, GemRenderItemRenderer.Unbaked.MAP_CODEC);
	}
*///?}
}
