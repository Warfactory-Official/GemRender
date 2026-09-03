package com.wf.gemrender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.common.Mod;

@Mod(GemRender.MOD_ID)
public final class GemRender {
	public static final String MOD_ID = "gemrender";

	public static final Logger LOGGER = LoggerFactory.getLogger("GemRender");

	public GemRender() {
		LOGGER.info("GemRender loading");
	}
}
