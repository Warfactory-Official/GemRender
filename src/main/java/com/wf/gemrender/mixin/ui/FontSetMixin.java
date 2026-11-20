package com.wf.gemrender.mixin.ui;

import com.wf.gemrender.ui.CanvasFont;
import com.wf.gemrender.ui.SharpText;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FontSet.class)
abstract class FontSetMixin {
    @Inject(method = "resetTextures", at = @At("HEAD"))
    private void gemrender$invalidate(CallbackInfo ci) {
        CanvasFont.invalidate();
        SharpText.invalidate();
    }
}
