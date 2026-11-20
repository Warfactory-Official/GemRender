package com.wf.gemrender.mixin.ui;

import com.wf.gemrender.ui.SharpText;
import com.wf.gemrender.ui.SharpTextScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void gemrender$sharpOn(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SharpText.screen(this instanceof SharpTextScreen);
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void gemrender$sharpOff(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SharpText.screen(false);
    }
}
