package com.wf.gemrender.mixin.direct26;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void gemrender$beginHand(CallbackInfo ci) {
        DirectRenderer.beginSink(DirectPass.HAND);
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void gemrender$endHand(CallbackInfo ci) {

        Vanilla.intoMainTarget(DirectRenderer::endSink);
    }
}
