package com.wf.gemrender;

import net.minecraft.client.Minecraft;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
*///?}

/** {@link ShaderPackNotice} on NeoForge and Forge; Fabric registers it in {@code GemRenderFabric}. */
@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class ShaderPackNoticeEvents {
    private ShaderPackNoticeEvents() {
    }

    //? if neoforge {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ShaderPackNotice.tick(Minecraft.getInstance(), compatInstalled());
    }
    //?} else {
    /*@SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ShaderPackNotice.tick(Minecraft.getInstance(), compatInstalled());
        }
    }
    *///?}

    private static boolean compatInstalled() {
        return ModList.get().isLoaded("colorwheel") || ModList.get().isLoaded("irisflw");
    }
}
