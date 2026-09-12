package com.wf.gemrender.spike;

import net.minecraft.world.item.Item;
//? if <26.1 {
import java.util.function.Consumer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
//?}
//? if neoforge {
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
//?} else {
/*import net.minecraftforge.client.extensions.common.IClientItemExtensions;
*///?}

public class SpikeItem extends Item {
	public SpikeItem(Properties properties) {
		super(properties);
	}

	//? if <26.1 {
	@Override
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(new IClientItemExtensions() {
			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return DirectSpike.itemRenderer();
			}
		});
	}
	//?}
}
