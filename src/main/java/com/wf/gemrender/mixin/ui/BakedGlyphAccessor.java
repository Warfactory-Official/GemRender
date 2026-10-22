package com.wf.gemrender.mixin.ui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=26.2 {
/*import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;

@Mixin(BakedSheetGlyph.class)
public interface BakedGlyphAccessor {
*///?} else {
import net.minecraft.client.gui.font.glyphs.BakedGlyph;

@Mixin(BakedGlyph.class)
public interface BakedGlyphAccessor {
//?}
    @Accessor("u0")
    float gemrender$u0();

    @Accessor("u1")
    float gemrender$u1();

    @Accessor("v0")
    float gemrender$v0();

    @Accessor("v1")
    float gemrender$v1();

    @Accessor("left")
    float gemrender$left();

    @Accessor("right")
    float gemrender$right();

    @Accessor("up")
    float gemrender$up();

    @Accessor("down")
    float gemrender$down();

    //? if >=26.2 {
    /*@Accessor("textureView")
    com.mojang.blaze3d.textures.GpuTextureView gemrender$textureView();

    @Accessor("renderTypes")
    net.minecraft.client.gui.font.GlyphRenderTypes gemrender$renderTypes();
    *///?}
}
