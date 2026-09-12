package com.wf.gemrender.mixin.direct26;

import com.mojang.blaze3d.platform.Lighting;
import com.wf.gemrender.direct.DirectVanilla;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lighting.class)
public class LightingMixin {
    @Inject(method = "setupFor(Lcom/mojang/blaze3d/platform/Lighting$Entry;)V", at = @At("HEAD"))
    private void gemrender$recordEntry(Lighting.Entry entry, CallbackInfo ci) {
        DirectVanilla.recordLightEntry(entry);
    }
}
