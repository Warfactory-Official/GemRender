package com.wf.gemrender.mixin.ui;

import org.spongepowered.asm.mixin.Mixin;

//? if >=26.2 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;

// 26.2: no immediate batch to flush; Canvas orders its GUI layer by render-state placement. Empty => config valid.
@Mixin(GuiGraphicsExtractor.class)
public interface BufferSourceAccessor {
}
*///?} else {
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MultiBufferSource.BufferSource.class)
public interface BufferSourceAccessor {
    @Accessor("startedBuilders")
    Map<RenderType, BufferBuilder> gemrender$startedBuilders();
}
//?}
