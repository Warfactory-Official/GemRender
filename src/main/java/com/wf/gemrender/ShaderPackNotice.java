package com.wf.gemrender;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Tells a player running an Iris or Oculus shader pack that GemRender's lights are off (the pack replaces the shaders
 * they are drawn in) and, without a Flywheel compat mod, that models draw on the CPU: Flywheel's backends stand down
 * under a pack unless Colorwheel (or Iris &amp; Oculus Flywheel Compat) keeps them on. Shown once per world join and again
 * whenever the pack is switched back on.
 */
public final class ShaderPackNotice {
    private static final MethodHandle GET_INSTANCE;
    private static final MethodHandle IN_USE;
    // Two ids, so the second toast queues instead of replacing the first; 1.20.1 has only its fixed set.
    //? if >=1.20.5 {
    private static final SystemToast.SystemToastId LIGHTING = new SystemToast.SystemToastId(10000L);
    private static final SystemToast.SystemToastId COLORWHEEL = new SystemToast.SystemToastId(10000L);
    //?} else {
    /*private static final SystemToast.SystemToastIds LIGHTING = SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING;
    private static final SystemToast.SystemToastIds COLORWHEEL = SystemToast.SystemToastIds.PERIODIC_NOTIFICATION;
    *///?}
    private static boolean shown;

    static {
        MethodHandle getInstance = null;
        MethodHandle inUse = null;
        try {
            // Oculus publishes the same API under Iris's package.
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstance = MethodHandles.publicLookup().findStatic(api, "getInstance", MethodType.methodType(api));
            inUse = MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError absent) {
            // neither Iris nor Oculus is installed
        }
        GET_INSTANCE = getInstance;
        IN_USE = inUse;
    }

    private ShaderPackNotice() {
    }

    /** Client tick on any loader; {@code compatInstalled} when Colorwheel or Iris &amp; Oculus Flywheel Compat is loaded. */
    public static void tick(Minecraft minecraft, boolean compatInstalled) {
        if (IN_USE == null) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null || !packInUse()) {
            shown = false;
            return;
        }
        if (!shown) {
            shown = true;
            toast(minecraft, LIGHTING, "GemRender: advanced lighting disabled", "Dynamic lights are off while shaders are on.");
            if (!compatInstalled) {
                showColorwheel(minecraft);
            }
        }
    }

    private static boolean packInUse() {
        try {
            return (boolean) IN_USE.invoke(GET_INSTANCE.invoke());
        } catch (Throwable e) {
            // IrisApi's getters do not throw; treat a changed API as no pack
            return false;
        }
    }

    //? if >=1.20.5 {
    private static void toast(Minecraft minecraft, SystemToast.SystemToastId id, String title, String body) {
    //?} else {
    /*private static void toast(Minecraft minecraft, SystemToast.SystemToastIds id, String title, String body) {
    *///?}
        //? if >=26.1 {
        /*SystemToast.addOrUpdate(minecraft.getToastManager(), id, Component.literal(title), Component.literal(body));
        *///?} else {
        SystemToast.addOrUpdate(minecraft.getToasts(), id, Component.literal(title), Component.literal(body));
        //?}
    }

    private static void showColorwheel(Minecraft minecraft) {
        toast(minecraft, COLORWHEEL, "GemRender: install Colorwheel", "Shader packs turn instancing off without it: low performance.");
        Component chat = Component.literal("[GemRender] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("A shader pack is active but Colorwheel is not installed. Under a shader pack"
                        + " Flywheel turns instancing off, so every model is drawn on the CPU and performance will be"
                        + " low. Install Colorwheel (modrinth.com/mod/colorwheel) to keep it on.")
                        .withStyle(ChatFormatting.YELLOW));
        //? if >=26.1 {
        /*minecraft.gui.getChat().addClientSystemMessage(chat);
        *///?} else {
        minecraft.gui.getChat().addMessage(chat);
        //?}
        GemRender.LOGGER.warn("Shader pack in use without Colorwheel: Flywheel instancing is off, models draw on the CPU");
    }
}
