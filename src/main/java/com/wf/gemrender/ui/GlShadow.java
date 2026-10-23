package com.wf.gemrender.ui;

import com.mojang.blaze3d.platform.GlStateManager;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Reads {@link GlStateManager}'s shadow copy instead of {@code glGet}: under Mesa glthread every
 * {@code glGet} is a round trip to the GL thread (18 of them measured 31 us/frame).
 */
final class GlShadow {
    private static final Object BLEND;
    private static final Object BLEND_MODE;
    private static final Object DEPTH;
    private static final Object DEPTH_MODE;
    private static final Object CULL_MODE;
    private static final Object SCISSOR_MODE;
    private static final VarHandle ENABLED;
    private static final VarHandle SRC_RGB;
    private static final VarHandle DST_RGB;
    private static final VarHandle SRC_ALPHA;
    private static final VarHandle DST_ALPHA;
    private static final VarHandle DEPTH_MASK;
    private static final VarHandle LAST_PROGRAM;

    static {
        try {
            MethodHandles.Lookup gsm = MethodHandles.privateLookupIn(GlStateManager.class, MethodHandles.lookup());
            Class<?> blendClass = Class.forName("com.mojang.blaze3d.platform.GlStateManager$BlendState");
            Class<?> depthClass = Class.forName("com.mojang.blaze3d.platform.GlStateManager$DepthState");
            Class<?> cullClass = Class.forName("com.mojang.blaze3d.platform.GlStateManager$CullState");
            Class<?> scissorClass = Class.forName("com.mojang.blaze3d.platform.GlStateManager$ScissorState");
            Class<?> boolClass = Class.forName("com.mojang.blaze3d.platform.GlStateManager$BooleanState");
            BLEND = gsm.findStaticVarHandle(GlStateManager.class, "BLEND", blendClass).get();
            DEPTH = gsm.findStaticVarHandle(GlStateManager.class, "DEPTH", depthClass).get();
            Object cull = gsm.findStaticVarHandle(GlStateManager.class, "CULL", cullClass).get();
            Object scissor = gsm.findStaticVarHandle(GlStateManager.class, "SCISSOR", scissorClass).get();
            MethodHandles.Lookup blend = MethodHandles.privateLookupIn(blendClass, MethodHandles.lookup());
            MethodHandles.Lookup depth = MethodHandles.privateLookupIn(depthClass, MethodHandles.lookup());
            MethodHandles.Lookup cullLookup = MethodHandles.privateLookupIn(cullClass, MethodHandles.lookup());
            MethodHandles.Lookup scissorLookup = MethodHandles.privateLookupIn(scissorClass, MethodHandles.lookup());
            BLEND_MODE = blend.findVarHandle(blendClass, "mode", boolClass).get(BLEND);
            DEPTH_MODE = depth.findVarHandle(depthClass, "mode", boolClass).get(DEPTH);
            CULL_MODE = cullLookup.findVarHandle(cullClass, "enable", boolClass).get(cull);
            SCISSOR_MODE = scissorLookup.findVarHandle(scissorClass, "mode", boolClass).get(scissor);
            ENABLED = MethodHandles.privateLookupIn(boolClass, MethodHandles.lookup())
                    .findVarHandle(boolClass, "enabled", boolean.class);
            SRC_RGB = blend.findVarHandle(blendClass, "srcRgb", int.class);
            DST_RGB = blend.findVarHandle(blendClass, "dstRgb", int.class);
            SRC_ALPHA = blend.findVarHandle(blendClass, "srcAlpha", int.class);
            DST_ALPHA = blend.findVarHandle(blendClass, "dstAlpha", int.class);
            DEPTH_MASK = depth.findVarHandle(depthClass, "mask", boolean.class);
            LAST_PROGRAM = MethodHandles.privateLookupIn(net.minecraft.client.renderer.ShaderInstance.class,
                            MethodHandles.lookup())
                    .findStaticVarHandle(net.minecraft.client.renderer.ShaderInstance.class, "lastProgramId", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private GlShadow() {
    }

    static boolean blend() {
        return (boolean) ENABLED.get(BLEND_MODE);
    }

    static int srcRgb() {
        return (int) SRC_RGB.get(BLEND);
    }

    static int dstRgb() {
        return (int) DST_RGB.get(BLEND);
    }

    static int srcAlpha() {
        return (int) SRC_ALPHA.get(BLEND);
    }

    static int dstAlpha() {
        return (int) DST_ALPHA.get(BLEND);
    }

    static boolean depthTest() {
        return (boolean) ENABLED.get(DEPTH_MODE);
    }

    static boolean depthMask() {
        return (boolean) DEPTH_MASK.get(DEPTH);
    }

    static boolean cull() {
        return (boolean) ENABLED.get(CULL_MODE);
    }

    static boolean scissor() {
        return (boolean) ENABLED.get(SCISSOR_MODE);
    }

    /** Program vanilla believes is bound; -1 => none (it rebinds before its next draw). */
    static int program() {
        return (int) LAST_PROGRAM.get();
    }
}
