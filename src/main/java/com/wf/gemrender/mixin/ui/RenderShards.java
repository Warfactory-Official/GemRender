package com.wf.gemrender.mixin.ui;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

public abstract class RenderShards extends RenderType {
    private RenderShards() {
        super("", null, null, 0, false, false, null, null);
    }

    @Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
    public interface CompositeRenderTypeAccessor {
        @Accessor("state")
        RenderType.CompositeState gemrender$state();
    }

    @Mixin(RenderType.CompositeState.class)
    public interface CompositeStateAccessor {
        @Accessor("textureState")
        RenderStateShard.EmptyTextureStateShard gemrender$textureState();
    }

    @Mixin(RenderStateShard.EmptyTextureStateShard.class)
    public interface TextureShardAccessor {
        @Invoker("cutoutTexture")
        Optional<ResourceLocation> gemrender$cutoutTexture();
    }
}
