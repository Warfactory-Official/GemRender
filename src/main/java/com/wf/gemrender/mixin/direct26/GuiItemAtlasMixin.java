package com.wf.gemrender.mixin.direct26;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiItemAtlas.class)
public class GuiItemAtlasMixin {
    @Inject(method = "drawToSlot(IIZLnet/minecraft/client/renderer/item/ItemStackRenderState;)V",
            at = @At("HEAD"))
    private void gemrender$beginSlot(int slotX, int slotY, boolean clear, ItemStackRenderState item,
                                     CallbackInfo ci) {
        DirectRenderer.beginSink(DirectPass.GUI);
    }

    @Inject(method = "drawToSlot(IIZLnet/minecraft/client/renderer/item/ItemStackRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableScissorForRenderTypeDraws()V"))
    private void gemrender$endSlot(int slotX, int slotY, boolean clear, ItemStackRenderState item,
                                   CallbackInfo ci) {

        Vanilla.intoOutputOverride(DirectRenderer::endSink);
    }
}
