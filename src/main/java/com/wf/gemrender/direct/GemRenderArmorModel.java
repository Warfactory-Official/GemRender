package com.wf.gemrender.direct;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.NodeTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.LinkedHashMap;
import java.util.Map;
//? if >=26.1 {
/*import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
 *///?}

/**
 * Draws a GemRender model as worn armour, following the entity's own animation.
 *
 * <p>Vanilla's armour hook wants a {@link HumanoidModel}, so this is one -- and being one is what makes
 * it work rather than a formality. {@code HumanoidArmorLayer} copies the wearer's animated part
 * transforms onto the model it is handed before rendering it, so by the time {@link #renderToBuffer}
 * runs, {@code head}, {@code body} and the four limbs already hold this frame's walk cycle. Those six
 * rotations are then written onto the glTF's own nodes and the model is posed from them, through the
 * same external-pose seam a vehicle's turret uses.
 *
 * <p>So the armour bends with the wearer without the asset carrying a walk clip, and a glTF clip of its
 * own can still be layered on later by writing the state before the limbs are applied.
 *
 * <h2>Registering</h2>
 *
 * <p>One hook, which keeps its name on every version and changes its arguments on 26.1 because the
 * wearer is no longer there to pass. {@link #prepare} is the same call either way.
 *
 * <p><b>Before 26.1:</b>
 *
 * <pre>{@code
 * public void initializeClient(Consumer<IClientItemExtensions> consumer) {
 * 	 consumer.accept(new IClientItemExtensions() {
 * 		 public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
 * 				 EquipmentSlot slot, HumanoidModel<?> original) {
 * 			 return MY_ARMOR.prepare(entity, stack, slot);
 *         }
 *     });
 * }
 * }</pre>
 *
 * <p><b>On 26.1</b> it is {@code getHumanoidArmorModel(ItemStack, EquipmentClientInfo.LayerType,
 * Model)}: armour is chosen in the submit phase, which is handed the wearer's
 * {@code HumanoidRenderState} rather than the wearer, and a layer type rather than a slot. So the mod
 * passes {@code null} for the entity and the slot it derives from the layer type -- and
 * {@link ArmorAppearance} is told, in its own javadoc, that its entity is null there and only there.
 *
 * <pre>{@code
 * public void initializeClient(Consumer<IClientItemExtensions> consumer) {
 * 	 consumer.accept(new IClientItemExtensions() {
 * 		 public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layer,
 * 				 Model original) {
 * 			 return MY_ARMOR.prepare(null, stack, slotFor(layer));
 *         }
 *     });
 * }
 * }</pre>
 */
//? if >=26.1 {
/*public class GemRenderArmorModel extends HumanoidModel<HumanoidRenderState> {
 *///?} else {
public class GemRenderArmorModel extends HumanoidModel<LivingEntity> {
//?}
    /**
     * The glTF node each vanilla part drives, by name.
     *
     * <p>Conventional names, overridable, because a glTF exporter writes whatever the artist called the
     * bone. A name that matches nothing is not an error: an asset that only models a helmet has no leg
     * bone, and demanding one would be worse than ignoring it.
     */
    public static final Map<String, String> DEFAULT_BONES = Map.of(
            "head", "head",
            "body", "body",
            "left_arm", "left_arm",
            "right_arm", "right_arm",
            "left_leg", "left_leg",
            "right_leg", "right_leg");

    private final ArmorAppearance appearance;
    private final Map<String, String> bones;

    /**
     * Per model, the resolved node slot for each vanilla part; {@code -1} where the asset has none.
     */
    private final Map<GemRenderGltfModel, int[]> bindings = new LinkedHashMap<>();

    private final Quaternionf scratchRotation = new Quaternionf();
    private final Quaternionf restRotation = new Quaternionf();

    @Nullable
    private LivingEntity entity;
    @Nullable
    private ItemStack stack;
    @Nullable
    private EquipmentSlot slot;

    /**
     * Whether the piece named by the last {@link #prepare} still has to be drawn.
     *
     * <p>Vanilla asks for one armour piece more than once. {@code HumanoidArmorLayer} renders the model
     * once per {@code ArmorMaterial.Layer} -- two for anything dyeable -- again for an armour trim, and
     * again for the enchantment glint, each time through {@code renderToBuffer}, because for vanilla's
     * own model those calls are different textures over the same cubes. A GemRender model carries its
     * own materials and is drawn whole, so every one of those is the same picture again: an enchanted
     * chestplate would cost two instances and, where the model has blended geometry, composite it twice.
     *
     * <p>{@link #prepare} is called once per piece, from the hook that hands vanilla this model, which
     * makes it the honest place to count from.
     */
    private boolean pending;

    public GemRenderArmorModel(ArmorAppearance appearance) {
        this(appearance, DEFAULT_BONES);
    }

    public GemRenderArmorModel(ArmorAppearance appearance, Map<String, String> bones) {
        // Any humanoid armour layer will do: all this needs from it is the six posable parts, which are
        // the same in every one. 26.1 replaced the inner/outer pair with one ArmorModelSet per wearer,
        // so there is no PLAYER_INNER_ARMOR to name -- the chest layer is the nearest thing to it.
        //? if >=26.1 {
		/*super(Minecraft.getInstance()
				.getEntityModels()
				.bakeLayer(ModelLayers.PLAYER_ARMOR.chest()));
		*///?} else {
        super(Minecraft.getInstance()
                .getEntityModels()
                .bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        //?}
        this.appearance = appearance;
        this.bones = Map.copyOf(bones);
    }

    /**
     * Records what is about to be drawn and returns this model, for returning straight out of
     * {@code getHumanoidArmorModel}.
     *
     * <p>The slot has to be captured here because {@link #renderToBuffer} is not told it, and one
     * renderer serves all four.
     */
    public GemRenderArmorModel prepare(@Nullable LivingEntity entity, ItemStack stack,
                                       EquipmentSlot slot) {
        this.entity = entity;
        this.stack = stack;
        this.slot = slot;
        this.pending = true;
        return this;
    }

    // 1.21 replaced the four float colour components with one packed int. GemRender ignores the
    // vertex colour on both -- it tints through ItemAppearance.tint -- so the two overrides differ
    // only in the parameters they discard, and both delegate to one body.
    //? if >=1.21 <26.1 {
    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int colour) {
        render(pose, light, overlay);
    }
    //?}

    //? if <1.21 {
	/*@Override
	public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
			float red, float green, float blue, float alpha) {
		render(pose, light, overlay);
	}
	*///?}

    //? if >=26.1 {
	/*// 26.1 made Model#renderToBuffer final and moved the draw behind a submit node, so this piece is
	// queued from EquipmentLayerRendererMixin instead of from an override. That mixin is the better
	// seam anyway: renderLayers is handed the PoseStack, the light and the wearer's render state in
	// one call and then submits the same model up to four times over -- once per material layer, again
	// for a trim, again for the glint -- so intercepting there both gives this everything it needs and
	// makes the repeat a non-event rather than something `pending` has to swallow.
	//
	// Returns whether the piece was GemRender's, which is what tells the mixin to cancel vanilla's
	// draw. A model that is not pending still returns true: it has already been queued this frame and
	// vanilla must not draw its cubes over the top.
	public boolean gemrender$submitArmour(PoseStack pose, Object state, int light, int overlay) {
		if (state instanceof HumanoidRenderState humanoid) {
			// Writes this frame's walk cycle onto head, body and the four limbs. Vanilla calls it
			// just before rendering a submitted model; here the model is never submitted, so it is
			// called here.
			setupAnim(humanoid);
		}
		if (!pending) {
			return true;
		}
		render(pose, light, overlay);
		return true;
	}
	*///?}

    private void render(PoseStack pose, int light, int overlay) {
        if (entity == null || stack == null || slot == null || !pending) {
            return;
        }
        pending = false;

        GemRenderGltfModel model = appearance.model(entity, stack, slot);
        if (model == null) {
            return;
        }

        NodeTable table = model.layout()
                .nodeTable();
        int[] binding = bindings.computeIfAbsent(model, key -> resolve(table));

        float[] state = table.newScratch();
        applyPart(table, state, binding[0], head);
        applyPart(table, state, binding[1], body);
        applyPart(table, state, binding[2], leftArm);
        applyPart(table, state, binding[3], rightArm);
        applyPart(table, state, binding[4], leftLeg);
        applyPart(table, state, binding[5], rightLeg);

        pose.pushPose();
        try {
            // Undo the flip LivingEntityRenderer put on the stack. Vanilla entity models are authored
            // Y-down and drawn under a scale of (-1, -1, 1); a glTF is Y-up, so without this it renders
            // upside down and mirrored -- which reads as a broken export rather than a space mismatch.
            pose.scale(-1.0f, -1.0f, 1.0f);

            DirectRenderer.submit(model, state, pose.last()
                            .pose(), light, overlay, appearance.tint(entity, stack, slot), DirectPass.LEVEL,
                    appearance.variant(entity, stack, slot));
        } finally {
            pose.popPose();
        }
    }

    /**
     * Writes one vanilla part's rotation onto its glTF node.
     *
     * <p>X and Y negate and Z does not, which is the same conversion GeckoLib's {@code matchModelPartRot}
     * makes and follows from the axis flip undone above. The rotation is composed onto the node's rest
     * orientation rather than replacing it, so a bone the artist angled keeps its angle and the wearer's
     * motion is added to it.
     */
    private void applyPart(NodeTable table, float[] state, int slotIndex, ModelPart part) {
        if (slotIndex < 0 || !table.isPosable(slotIndex)) {
            return;
        }

        table.restRotation(slotIndex, restRotation);
        scratchRotation.set(restRotation)
                .mul(new Quaternionf().rotationZYX(part.zRot, -part.yRot, -part.xRot));

        table.setRotation(state, slotIndex, scratchRotation);
    }

    private int[] resolve(NodeTable table) {
        String[] parts = {"head", "body", "left_arm", "right_arm", "left_leg", "right_leg"};
        int[] slots = new int[parts.length];
        int found = 0;

        for (int i = 0; i < parts.length; i++) {
            String node = bones.get(parts[i]);
            slots[i] = node == null ? -1 : table.slotOfName(node);
            if (slots[i] >= 0) {
                found++;
            }
        }

        if (found == 0) {
            GemRender.LOGGER.warn("A GemRender armour model has none of the nodes {}, so it will draw at "
                            + "rest and not follow the wearer. Pass a bone map naming the asset's own nodes.",
                    bones.values());
        }
        return slots;
    }
}
