package com.wf.gemrender.direct;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

//? if <26.1 {
//?}
//? if >=26.1 {
/*import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
*///?}

/**
 * Draws a GemRender model wherever vanilla draws an item: in a hand, in an inventory, on the ground, in
 * a frame, on a head.
 *
 * <p>All of those go through one hook on every version, and the hook hands over the display context, so
 * a single renderer covers the lot and the context says which one is being asked for. The only thing
 * this class decides for itself is which {@link DirectPass} a context belongs to -- the first-person
 * hand is drawn after the level under its own projection and cannot batch with the rest.
 *
 * <h2>Registering</h2>
 *
 * <p>Two pieces on every version, but vanilla changed which of them is Java and which is data.
 *
 * <p><b>Everywhere.</b> Build the renderer and give it a name:
 *
 * <pre>{@code
 * public static final ResourceLocation RIFLE = ResourceLocation.fromNamespaceAndPath("mymod", "rifle");
 *
 * GemRenderItemRenderer.register(RIFLE, GemRenderItemRenderer.of(model, clip));
 * }</pre>
 *
 * <p><b>Before 26.1</b>, the item then claims it, and an item model JSON inheriting
 * {@code builtin/entity} is what makes a baked model report {@code isCustomRenderer()} and route here
 * at all. Without it the item silently draws as a missing model:
 *
 * <pre>{@code
 * { "parent": "builtin/entity", "gui_light": "side" }
 *
 * public void initializeClient(Consumer<IClientItemExtensions> consumer) {
 * 	 consumer.accept(new IClientItemExtensions() {
 * 		 public BlockEntityWithoutLevelRenderer getCustomRenderer() {
 * 			 return GemRenderItemRenderer.get(RIFLE);
 *         }
 *     });
 * }
 * }</pre>
 *
 * <p>{@code gui_light: side} asks for the three-dimensional item lighting rather than the flat lighting
 * a sprite gets; a model shaded flat in the inventory looks like a lighting bug and is that line.
 *
 * <p><b>On 26.1</b> there is no {@code getCustomRenderer} and no {@code builtin/entity}: an item model
 * names its renderer in data, and the item needs no client code at all.
 *
 * <pre>{@code
 * // assets/mymod/items/rifle.json
 * {
 *   "model": {
 * 	 "type": "minecraft:special",
 * 	 "base": "mymod:item/rifle_base",
 * 	 "model": { "type": "gemrender:model", "key": "mymod:rifle" }
 *   }
 * }
 * }</pre>
 *
 * <p>{@code base} is an ordinary block/item model, used only for the display transforms and the
 * particle texture; the geometry drawn is GemRender's.
 */
//? if >=26.1 {
/*public class GemRenderItemRenderer implements SpecialModelRenderer<ItemStack> {
 *///?} else {
public class GemRenderItemRenderer extends BlockEntityWithoutLevelRenderer {
//?}
    /**
     * Renderers by name.
     *
     * <p>Populated the same way on every version; read back by name only on 26.1, where the item model
     * JSON is what names one. Before it, {@link #get} is a convenience for the mod's own
     * {@code getCustomRenderer} and nothing else consults this map -- which is deliberate, so that one
     * registration call is correct everywhere and only the claiming half differs.
     */
    private static final Map<ResourceLocation, GemRenderItemRenderer> REGISTRY = new HashMap<>();

    private final ItemAppearance appearance;

    //? if >=26.1 {
	/*public GemRenderItemRenderer(ItemAppearance appearance) {
		this.appearance = appearance;
	}
	*///?} else {
    public GemRenderItemRenderer(ItemAppearance appearance) {
        super(Minecraft.getInstance()
                        .getBlockEntityRenderDispatcher(),
                Minecraft.getInstance()
                        .getEntityModels());
        this.appearance = appearance;
    }
    //?}

    /**
     * The fixed case: one model and one clip, animated on the world clock, whatever the context.
     */
    public static GemRenderItemRenderer of(GemRenderGltfModel model, @Nullable GltfAnimation clip) {
        return new GemRenderItemRenderer(new ItemAppearance() {
            @Override
            public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                return model;
            }

            @Override
            public GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
                return clip;
            }

            @Override
            public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null) {
                    return 0.0f;
                }
                return (minecraft.level.getGameTime() + partialTick) / 20.0f;
            }
        });
    }

    /**
     * Names a renderer, and hands it straight back so the call can be an initialiser.
     */
    public static GemRenderItemRenderer register(ResourceLocation key, GemRenderItemRenderer renderer) {
        REGISTRY.put(key, renderer);
        return renderer;
    }

    /**
     * The renderer registered under {@code key}, or {@code null}.
     */
    @Nullable
    public static GemRenderItemRenderer get(ResourceLocation key) {
        return REGISTRY.get(key);
    }

    /**
     * Which batch a context belongs in.
     *
     * <p>The hand is its own pass because it is rendered after the level with a separate projection, so
     * that a long model held close to the camera cannot clip into the world. Everything else that
     * happens inside the level -- third person, the ground, a frame, a head -- shares the entity pass.
     *
     * <p>On 26.1 this is advisory: {@code DirectRenderer} knows which of vanilla's sinks is open and
     * uses that instead, which is strictly better information. It is still computed and passed, so the
     * two versions take the same path and a sink that is somehow not open falls back to something
     * sensible.
     */
    private static DirectPass passFor(ItemDisplayContext context) {
        return switch (context) {
            case GUI -> DirectPass.GUI;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> DirectPass.HAND;
            default -> DirectPass.LEVEL;
        };
    }
    //?}

    //? if >=26.1 {
	/*// The id the item model JSON names this renderer's TYPE by. One per mod, not one per model: the
	// model is chosen by the "key" field inside it, which is what REGISTRY is keyed on.
	public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID,
			"model");

	// The stack itself is the argument. SpecialModelRenderer#submit is handed only this, so anything
	// ItemAppearance is asked about the stack has to come through here -- and vanilla folds the
	// argument into the model identity the GUI item atlas caches on, so two stacks that differ get
	// their own atlas slot rather than sharing one.
	@Override
	@Nullable
	public ItemStack extractArgument(ItemStack stack) {
		return stack;
	}

	// hasFoil and outlineColor are deliberately ignored. A GemRender model is drawn whole with its own
	// materials through its own program: there is no vanilla render type to layer a glint over and no
	// outline buffer to write into, the same as on every earlier version where this hook was not given
	// them either.
	@Override
	public void submit(@Nullable ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
			int light, int overlay, boolean hasFoil, int outlineColor) {
		if (stack == null) {
			return;
		}
		draw(stack, DirectRenderer.itemContext(), pose, light, overlay);
	}

	// The corners of the cell a vanilla item occupies, in the frame vanilla measures them in: [0,1]^3,
	// the block-model box, before the translate(-0.5, -0.5, -0.5) that #draw undoes. Vanilla uses these
	// for the GUI bounding box and to decide whether an item is oversized enough to need its own render
	// target rather than an atlas slot; a glTF is arbitrary and GemRender does not measure it, so this
	// claims exactly the space a vanilla item occupies.
	//
	// An asset that overflows that box is clipped to it on 26.1, where each item is drawn into a
	// scissored GuiItemAtlas slot -- there is no such clip on 1.20.1 or 1.21.1. Fitting the model is
	// what ItemAppearance#transform is for; an item that is meant to overflow declares
	// "oversized_in_gui": true in its client item JSON, which is what moves it off the atlas.
	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		for (int i = 0; i < 8; i++) {
			output.accept(new Vector3f((i & 1) == 0 ? 0.0f : 1.0f, (i & 2) == 0 ? 0.0f : 1.0f,
					(i & 4) == 0 ? 0.0f : 1.0f));
		}
	}

	// The data half of registration: an item model of type gemrender:model carries the key, and this
	// resolves it to the renderer registered under that name during client setup.
	public record Unbaked(ResourceLocation key) implements SpecialModelRenderer.Unbaked<ItemStack> {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
				.group(ResourceLocation.CODEC.fieldOf("key")
						.forGetter(Unbaked::key))
				.apply(instance, Unbaked::new));

		@Override
		@Nullable
		public SpecialModelRenderer<ItemStack> bake(SpecialModelRenderer.BakingContext context) {
			GemRenderItemRenderer renderer = get(key);
			if (renderer == null) {
				// Null makes vanilla fall back to the missing model, which is the honest picture: the
				// item asked for a renderer nobody registered.
				GemRender.LOGGER.warn("An item model asks for the GemRender renderer '{}', which is not "
						+ "registered. Call GemRenderItemRenderer.register with that name during client "
						+ "setup.", key);
			}
			return renderer;
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
	*///?}

    //? if <26.1 {
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        draw(stack, context, pose, light, overlay);
    }

    /**
     * Whether this stack's appearance moves, and so whether a cached picture of it goes stale.
     *
     * <p>Only 26.1 asks. It renders each distinct item model once into a {@code GuiItemAtlas} slot and
     * blits that wherever the item appears, and a slot is only redrawn if the render state was marked
     * animated -- which vanilla does for a foil and for nothing else. An animated GemRender model left
     * unmarked is drawn on the frame its slot is allocated and then frozen, for the life of the atlas:
     * the inventory shows one still of a model that is moving everywhere else, with nothing logged.
     *
     * <p>Answered from the clip rather than assumed, so a static model still gets the cheap path it
     * should: a slot it keeps.
     */
    public boolean animates(ItemStack stack, ItemDisplayContext context) {
        return appearance.clip(stack, context) != null;
    }

    private void draw(ItemStack stack, ItemDisplayContext context, PoseStack pose, int light,
                      int overlay) {
        GemRenderGltfModel model = appearance.model(stack, context);
        if (model == null) {
            return;
        }

        float partialTick = Vanilla.partialTick();

        pose.pushPose();
        try {
            // Vanilla hands an item renderer the cell with its ORIGIN AT A CORNER: every version applies
            // translate(-0.5, -0.5, -0.5) after the display transform, because a vanilla item is a block
            // model whose quads live in [0,1]^3. A glTF is authored around its own origin, so without
            // this every GemRender item is drawn half a cell off along all three axes -- rotated by
            // whatever the display transform is, so it reads as an odd offset rather than as a shift.
            //
            // It was invisible until 26.1. Nothing clips a GUI item on 1.20.1 or 1.21.1, so the model
            // simply drew outside its 16x16 box and the eye took the result for the item; 26.1 renders
            // each item into a GuiItemAtlas slot under a scissor, and the same half-cell offset is
            // clipped away to a sliver. Correcting it here -- rather than in each ItemAppearance -- is
            // what makes one asset look the same on all three.
            pose.translate(0.5f, 0.5f, 0.5f);

            appearance.transform(stack, context, pose);

            DirectRenderer.submit(model, appearance.clip(stack, context),
                    appearance.seconds(stack, context, partialTick), pose.last()
                            .pose(),
                    light, overlay, appearance.tint(stack, context), passFor(context),
                    appearance.variant(stack, context));
        } finally {
            pose.popPose();
        }
    }
}
