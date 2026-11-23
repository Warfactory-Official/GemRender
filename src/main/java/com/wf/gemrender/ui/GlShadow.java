package com.wf.gemrender.ui;

import com.mojang.blaze3d.platform.GlStateManager;

import com.wf.gemrender.GemRender;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import static org.lwjgl.opengl.GL33C.*;
//? if <26.1 {
import net.minecraft.client.renderer.ShaderInstance;
//?}

/**
 * Reads {@link GlStateManager}'s shadow copy instead of {@code glGet}: under Mesa glthread every
 * {@code glGet} is a round trip to the GL thread (18 of them measured 31 us/frame).
 */
final class GlShadow {
    private static Object BLEND;
    private static Object BLEND_MODE;
    private static Object DEPTH;
    private static Object DEPTH_MODE;
    private static Object CULL_MODE;
    private static Object SCISSOR_MODE;
    private static VarHandle ENABLED;
    private static VarHandle SRC_RGB;
    private static VarHandle DST_RGB;
    private static VarHandle SRC_ALPHA;
    private static VarHandle DST_ALPHA;
    private static VarHandle DEPTH_MASK;
    //? if <26.1 {
    private static VarHandle LAST_PROGRAM;
    //?}

    private static boolean shadow;

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
            //? if <26.1 {
            LAST_PROGRAM = MethodHandles.privateLookupIn(ShaderInstance.class,
                            MethodHandles.lookup())
                    .findStaticVarHandle(ShaderInstance.class, "lastProgramId", int.class);
            //?}
            shadow = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            GemRender.LOGGER.warn("GlStateManager shadow state unreachable (obfuscated runtime?); canvas reads GL state with glGet", e);
        }
    }

    private GlShadow() {
    }

    static boolean blend() {
        return shadow ? (boolean) ENABLED.get(BLEND_MODE) : glIsEnabled(GL_BLEND);
    }

    static int srcRgb() {
        return shadow ? (int) SRC_RGB.get(BLEND) : glGetInteger(GL_BLEND_SRC_RGB);
    }

    static int dstRgb() {
        return shadow ? (int) DST_RGB.get(BLEND) : glGetInteger(GL_BLEND_DST_RGB);
    }

    static int srcAlpha() {
        return shadow ? (int) SRC_ALPHA.get(BLEND) : glGetInteger(GL_BLEND_SRC_ALPHA);
    }

    static int dstAlpha() {
        return shadow ? (int) DST_ALPHA.get(BLEND) : glGetInteger(GL_BLEND_DST_ALPHA);
    }

    static boolean depthTest() {
        return shadow ? (boolean) ENABLED.get(DEPTH_MODE) : glIsEnabled(GL_DEPTH_TEST);
    }

    static boolean depthMask() {
        return shadow ? (boolean) DEPTH_MASK.get(DEPTH) : glGetBoolean(GL_DEPTH_WRITEMASK);
    }

    static boolean cull() {
        return shadow ? (boolean) ENABLED.get(CULL_MODE) : glIsEnabled(GL_CULL_FACE);
    }

    static boolean scissor() {
        return shadow ? (boolean) ENABLED.get(SCISSOR_MODE) : glIsEnabled(GL_SCISSOR_TEST);
    }

    /** Program vanilla believes is bound; -1 => none (it rebinds before its next draw). */
    static int program() {
        //? if >=26.1 {
        /*return glGetInteger(GL_CURRENT_PROGRAM);
        *///?} else {
        return shadow ? (int) LAST_PROGRAM.get() : glGetInteger(GL_CURRENT_PROGRAM);
        //?}
    }
}
