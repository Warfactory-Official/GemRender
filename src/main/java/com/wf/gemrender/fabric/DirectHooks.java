package com.wf.gemrender.fabric;

import java.util.Optional;

import dev.engine_room.flywheel.api.event.EndClientResourceReloadCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;

public final class DirectHooks {
    private DirectHooks() {
    }

    //? if direct {
    public static void init() {
        EndClientResourceReloadCallback.EVENT.register(DirectHooks::onEndClientResourceReload);
        registerItemRenderer();
    }

    private static void onEndClientResourceReload(Minecraft minecraft, ResourceManager resources,
            boolean first, Optional<Throwable> error) {
        if (error.isPresent()) {
            return;
        }

        com.wf.gemrender.direct.DirectReload.run();
    }
    //?} else {
    /*public static void init() {
    }
    *///?}

    // NeoForge registers the item renderer type in RegisterSpecialModelRendererEvent; Fabric has no
    // event for it, so the vanilla id map is widened (gemrender.accesswidener).
    //? if direct && >=26.1 {
    /*private static void registerItemRenderer() {
        net.minecraft.client.renderer.special.SpecialModelRenderers.ID_MAPPER.put(
                com.wf.gemrender.direct.GemRenderItemRenderer.TYPE_ID,
                com.wf.gemrender.direct.GemRenderItemRenderer.Unbaked.MAP_CODEC);
    }
    *///?} elif direct {
    private static void registerItemRenderer() {
    }
    //?}
}
