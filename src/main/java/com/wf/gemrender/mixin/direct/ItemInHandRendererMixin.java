package com.wf.gemrender.mixin.direct;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void gemrender$flushDirect(CallbackInfo ci) {
        DirectRenderer.flush(DirectPass.HAND);
    }
}
