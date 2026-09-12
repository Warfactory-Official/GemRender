package com.wf.gemrender.direct;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

final class ResidentModels {
    private static final Map<GemRenderGltfModel, ResidentModel> RESIDENT = new IdentityHashMap<>();

    private ResidentModels() {
    }

    @Nullable
    static ResidentModel get(GemRenderGltfModel model) {
        RenderSystem.assertOnRenderThread();

        ResidentModel resident = RESIDENT.get(model);
        if (resident != null) {
            return resident;
        }

        try {
            resident = ResidentModel.upload(model);
        } catch (RuntimeException e) {
            GemRender.LOGGER.error("Could not make a model resident for the direct path; it will not draw "
                    + "as an item until the next resource reload", e);
            return null;
        }

        RESIDENT.put(model, resident);
        GemRender.LOGGER.info("Direct path: uploaded {} mesh(es), now holding {} model(s) resident",
                resident.parts()
                        .size(),
                RESIDENT.size());
        return resident;
    }

    static void freeAll() {
        if (RESIDENT.isEmpty()) {
            return;
        }

        for (ResidentModel resident : RESIDENT.values()) {
            resident.delete();
        }
        GemRender.LOGGER.info("Direct path: freed {} resident model(s)", RESIDENT.size());
        RESIDENT.clear();
    }

    static int residentCount() {
        return RESIDENT.size();
    }
}
