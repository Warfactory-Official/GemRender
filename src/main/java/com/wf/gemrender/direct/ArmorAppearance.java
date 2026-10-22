package com.wf.gemrender.direct;

import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.NodeTable;
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
     * <p>{@code entity} is the wearer. The 26.1 hook has no wearer; 26.2 reads it from the render state
     * ({@code GemRenderArmorModel.WEARER}), or supplies {@code null} for non-living renderers.
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

    /** Child models drawn at this piece's socket nodes (posed), same light and tint. */
    default void attachments(@Nullable LivingEntity entity, ItemStack stack, EquipmentSlot slot, Attachments out) {
    }

    @FunctionalInterface
    interface Attachments {
        /**
         * @param socket node name in the piece's model; missing => skipped, warned once per model
         * @param pose   writes the child's node state (starts at rest); null = rest
         */
        void attach(GemRenderGltfModel child, String socket, @Nullable ChildPose pose);
    }

    @FunctionalInterface
    interface ChildPose {
        void pose(NodeTable table, float[] state);
    }
}
