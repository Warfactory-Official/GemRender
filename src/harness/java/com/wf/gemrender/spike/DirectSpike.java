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
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
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

	private static long totemArmedAt = -40L;

	private static final boolean INTERLEAVE = Boolean.getBoolean("gemrender.directinterleave");

	//? if >=26.1 {
	/*private static final boolean ITEM_GRID = true;
*///?} else {
	private static final boolean ITEM_GRID = Boolean.getBoolean("gemrender.directitem");
	//?}

	public static boolean needsHand() {
		return HAND && COUNT > 0;
	}

	private static int copy;

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
			default -> SpikeAssets.RADAR;
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
					return model == null ? null : model.animationOrAny("");
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
					org.joml.Vector4fc sphere = model.model()
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

	@SubscribeEvent
	public static void onRenderHand(RenderHandEvent event) {
		if (!HAND || COUNT <= 0 || model() == null || event.getHand() != InteractionHand.MAIN_HAND) {
			return;
		}

		PoseStack pose = event.getPoseStack();
		ItemStack stack = new ItemStack(Items.STONE);

		for (int i = 0; i < COUNT; i++) {
			copy = i;
			pose.pushPose();
			pose.translate(-0.4f + i * 0.4f, -0.3f, -2.0f);

			//? if >=26.1 {
			/*draw(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, null,
					event.getPackedLight());
*///?} else {
			draw(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose,
					event.getMultiBufferSource(), event.getPackedLight());
			//?}

			pose.popPose();
		}
	}

	//? if >=26.1 {
	/*@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		itemGrid(event.getGuiGraphics());
	}
*///?} else {

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		if (COUNT <= 0 || model() == null) {
			return;
		}

		if (ITEM_GRID) {
			itemGrid(event.getGuiGraphics());
			return;
		}

		var graphics = event.getGuiGraphics();
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
		if (COUNT <= 0 || model() == null || SpikeItems.item() == null) {
			return;
		}

		ItemStack stack = new ItemStack(SpikeItems.item());
		int stride = Math.max(1, (int) Math.ceil(Math.sqrt(COUNT)));
		for (int i = 0; i < COUNT; i++) {
			copy = i;
			drawGridItem(graphics, stack, 12 + (i % stride) * 18, 12 + (i / stride) * 18);
		}

		guiDraws = DirectRenderer.drawsLastFlush();
		guiPalettes = DirectRenderer.palettesLastFlush();
		guiInstances = DirectRenderer.instancesLastFlush();
	}

	//? if >=26.1 {
	/*@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onRenderStage(RenderLevelStageEvent.AfterOpaqueFeatures event) {
		levelCopies(event.getPoseStack());
	}
*///?} else {
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
		if (!TOTEM || mc.level == null || SpikeItems.item() == null || model() == null) {
			return;
		}

		long now = mc.level.getGameTime();
		if (now - totemArmedAt < 38L) {
			return;
		}
		totemArmedAt = now;
		mc.gameRenderer.displayItemActivation(new ItemStack(SpikeItems.item()));
	}

	private static void levelCopies(PoseStack pose) {
		totemPop();

		if (COUNT <= 0) {
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
				+ " guiDraws=" + guiDraws + " guiPalettes=" + guiPalettes
				+ " guiInstances=" + guiInstances
				+ " levelDraws=" + levelDraws + " directStats=[" + DirectStats.report() + "]";
	}
}
