package com.wf.gemrender.mixin.direct26;

import com.wf.gemrender.direct.GemRenderItemRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpecialModelWrapper.class)
public class SpecialModelWrapperMixin {
    @Shadow
    @Final
    private SpecialModelRenderer<?> specialRenderer;

    @Inject(method = "update(Lnet/minecraft/client/renderer/item/ItemStackRenderState;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/item/ItemModelResolver;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/multiplayer/ClientLevel;"
            + "Lnet/minecraft/world/entity/ItemOwner;I)V", at = @At("RETURN"))
    private void gemrender$markAnimated(ItemStackRenderState output, ItemStack item,
                                        ItemModelResolver resolver, ItemDisplayContext displayContext, ClientLevel level,
                                        ItemOwner owner, int seed, CallbackInfo ci) {
        if (specialRenderer instanceof GemRenderItemRenderer renderer
                && renderer.animates(item, displayContext)) {
            output.setAnimated();
        }
    }
}
