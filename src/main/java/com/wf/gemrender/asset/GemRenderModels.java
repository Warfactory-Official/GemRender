package com.wf.gemrender.asset;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.gltf.GltfImporter;
import com.wf.gemrender.render.MorphBuffer;
import com.wf.gemrender.texture.ModelTextures;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Declares and resolves GemRender assets: {@code handle(id)} to declare, {@code get(id)} to read. */
@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class GemRenderModels {
    private static final ModelCache<GemRenderGltfModel> MODELS =
            new ModelCache<>("GemRender models", GemRenderModels::load, GemRenderModels::dispose);

    /**
     * The same assets on the rigid-part path. A separate cache rather than a flag on the first: the
     * two produce different objects and an asset may reasonably be wanted both ways in one level.
     */
    private static final ModelCache<GemRenderPartsModel> PARTS =
            new ModelCache<>("GemRender parts models", GemRenderModels::loadParts,
                    GemRenderModels::disposeParts);

    /**
     * Assets a mod assembles itself, through {@link com.wf.gemrender.rig.RigBuilder} or otherwise. Same
     * cache, same reload, same disposal as an imported one; the only difference is where the bytes came
     * from.
     */
    private static final ModelCache<GemRenderGltfModel> BUILT =
            new ModelCache<>("GemRender built models", GemRenderModels::runBuilder,
                    GemRenderModels::dispose);

    private static final Map<ResourceLocation, Builder> BUILDERS = new ConcurrentHashMap<>();

    /** How a mod assembles a model that has no file to import. Run again after every resource reload. */
    @FunctionalInterface
    public interface Builder {
        GemRenderGltfModel build(ResourceLocation id) throws Exception;
    }

    private GemRenderModels() {
    }

    private static GemRenderGltfModel load(ResourceLocation asset) throws IOException {
        if (asset.getPath()
                .toLowerCase(Locale.ROOT)
                .endsWith(".geo.json")) {
            return BedrockImporter.load(asset);
        }
        return GltfImporter.load(asset);
    }

    private static GemRenderPartsModel loadParts(ResourceLocation asset) throws IOException {
        if (!asset.getPath()
                .toLowerCase(Locale.ROOT)
                .endsWith(".geo.json")) {
            throw new IllegalArgumentException(asset + " is not Bedrock geometry; the rigid-part path "
                    + "reads .geo.json only");
        }
        return BedrockImporter.loadParts(asset);
    }

    public static ModelCache.Handle<GemRenderGltfModel> handle(ResourceLocation asset) {
        return MODELS.handle(asset);
    }

    /**
     * Declares a model the calling mod builds rather than imports, under an id of its own choosing.
     *
     * <p>{@link #handle} for an asset with no file behind it. The id is a name, not a path -- nothing
     * reads it -- but it has to be stable and unique, because it is what the cache keys on. The builder
     * runs lazily on first use and again after every resource reload, on the thread that asks; declare
     * the handle in a {@code static final} for the same reason as an imported one, so the build happens
     * at a defined moment rather than mid-frame on a Flywheel task thread.
     *
     * <p>The first builder registered for an id wins. Registering a second is a no-op rather than an
     * error, so a handle can be declared from more than one place without a race deciding which is used.
     */
    public static ModelCache.Handle<GemRenderGltfModel> built(ResourceLocation id, Builder builder) {
        BUILDERS.putIfAbsent(id, builder);
        return BUILT.handle(id);
    }

    private static GemRenderGltfModel runBuilder(ResourceLocation id) throws Exception {
        Builder builder = BUILDERS.get(id);
        if (builder == null) {
            throw new IllegalStateException(id + " has no registered builder");
        }
        return builder.build(id);
    }

    /**
     * The rigid-part counterpart of {@link #handle}, and the one to declare in a {@code static final}.
     * {@link #parts} resolves through the same cache but loads on the spot, which on the visual's own
     * thread is an import mid-frame.
     */
    public static ModelCache.Handle<GemRenderPartsModel> partsHandle(ResourceLocation asset) {
        return PARTS.handle(asset);
    }

    @Nullable
    public static GemRenderPartsModel parts(ResourceLocation asset) {
        return PARTS.get(asset);
    }

    @Nullable
    public static GemRenderGltfModel get(ResourceLocation asset) {
        return MODELS.get(asset);
    }

    public static ModelCache<GemRenderGltfModel> cache() {
        return MODELS;
    }

    public static int generation() {
        return MODELS.generation();
    }

    @SubscribeEvent
    public static void onEndClientResourceReload(EndClientResourceReloadEvent event) {
        if (event.error()
                .isPresent()) {
            return;
        }
        MorphBuffer.getInstance().reset();
        MODELS.reload();
        PARTS.reload();
        BUILT.reload();
    }

    private static void dispose(ResourceLocation id, GemRenderGltfModel model) {
        for (ResourceLocation texture : model.textures()) {
            ModelTextures.release(texture);
        }
    }

    private static void disposeParts(ResourceLocation id, GemRenderPartsModel model) {
        for (ResourceLocation texture : model.textures()) {
            ModelTextures.release(texture);
        }
    }
}
