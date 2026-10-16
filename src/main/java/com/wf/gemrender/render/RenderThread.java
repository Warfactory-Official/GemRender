package com.wf.gemrender.render;

import com.mojang.blaze3d.systems.RenderSystem;
//? if >=26.1 {
/*import net.minecraft.client.Minecraft;
 *///?}

public final class RenderThread {
    private RenderThread() {
    }

    /** Now on the render thread, else queued; one FIFO queue for every caller. */
    public static void later(Runnable work) {
        if (RenderSystem.isOnRenderThread()) {
            work.run();
            return;
        }
        //? if >=26.1 {
        /*Minecraft.getInstance().execute(work);
         *///?} else {
        RenderSystem.recordRenderCall(work::run);
        //?}
    }
}
