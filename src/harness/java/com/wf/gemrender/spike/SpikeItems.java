package com.wf.gemrender.spike;

import com.wf.gemrender.GemRender;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
//?} else {
/*import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.RegisterEvent;
*///?}
//? if >=26.1 {
/*import com.wf.gemrender.direct.GemRenderItemRenderer;

import net.minecraft.resources.ResourceKey;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
*///?}

//? if >=26.1 {
/*@EventBusSubscriber(modid = GemRender.MOD_ID)
*///?} else {
@EventBusSubscriber(modid = GemRender.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
//?}
public final class SpikeItems {

	public static final ResourceLocation SPIKE = ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID,
			"spike");

	private static Item item;

	private SpikeItems() {
	}

	public static Item item() {
		return item;
	}

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		event.register(Registries.ITEM, helper -> {
			item = new SpikeItem(properties());
			helper.register(SPIKE, item);
		});
	}

	private static Item.Properties properties() {

		//? if >=26.1 {
		/*return new Item.Properties().stacksTo(1)
				.setId(ResourceKey.create(Registries.ITEM, SPIKE));
*///?} else {
		return new Item.Properties().stacksTo(1);
		//?}
	}

	//? if >=26.1 {
	
	/*@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		GemRenderItemRenderer.register(SPIKE, DirectSpike.itemRenderer());
	}
*///?}
}
