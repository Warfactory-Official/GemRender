package com.wf.gemrender.spike;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.direct.DirectStats;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wf.gemrender.direct.GemRenderArmorModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}
//? if fabric {
/*import com.wf.gemrender.Ids;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
*///?}
//? if fabric && >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?} elif fabric {
/*import com.wf.gemrender.fabric.HandRenderEvents;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.world.item.Item;
*///?}

//? if !fabric {
@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
//?}
public final class DirectSpike {
	private static final int COUNT = Integer.getInteger("gemrender.autodirect", 0);

	private static final String ASSET = System.getProperty("gemrender.directasset", "radar");

	private static final boolean SPREAD = Boolean.getBoolean("gemrender.directspread");

	private static final boolean FLUSH_PER_COPY = Boolean.getBoolean("gemrender.directflushper");

	private static final boolean DECORATE = Boolean.getBoolean("gemrender.directdecorate");

	private static final boolean ARMOR = Boolean.getBoolean("gemrender.directarmor");

	private static final int ARMOR_RENDERS = 3;

	private static final boolean HAND = Boolean.getBoolean("gemrender.directhand");

	private static final boolean TOTEM = Boolean.getBoolean("gemrender.directtotem");

	/** Copies as scaled item_display entities over a platform: drawn by entity rendering, shadow pass included. */
	private static final boolean ENTITY = Boolean.getBoolean("gemrender.directentity");

	private static long totemArmedAt = -40L;

	private static final boolean INTERLEAVE = Boolean.getBoolean("gemrender.directinterleave");

	//? if >=26.1 {
	/*private static final boolean ITEM_GRID = true;
*///?} else {
	private static final boolean ITEM_GRID = Boolean.getBoolean("gemrender.directitem");
	//?}

	public static boolean needsHand() {
		return (HAND || SCREEN) && COUNT > 0;
	}

	/** No clip: on 26.1 the GUI item atlas keeps the slot instead of redrawing it each frame. */
	private static final boolean STATIC = Boolean.getBoolean("gemrender.directstatic");

	/** Spike stacks in the hotbar, creative inventory held open: the creative-UI row. */
	private static final boolean SCREEN = Boolean.getBoolean("gemrender.directscreen");

	public static boolean wantsScreen() {
		return SCREEN && COUNT > 0;
	}

	private static java.util.List<String> hotbarCommands() {
		java.util.List<String> commands = new java.util.ArrayList<>();
		if (wantsScreen()) {
			for (int i = 0; i < Math.min(COUNT, 9); i++) {
				commands.add("item replace entity @s hotbar." + i + " with gemrender:spike");
			}
		}
		return commands;
	}

	public static void holdScreen(Minecraft mc) {
		if (!wantsScreen() || mc.player == null || !mc.player.getAbilities().instabuild
				|| mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
			return;
		}
		mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player,
				mc.player.connection.enabledFeatures(), mc.options.operatorItemsTab().get()));
	}

	public static java.util.List<String> stagingCommands(net.minecraft.core.BlockPos origin) {
		java.util.List<String> commands = new java.util.ArrayList<>(hotbarCommands());
		if (!ENTITY || COUNT <= 0) {
			return commands;
		}
		int x = origin.getX();
		int y = origin.getY();
		int z = origin.getZ();
		commands.add("kill @e[type=minecraft:item_display]");
		commands.add("fill " + (x - 24) + " " + (y - 1) + " " + (z - 24) + " " + (x + 24) + " " + (y - 1) + " "
				+ (z + 24) + " minecraft:smooth_stone");
		for (int i = 0; i < COUNT; i++) {
			commands.add("summon minecraft:item_display " + (x - 12 + i * 6) + " " + (y + 4) + " " + (z + 12)
					+ " {item:{id:\"gemrender:spike\",count:1},item_display:\"fixed\",transformation:"
					+ "{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],"
					+ "scale:[6f,6f,6f]}}");
		}
		return commands;
	}

	private static int copy;

	private static final boolean DIRECT_BLEND = Boolean.getBoolean("gemrender.directblend");

	private record BlendAssets(GemRenderGltfModel model, GltfAnimation clip, float timeB, GltfAnimation swing,
			com.wf.gemrender.gltf.blend.AdditiveReference rest, org.joml.Vector4f sphere) {
	}

	@Nullable
	private static BlendAssets blendAssets;

	private static BlendAssets blendAssets(GemRenderGltfModel model) {
		if (blendAssets == null || blendAssets.model() != model) {
			com.wf.gemrender.gltf.NodeTable table = model.layout()
					.nodeTable();
			GltfAnimation clip = model.animationOrAny("");
			blendAssets = new BlendAssets(model, clip, BlendVisual.farthestFrom(table, clip, 0.0f),
					GltfAnimation.procedural("swing", com.wf.gemrender.gltf.NodeSwing.open(table,
							BlendVisual.defaultNode(table), 0.0f, 0.0f, 1.0f, (float) Math.toRadians(35.0))),
					com.wf.gemrender.gltf.blend.AdditiveReference.rest(table), new org.joml.Vector4f());
			model.bounds()
					.evaluate(model.restPalette(), blendAssets.sphere());
		}
		return blendAssets;
	}

	private static GemRenderItemRenderer renderer;

	private static GemRenderArmorModel armor;

	private static int guiDraws;
	private static int guiPalettes;
	private static int guiInstances;
	private static int levelDraws;

	private DirectSpike() {
	}

	private static final boolean VARIANTS = Boolean.getBoolean("gemrender.autovariants");

	private static final int VARIANT_INDEX = Integer.getInteger("gemrender.autovariantindex", -1);

	private static ResourceLocation asset() {
		if (VARIANTS) {
			return SpikeAssets.RADAR_SKINS;
		}
		return switch (ASSET) {
			case "rig" -> SpikeAssets.RIG;
			case "morph" -> SpikeAssets.MORPH;
			case "glass" -> SpikeAssets.GLASS;
			case "pbr" -> SpikeAssets.PBR;
			default -> ASSET.indexOf(':') > 0 ? com.wf.gemrender.Ids.parse(ASSET) : SpikeAssets.RADAR;
		};
	}

	@Nullable
	private static GemRenderGltfModel model() {
		return SpikeAssets.model(asset());
	}

	public static GemRenderItemRenderer itemRenderer() {
		return renderer();
	}

	private static GemRenderItemRenderer renderer() {
		if (renderer == null) {
			renderer = new GemRenderItemRenderer(new ItemAppearance() {
				@Override
				public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
					return DirectSpike.model();
				}

				@Override
				public com.wf.gemrender.texture.VariantUv variant(ItemStack stack,
						ItemDisplayContext context) {
					GemRenderGltfModel model = DirectSpike.model();
					if (model == null) {
						return com.wf.gemrender.texture.VariantUv.NONE;
					}
					return model.variant(VARIANT_INDEX >= 0 ? VARIANT_INDEX
							: copy % model.variantCount());
				}

				@Override
				public GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
					GemRenderGltfModel model = DirectSpike.model();
					return model == null || STATIC ? null : model.animationOrAny("");
				}

				@Override
				public boolean blend(ItemStack stack, ItemDisplayContext context, float partialTick,
						com.wf.gemrender.gltf.blend.AnimationBlend out) {
					GemRenderGltfModel model = DirectSpike.model();
					if (!DIRECT_BLEND || model == null) {
						return false;
					}
					BlendAssets assets = blendAssets(model);
					float w = COUNT <= 1 ? 0.5f : copy / (float) (COUNT - 1);
					out.override(assets.clip(), 0.0f, 1.0f - w);
					out.override(assets.clip(), assets.timeB(), w);
					out.additive(assets.swing(), 1.0f, w, com.wf.gemrender.gltf.blend.BlendMask.ALL, assets.rest());
					return true;
				}

				@Override
				public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
					Minecraft minecraft = Minecraft.getInstance();
					if (minecraft.level == null) {
						return 0.0f;
					}
					float now = (minecraft.level.getGameTime() + partialTick) / 20.0f;

					return SPREAD ? now + copy / 128.0f : now;
				}

				@Override
				public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {

					GemRenderGltfModel model = DirectSpike.model();
					if (model == null) {
						return;
					}
					org.joml.Vector4fc sphere = DIRECT_BLEND ? blendAssets(model).sphere() : model.model()
							.boundingSphere();
					float radius = Math.max(0.001f, sphere.w());
					float scale = 0.5f / radius;
					pose.scale(scale, scale, scale);

					pose.translate(-sphere.x(), -sphere.y(), -sphere.z());
				}
			});
		}
		return renderer;
	}

	private static GemRenderArmorModel armor() {
		if (armor == null) {
			armor = new GemRenderArmorModel((entity, stack, slot) -> model());
		}
		return armor;
	}

	private static void draw(ItemStack stack, ItemDisplayContext context, PoseStack pose,
			@Nullable MultiBufferSource buffers, int light) {
		pose.pushPose();
		try {

			pose.translate(-0.5f, -0.5f, -0.5f);
			drawInCell(stack, context, pose, buffers, light);
		} finally {
			pose.popPose();
		}
	}

	private static void drawInCell(ItemStack stack, ItemDisplayContext context, PoseStack pose,
			@Nullable MultiBufferSource buffers, int light) {
		//? if >=26.1 {
		/*DirectRenderer.beginItemContext(context);
		try {
			renderer().submit(stack, pose, null, light, OverlayTexture.NO_OVERLAY, false, 0);
		} finally {
			DirectRenderer.endItemContext();
		}
*///?} else {
		renderer().renderByItem(stack, context, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		//?}
	}

	private static void drawArmor(PoseStack pose, MultiBufferSource buffers, int light) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		GemRenderArmorModel model = armor().prepare(player, new ItemStack(Items.STONE),
				EquipmentSlot.CHEST);

		pose.pushPose();
		pose.scale(-1.0f, -1.0f, 1.0f);
		//? if >=26.1 {
		
		/*for (int i = 0; i < ARMOR_RENDERS; i++) {
			model.gemrender$submitArmour(pose, null, light, OverlayTexture.NO_OVERLAY);
		}
*///?} else if >=1.21 {
		VertexConsumer buffer = buffers
				.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
		for (int i = 0; i < ARMOR_RENDERS; i++) {
			model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		}
		//?} else {
		/*VertexConsumer buffer = buffers
				.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
		for (int i = 0; i < ARMOR_RENDERS; i++) {
			model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
		}
*///?}
		pose.popPose();
	}

	private static float unitScale() {
		GemRenderGltfModel model = model();
		if (model == null) {
			return 1.0f;
		}
		return 0.5f / Math.max(0.001f, model.model()
				.boundingSphere()
				.w());
	}

	private static void handCopies(PoseStack pose, MultiBufferSource buffers, int light) {
		if (!HAND || COUNT <= 0 || model() == null) {
			return;
		}

		ItemStack stack = new ItemStack(Items.STONE);

		for (int i = 0; i < COUNT; i++) {
			copy = i;
			pose.pushPose();
			pose.translate(-0.4f + i * 0.4f, -0.3f, -2.0f);
			draw(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, buffers, light);
			pose.popPose();
		}
	}

	//? if >=26.1 && neoforge {
	/*@SubscribeEvent
	public static void onRenderHand(RenderHandEvent event) {
		if (event.getHand() != InteractionHand.MAIN_HAND) {
			return;
		}
		handCopies(event.getPoseStack(), null, event.getPackedLight());
	}
*///?} else if !fabric {
	@SubscribeEvent
	public static void onRenderHand(RenderHandEvent event) {
		if (event.getHand() != InteractionHand.MAIN_HAND) {
			return;
		}
		handCopies(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
	}
	//?}

	//? if >=26.1 && neoforge {
	/*@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		itemGrid(event.getGuiGraphics());
	}
*///?} else if !fabric {
	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		gui(event.getGuiGraphics());
	}
	//?}

	//? if <26.1 {
	public static void gui(net.minecraft.client.gui.GuiGraphics graphics) {
		if (COUNT <= 0 || model() == null) {
			return;
		}

		if (ITEM_GRID) {
			itemGrid(graphics);
			return;
		}

		PoseStack pose = graphics.pose();

		if (ARMOR) {

			pose.pushPose();
			pose.translate(20.0f, 20.0f, 150.0f);
			pose.scale(16.0f, -16.0f, 16.0f);
			pose.scale(unitScale(), unitScale(), unitScale());
			drawArmor(pose, graphics.bufferSource(), LightTexture.FULL_BRIGHT);
			pose.popPose();

			graphics.flush();
			guiDraws = DirectRenderer.drawsLastFlush();
			guiPalettes = DirectRenderer.palettesLastFlush();
			guiInstances = DirectRenderer.instancesLastFlush();
			return;
		}

		int stride = Math.max(1, (int) Math.ceil(Math.sqrt(COUNT)));
		ItemStack stack = new ItemStack(Items.STONE);
		ItemStack vanilla = new ItemStack(Items.DIAMOND);

		for (int i = 0; i < COUNT; i++) {
			int column = i % stride;
			int row = i / stride;
			copy = i;

			pose.pushPose();

			pose.translate(20.0f + column * 18.0f, 20.0f + row * 18.0f, 150.0f);
			pose.scale(16.0f, -16.0f, 16.0f);

			draw(stack, ItemDisplayContext.GUI, pose, graphics.bufferSource(),
					LightTexture.FULL_BRIGHT);

			pose.popPose();

			if (INTERLEAVE) {

				graphics.renderItem(vanilla, 20 + column * 18 - 8 - 18, 20 + row * 18 - 8);
				graphics.renderItem(vanilla, 20 + column * 18 - 8 + 18, 20 + row * 18 - 8);
			}

			if (FLUSH_PER_COPY) {

				graphics.flush();

				if (DECORATE) {
					int slotX = 20 + column * 18 - 8;
					int slotY = 20 + row * 18 - 8;
					graphics.fill(RenderType.guiOverlay(), slotX + 2, slotY + 13, slotX + 15, slotY + 15,
							0xFF00FF00);
				}
			}
		}

		graphics.flush();

		guiDraws = DirectRenderer.drawsLastFlush();
		guiPalettes = DirectRenderer.palettesLastFlush();
		guiInstances = DirectRenderer.instancesLastFlush();
	}
	//?}

	//? if >=26.1 {
	/*private static void drawGridItem(Object graphics, ItemStack stack, int x, int y) {
		((net.minecraft.client.gui.GuiGraphicsExtractor) graphics).item(stack, x, y);
	}
*///?} else {
	private static void drawGridItem(Object graphics, ItemStack stack, int x, int y) {
		((net.minecraft.client.gui.GuiGraphics) graphics).renderItem(stack, x, y);
	}
	//?}

	private static void itemGrid(Object graphics) {
		ItemStack stack = spikeStack();
		if (COUNT <= 0 || model() == null || stack == null) {
			return;
		}

		int stride = Math.max(1, (int) Math.ceil(Math.sqrt(COUNT)));
		for (int i = 0; i < COUNT; i++) {
			copy = i;
			drawGridItem(graphics, stack, 12 + (i % stride) * 18, 12 + (i / stride) * 18);
		}

		guiDraws = DirectRenderer.drawsLastFlush();
		guiPalettes = DirectRenderer.palettesLastFlush();
		guiInstances = DirectRenderer.instancesLastFlush();
	}

	//? if >=26.1 && neoforge {
	/*@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onRenderStage(RenderLevelStageEvent.AfterOpaqueFeatures event) {
		levelCopies(event.getPoseStack());
	}
*///?} else if !fabric {
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onRenderStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		levelCopies(event.getPoseStack());
	}
	//?}

	private static void totemPop() {
		Minecraft mc = Minecraft.getInstance();
		ItemStack totem = spikeStack();
		if (!TOTEM || mc.level == null || totem == null || model() == null) {
			return;
		}

		long now = mc.level.getGameTime();
		if (now - totemArmedAt < 38L) {
			return;
		}
		totemArmedAt = now;
		mc.gameRenderer.displayItemActivation(totem);
	}

	private static void levelCopies(PoseStack pose) {
		totemPop();

		if (COUNT <= 0 || ENTITY) {
			return;
		}
		GemRenderGltfModel model = model();
		if (model == null) {
			return;
		}

		ItemStack stack = new ItemStack(Items.STONE);

		if (ARMOR) {
			pose.pushPose();
			pose.translate(0.0f, 1.0f, 0.0f);
			float scale = 2.0f * unitScale();
			pose.scale(scale, scale, scale);
			drawArmor(pose, Minecraft.getInstance()
					.renderBuffers()
					.bufferSource(), LightTexture.FULL_BRIGHT);
			pose.popPose();
			return;
		}

		for (int i = 0; i < COUNT; i++) {
			copy = i;
			pose.pushPose();
			pose.translate(i * 1.5f, 1.0f, 0.0f);

			draw(stack, ItemDisplayContext.GROUND, pose, Minecraft.getInstance()
					.renderBuffers()
					.bufferSource(), LightTexture.FULL_BRIGHT);

			pose.popPose();
		}
	}

	// 26.1 Fabric: no hand event, so -Pdirecthand draws nothing here. The level copies run in an early
	// phase of AFTER_SOLID_FEATURES, as NeoForge runs them at HIGH priority.
	//? if fabric && >=26.1 {
	/*public static void init() {
		LevelRenderEvents.AFTER_SOLID_FEATURES.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		LevelRenderEvents.AFTER_SOLID_FEATURES.register(EARLY, context -> levelCopies(context.poseStack()));

		Registry.register(BuiltInRegistries.ITEM, SpikeItems.SPIKE, SpikeItems.create());
		GemRenderItemRenderer.register(SpikeItems.SPIKE, renderer());
	}

	public static void gui(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
		itemGrid(graphics);
	}
	*///?} elif fabric {
	/*public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		WorldRenderEvents.AFTER_ENTITIES.register(EARLY, context -> levelCopies(context.matrixStack()));
		HandRenderEvents.BEFORE_HAND.register(
				(pose, buffers, light, partialTick) -> handCopies(pose, buffers, light));

		Item item = Registry.register(BuiltInRegistries.ITEM, SpikeItems.SPIKE, SpikeItems.create());
		BuiltinItemRendererRegistry.INSTANCE.register(item,
				(stack, context, pose, buffers, light, overlay) -> renderer()
						.renderByItem(stack, context, pose, buffers, light, overlay));
	}
	*///?}

	//? if fabric {
	/*private static final net.minecraft.resources.ResourceLocation EARLY =
			Ids.of(GemRender.MOD_ID, "direct_spike");
	*///?}

	private static ItemStack spikeStack() {
		return SpikeItems.item() == null ? null : new ItemStack(SpikeItems.item());
	}

	public static String verdict() {
		if (COUNT <= 0) {
			return "";
		}
		levelDraws = DirectRenderer.drawsLastFlush();
		return " directCopies=" + COUNT + (SPREAD ? " directSpread=1" : "")
				+ (FLUSH_PER_COPY ? " directFlushPerCopy=1" : "")
				+ (DECORATE ? " directDecorate=1" : "")
				+ (ARMOR ? " directArmor=" + ARMOR_RENDERS : "")
				+ (HAND ? " directHand=1" : "")
				+ (TOTEM ? " directTotem=1" : "")
				+ (INTERLEAVE ? " directInterleave=1" : "")
				+ (ITEM_GRID ? " guiItemGrid=1" : "")
				+ (DIRECT_BLEND ? " directBlend=1" : "")
				+ " guiDraws=" + guiDraws + " guiPalettes=" + guiPalettes
				+ " guiInstances=" + guiInstances
				+ " levelDraws=" + levelDraws + " directStats=[" + DirectStats.report() + "]";
	}
}
