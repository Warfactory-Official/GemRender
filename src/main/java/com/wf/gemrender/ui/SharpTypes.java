package com.wf.gemrender.ui;

//? if <26.1 {
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

abstract class SharpTypes extends RenderType {
    private SharpTypes() {
        super("", null, null, 0, false, false, null, null);
    }

    static Object shader(Supplier<ShaderInstance> instance) {
        return new RenderStateShard.ShaderStateShard(instance);
    }

    static RenderType build(boolean intensity, Object shader, ResourceLocation page) {
        return RenderType.create(
                intensity ? "gemrender_sharp_text_intensity" : "gemrender_sharp_text",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                786432,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState((RenderStateShard.ShaderStateShard) shader)
                        .setTextureState(new RenderStateShard.TextureStateShard(page, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false));
    }
}
//?}
