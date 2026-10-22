package com.wf.gemrender.fabric;

import com.wf.gemrender.client.GemRenderClient;
import com.wf.gemrender.client.SpikeHud;
import com.wf.gemrender.client.SpikeSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
/*import com.wf.gemrender.GemRender;
import com.wf.gemrender.Ids;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class HarnessHooks {

    private HarnessHooks() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> GemRenderClient.tick());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher
                .register(GemRenderClient.commands(HarnessHooks::sourceOf)));

        //? if >=26.1 {
        /*HudElementRegistry.addLast(Ids.of(GemRender.MOD_ID, "spike_hud"), (graphics, tick) -> hud(graphics));
        *///?} else {
        HudRenderCallback.EVENT.register((graphics, tick) -> hud(graphics));
        //?}

        //? if direct {
        com.wf.gemrender.spike.DirectSpike.init();
        //?}
    }

    //? if >=26.1 {
    /*private static void hud(GuiGraphicsExtractor graphics) {
    *///?} else {
    private static void hud(GuiGraphics graphics) {
    //?}
        SpikeHud.render(graphics);

        //? if direct {
        com.wf.gemrender.spike.DirectSpike.gui(graphics);
        //?}
    }

    private static SpikeSource sourceOf(FabricClientCommandSource source) {
        return new SpikeSource() {
            @Override
            public void ok(Component message) {
                source.sendFeedback(message);
            }

            @Override
            public void fail(Component message) {
                source.sendError(message);
            }

            @Override
            public Vec3 position() {
                return source.getPosition();
            }
        };
    }
}
