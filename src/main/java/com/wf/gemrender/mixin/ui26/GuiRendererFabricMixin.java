package com.wf.gemrender.mixin.ui26;

import com.wf.gemrender.ui.CanvasPip;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiRenderer.class)
abstract class GuiRendererFabricMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<PictureInPictureRenderer<?>> gemrender$register(List<PictureInPictureRenderer<?>> renderers,
            GuiRenderState state, MultiBufferSource.BufferSource bufferSource, SubmitNodeCollector submit,
            FeatureRenderDispatcher dispatcher) {
        List<PictureInPictureRenderer<?>> all = new ArrayList<>(renderers);
        all.add(new CanvasPip(bufferSource));
        return all;
    }
}
