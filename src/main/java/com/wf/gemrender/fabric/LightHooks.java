package com.wf.gemrender.fabric;

//? if lights && <26.1 {
import com.wf.gemrender.light.IrTarget;
import com.wf.gemrender.light.LightCookies;
import com.wf.gemrender.light.LightGlare;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//?}

/** What {@code LightEvents} subscribes to on NeoForge and Forge. */
public final class LightHooks {
    private LightHooks() {
    }

    // 26.1: AfterSky and AfterLevel map to START_MAIN (the main pass follows the sky) and END_MAIN, which runs
    // before clouds and weather. Each pass ends on framebuffer 0, so the glare binds the main target itself.
    //? if lights && >=26.1 {
    /*public static void init() {
        System.setProperty("voxy.auxColour", "true");
        dev.engine_room.flywheel.api.event.EndClientResourceReloadCallback.EVENT.register(
                (minecraft, resources, first, error) -> com.wf.gemrender.light.LightCookies.reload());
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.START_MAIN.register(
                context -> com.wf.gemrender.light.IrTarget.afterSky());
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN.register(context -> {
            net.minecraft.client.renderer.state.level.CameraRenderState camera = context.levelState().cameraRenderState;
            com.wf.gemrender.render.Vanilla.intoMainTarget(() -> com.wf.gemrender.light.LightGlare.draw(
                    camera.viewRotationMatrix, camera.projectionMatrix));
        });
    }
    *///?} elif lights {
    public static void init() {
        System.setProperty("voxy.auxColour", "true");
        EndClientResourceReloadCallback.EVENT.register((minecraft, resources, first, error) -> LightCookies.reload());
        // No after-sky hook here: AFTER_SETUP runs after the sky and before the terrain.
        WorldRenderEvents.AFTER_SETUP.register(context -> IrTarget.afterSky());
        WorldRenderEvents.END.register(LightHooks::afterLevel);
    }

    private static void afterLevel(WorldRenderContext context) {
        //? if >=1.21 {
        LightGlare.draw(context.positionMatrix(), context.projectionMatrix());
        //?} else {
        /*LightGlare.draw(context.matrixStack().last().pose(), context.projectionMatrix());
        *///?}
    }
    //?} else {
    /*public static void init() {
    }
    *///?}
}
