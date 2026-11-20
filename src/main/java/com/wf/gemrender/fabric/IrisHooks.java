package com.wf.gemrender.fabric;

//? if iris {
import com.wf.gemrender.iris.IrisPbrBridge;
//?}
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class IrisHooks {
    private IrisHooks() {
    }

    //? if iris {
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> IrisPbrBridge
                .install(FabricLoader.getInstance()
                        .isModLoaded("iris")));
    }
    //?} else {
    /*public static void init() {
    }
    *///?}
}
