package com.wf.gemrender.ui;

//? if fabric {
/*import dev.engine_room.flywheel.api.event.EndClientResourceReloadCallback;

public final class UiEvents {
    private UiEvents() {
    }

    public static void init() {
        EndClientResourceReloadCallback.EVENT.register((minecraft, resources, first, error) -> Canvas.reload());
    }
}
*///?} else {
import com.wf.gemrender.GemRender;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
*///?}
//? if >=26.1 {
/*import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
*///?} else {
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
//?}

//? if forge {
/*@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
*///?} else {
@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
//?}
public final class UiEvents {
    private UiEvents() {
    }

    //? if >=26.1 {
    /*@SubscribeEvent
    public static void onEndClientResourceReload(ClientResourceLoadFinishedEvent event) {
        Canvas.reload();
    }

    @SubscribeEvent
    public static void onRegisterPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(CanvasPass.class, CanvasPip::new);
    }
    *///?} else {
    @SubscribeEvent
    public static void onEndClientResourceReload(EndClientResourceReloadEvent event) {
        Canvas.reload();
    }
    //?}
}
//?}
