package com.wf.gemrender.mixin.direct26;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.direct.DirectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public class ItemStackRenderStateMixin {

    @Shadow
    ItemDisplayContext displayContext;

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", at = @At("HEAD"))
    private void gemrender$beginContext(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                        int overlay, int outlineColor, CallbackInfo ci) {
        DirectRenderer.beginItemContext(displayContext);
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", at = @At("RETURN"))
    private void gemrender$endContext(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                      int overlay, int outlineColor, CallbackInfo ci) {
        DirectRenderer.endItemContext();
    }
}
