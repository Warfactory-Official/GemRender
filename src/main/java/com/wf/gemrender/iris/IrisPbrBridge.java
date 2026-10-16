package com.wf.gemrender.iris;

import com.wf.gemrender.GemRender;

public final class IrisPbrBridge {
    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("gemrender.irispbr", "true"));
    private static final boolean GUEST =
            !"false".equalsIgnoreCase(System.getProperty("gemrender.irisguest", "true"));

    private static boolean installed;

    private IrisPbrBridge() {
    }

    public static void install(boolean irisLoaded) {
        if (installed) {
            return;
        }
        installed = true;
        ShaderPacks.irisLoaded(irisLoaded);
        if (irisLoaded && GUEST) {
            com.wf.gemrender.iris.guest.IrisGuest.install();
        }

        if (!ENABLED || !irisLoaded) {
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
