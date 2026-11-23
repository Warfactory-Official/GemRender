package com.wf.gemrender.mixin.ui;

import org.spongepowered.asm.mixin.Mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

@Mixin(MultiBufferSource.BufferSource.class)
public interface BufferSourceAccessor {
    //? if >=1.21 {
    @Accessor("startedBuilders")
    Map<RenderType, BufferBuilder> gemrender$startedBuilders();
    //?} else {
    /*@Accessor("startedBuffers")
    Set<BufferBuilder> gemrender$startedBuilders();
    *///?}
}
