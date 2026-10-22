package com.wf.gemrender.mixin.direct;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {
    @Inject(method = "flush", at = @At("HEAD"))
    private void gemrender$flushDirect(CallbackInfo ci) {
        DirectRenderer.flushGui();
    }

    /** Queued items drawn under a later scissor => clipped (creative tab icons vs the inventory player box). */
    @Inject(method = "applyScissor", at = @At("HEAD"))
    private void gemrender$scissorFlush(CallbackInfo ci) {
        DirectRenderer.flush(DirectPass.GUI);
    }

    @Redirect(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void gemrender$itemFlush(GuiGraphics graphics) {
        DirectRenderer.beginItemFlush();
        try {
            graphics.flush();
        } finally {
            DirectRenderer.endItemFlush();
        }
    }
}
