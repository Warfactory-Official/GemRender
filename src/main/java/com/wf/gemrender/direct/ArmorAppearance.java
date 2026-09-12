package com.wf.gemrender.direct;

import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Which model a piece of armour draws, per slot.
 *
 * <p>One model per slot rather than one model with per-slot bone visibility, because vanilla asks four
 * separate times -- once per equipment slot -- and a model returned for two of them would be drawn
 * twice. Returning {@code null} for a slot draws nothing there, which is how a helmet-only item says so.
 */
public interface ArmorAppearance {
    /**
     * The model this piece draws, or {@code null} for none.
     *
     * <p>{@code entity} is the wearer, and is {@code null} on 26.1 and only there. Armour is chosen
     * during the submit phase now, which is handed the wearer's {@code HumanoidRenderState} and not the
     * wearer -- the entity is extracted a phase earlier and deliberately not carried forward, so there
     * is no truthful way to produce one here. Everything on the stack is still available; a mod that
     * varies a model by the wearer rather than by the item has to carry what it needs on the item.
     */
    @Nullable
    GemRenderGltfModel model(@Nullable LivingEntity entity, ItemStack stack, EquipmentSlot slot);

    /**
     * Which of the model's variants this piece wears. See {@link ItemAppearance#variant}.
     */
    default VariantUv variant(@Nullable LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
        return VariantUv.NONE;
    }

    /**
     * A tint multiplied over the model's own colours; leather dye, team colour, opaque white for none.
     */
    default int tint(@Nullable LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
        return 0xFFFFFFFF;
    }
}
