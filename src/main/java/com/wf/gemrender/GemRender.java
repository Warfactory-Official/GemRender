package com.wf.gemrender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if neoforge {
import net.neoforged.fml.common.Mod;
//?} elif forge {
/*import net.minecraftforge.fml.common.Mod;
 *///?}

//? if !fabric {
@Mod(GemRender.MOD_ID)
//?}
public final class GemRender {
    public static final String MOD_ID = "gemrender";

    public static final Logger LOGGER = LoggerFactory.getLogger("GemRender");

    public GemRender() {
        LOGGER.info("GemRender loading");
        //? if lights {
        System.setProperty("voxy.auxColour", "true");
        //?}
    }
}
