package com.wf.gemrender.direct;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a stack looks like, per context: which model, which clip, where in it, and how it sits.
 *
 * <p>Everything is a function of the stack and the {@link ItemDisplayContext} because that is what real
 * item models need and what a fixed model-per-item cannot express. A gun is a different mesh in the
 * hand than in the inventory, is held differently in first person than in third, and animates on a
 * reload timer rather than the world clock; a helmet on the ground is the same model an entity wears.
 * None of that is GemRender's business to model, so all of it is a question asked of the mod.
 *
 * <p>Every method is called on the render thread, once per copy per frame, and must be cheap: this is
 * a lookup, not a place to import an asset or step a simulation.
 */
public interface ItemAppearance {
    /**
     * The model to draw, or {@code null} to draw nothing and let vanilla's own model show through.
     */
    @Nullable
    GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context);

    /**
     * The clip to sample, or {@code null} for the model at rest.
     */
    @Nullable
    default GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
        return null;
    }

    /**
     * Where in the clip this copy is, in seconds. Wrapped into the clip's own length by the caller.
     *
     * <p>A pure function of things that do not change within a frame, or copies flicker: two calls for
     * the same stack in the same frame must return the same instant, which is also what lets an
     * inventory of identical items share one palette.
     */
    default float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        return 0.0f;
    }

    /**
     * Applies this context's placement to the pose stack, before the model is queued.
     *
     * <p>Called with the stack vanilla handed the renderer, so the usual unit cube of an item is already
     * established. A glTF authored at world scale will be far too large here and wants scaling down; the
     * display block of the item's own JSON model cannot do it, because a builtin/entity model has no
     * geometry for vanilla to transform.
     *
     * <p>The origin is the CENTRE of that cube, not a corner: vanilla's own convention puts the origin at
     * a corner because a vanilla item is a block model living in {@code [0,1]^3}, and the renderer undoes
     * that before calling this so a glTF's own origin is the anchor. So an implementation that scales the
     * model to fit within half a unit of the origin -- and translates the model's centre of mass there if
     * it is not already -- fills the item cell and nothing more.
     *
     * <p>Fitting inside it matters on 26.1 and nowhere else: 26.1 renders each item into a scissored
     * atlas slot, so anything outside the cell is clipped away rather than merely overflowing the slot.
     * An item meant to overflow declares {@code "oversized_in_gui": true} in its client item JSON.
     */
    default void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
    }

    /**
     * Which of the model's variants this stack wears, if it declares any.
     *
     * <p>{@code model.variant(i)}. A camo, a team paint, a rarity finish: the same sheet and the same
     * batch, so a chest full of differently painted guns is still one draw. Asked per (stack, context)
     * like everything else here, so a gun can be one skin in the hand and another in the inventory.
     */
    default VariantUv variant(ItemStack stack, ItemDisplayContext context) {
        return VariantUv.NONE;
    }

    /**
     * A tint multiplied over the model's own colours. Opaque white leaves it alone.
     */
    default int tint(ItemStack stack, ItemDisplayContext context) {
        return 0xFFFFFFFF;
    }
}
