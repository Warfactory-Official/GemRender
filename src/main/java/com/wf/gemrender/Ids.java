package com.wf.gemrender;

import net.minecraft.resources.ResourceLocation;

/**
 * Builds resource locations in a way that holds on every node.
 *
 * <p>1.21 replaced {@code new ResourceLocation(..)} with static factories and deprecated the
 * constructor. Forge and NeoForge backport those factories onto 1.20.1, so a mod that only ever
 * builds against them never notices; <b>Fabric 1.20.1 does not</b>, and every call site turns into a
 * missing symbol there. The split is stated once here rather than at each of the twenty sites.
 *
 * <p>These cannot be a Stonecutter string replacement: replacements are reversible, so they have to
 * be injective across the whole project, and {@code ResourceLocation -> ResourceLocation} already claims
 * that token for 26.1.
 */
public final class Ids {

    private Ids() {
    }

    public static ResourceLocation of(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    /** A location in GemRender's own namespace. */
    public static ResourceLocation of(String path) {
        return of(GemRender.MOD_ID, path);
    }

    /** A location in Minecraft's namespace. */
    public static ResourceLocation vanilla(String path) {
        //? if >=1.21 {
        return ResourceLocation.withDefaultNamespace(path);
        //?} else {
        /*return new ResourceLocation(path);
        *///?}
    }

    /** A {@code namespace:path} string, which throws if it is not one. */
    public static ResourceLocation parse(String id) {
        //? if >=1.21 {
        return ResourceLocation.parse(id);
        //?} else {
        /*return new ResourceLocation(id);
        *///?}
    }
}
