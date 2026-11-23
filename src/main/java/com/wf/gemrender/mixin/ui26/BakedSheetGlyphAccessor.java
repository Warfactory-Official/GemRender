package com.wf.gemrender.mixin.ui26;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BakedSheetGlyph.class)
public interface BakedSheetGlyphAccessor {
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

    @Accessor("textureView")
    GpuTextureView gemrender$textureView();

    @Accessor("renderTypes")
    GlyphRenderTypes gemrender$renderTypes();
}
