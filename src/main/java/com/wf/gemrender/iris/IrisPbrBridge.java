package com.wf.gemrender.iris;

import com.wf.gemrender.GemRender;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class IrisPbrBridge {
	private static final boolean ENABLED =
			!"false".equalsIgnoreCase(System.getProperty("gemrender.irispbr", "true"));

	private static boolean installed;

	private IrisPbrBridge() {
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (installed) {
			return;
		}
		installed = true;

		if (!ENABLED || !ModList.get()
				.isLoaded("iris")) {
			return;
		}

		try {
			IrisPbrLoader.register();
		} catch (Throwable t) {
			GemRender.LOGGER.warn("Could not register the LabPBR loader; shaderpacks will render "
					+ "GemRender materials without normal or specular data ({})", t.toString());
		}
	}
}
