package com.wf.gemrender.texture;

import com.mojang.blaze3d.platform.NativeImage;

public final class Pixels {
    private Pixels() {
    }

    public static int get(NativeImage image, int x, int y) {
        //? if >=26.1 {
        /*return net.minecraft.util.ARGB.toABGR(image.getPixel(x, y));
         *///?} else {
        return image.getPixelRGBA(x, y);
        //?}
    }

    public static void set(NativeImage image, int x, int y, int abgr) {
        //? if >=26.1 {
        /*image.setPixelABGR(x, y, abgr);
         *///?} else {
        image.setPixelRGBA(x, y, abgr);
        //?}
    }
}
