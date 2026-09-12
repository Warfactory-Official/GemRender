package com.wf.gemrender.mixin.direct26;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.direct.GemRenderArmorModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/resources/ResourceLocation;II)V",
            at = @At("HEAD"), cancellable = true)
    private void gemrender$submitDirectArmour(EquipmentClientInfo.LayerType layerType,
                                              ResourceKey<EquipmentAsset> equipmentAssetId, Model<?> model, Object state, ItemStack itemStack,
                                              PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                                              ResourceLocation playerTextureOverride, int outlineColor, int order, CallbackInfo ci) {
        Model<?> replacement = IClientItemExtensions.of(itemStack)
                .getGenericArmorModel(itemStack, layerType, model);

        if (replacement instanceof GemRenderArmorModel armour && armour.gemrender$submitArmour(poseStack,
                state, lightCoords, OverlayTexture.NO_OVERLAY)) {
            ci.cancel();
        }
    }
}
