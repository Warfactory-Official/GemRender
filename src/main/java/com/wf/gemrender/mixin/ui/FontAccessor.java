package com.wf.gemrender.mixin.ui;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Font.class)
public interface FontAccessor {
    @Invoker("getFontSet")
    FontSet gemrender$fontSet(ResourceLocation font);

    @Accessor("filterFishyGlyphs")
    boolean gemrender$filterFishyGlyphs();
}
