package com.wf.gemrender.asset;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GemRenderPartsModel;
import com.wf.gemrender.gltf.GltfImporter;
import com.wf.gemrender.render.MorphBuffer;
import com.wf.gemrender.texture.ModelTextures;
import com.wf.gemrender.vendor.jgltf.GltfResourceHook;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
*///?}
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Declares and resolves GemRender assets: {@code handle(id)} to declare, {@code get(id)} to read.
 */
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
     * runs lazily on first use and again after every resource reload, on a loader thread rather than on
     * the one that asked, so it must not touch the GPU or any render state; build geometry and let the
     * first draw upload it.
     *
     * <p>The first builder registered for an id wins. Registering a second is a no-op rather than an
     * error, so a handle can be declared from more than one place without a race deciding which is used.
     */
    public static ModelCache.Handle<GemRenderGltfModel> built(ResourceLocation id, Builder builder) {
        BUILDERS.putIfAbsent(id, builder);
        return BUILT.handle(id);
    }

    /**
     * Declares one asset wearing several sets of textures, drawn from one sheet and one batch.
     *
     * <p>A variant is a mob's colour, a vehicle's livery, a team's paint: the same geometry, the same
     * rig, the same clips, different pixels. Each entry of {@code variants} substitutes textures by the
     * location the asset itself names, and an entry a variant does not mention keeps the texture it
     * had. Variant 0 is the base, and is normally {@code Map.of()}.
     *
     * <p>They are stitched into one texture at import, tiled inside the sheet's bands, so the cost of
     * a skin at draw time is two floats on the instance and nothing else -- no second sampler, no
     * second material, no second draw. Put the offset on the instance with
     * {@code instance.variant(model.variant(i))}.
     *
     * <p>The ceiling is the sheet: every variant is a full copy of the model's textures, and the packer
     * declines rather than exceeding 4096 pixels. A 64x64 mob texture has room for dozens; a model
     * whose sheet is already 1028x3084 has room for three. And every variant's textures have to be the
     * same size as variant 0's, since they share one packed layout -- a skin of another size throws
     * with both sizes named rather than shipping a model wearing the wrong one.
     *
     * <p>{@code id} names this variant set, not the file: it is what the cache and the stitched sheet
     * are keyed on, so two variant sets of one asset need two ids and get two sheets.
     */
    public static ModelCache.Handle<GemRenderGltfModel> variants(ResourceLocation id,
                                                                 ResourceLocation asset, List<Map<ResourceLocation, ResourceLocation>> variants) {
        List<Map<ResourceLocation, ResourceLocation>> declared = variants.isEmpty()
                ? List.of(Map.of())
                : List.copyOf(variants);

        return built(id, ignored -> GltfImporter.load(asset, id, declared));
    }

    /**
     * Declares one Bedrock geometry worn with several skins, drawn from one sheet and one batch.
     *
     * <p>The {@link #variants} of the {@code .geo.json} world, and the shape that format is usually in:
     * a geometry names no texture of its own, so the same hull is worn with a dozen. Each entry is a
     * whole texture rather than a substitution, because there is only ever one to substitute.
     *
     * <p>The first skin is the base. All of them have to be the same size, and the sheet has the same
     * 4096-pixel ceiling any other does.
     */
    public static ModelCache.Handle<GemRenderGltfModel> skins(ResourceLocation id,
                                                              ResourceLocation geometry, List<ResourceLocation> skins) {
        List<ResourceLocation> declared = List.copyOf(skins);
        return built(id, ignored -> BedrockImporter.load(geometry, id, declared));
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
     */
    public static ModelCache.Handle<GemRenderPartsModel> partsHandle(ResourceLocation asset) {
        return PARTS.handle(asset);
    }

    /**
     * The model if it is loaded; otherwise {@code null}, having asked for it. Ask again next frame.
     */
    @Nullable
    public static GemRenderPartsModel parts(ResourceLocation asset) {
        return PARTS.get(asset);
    }

    /**
     * The model if it is loaded; otherwise {@code null}, having asked for it. Ask again next frame.
     */
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

    /**
     * Re-imports every asset a mod has declared, in parallel, on the loader pool.
     *
     * <p>The three caches are quiesced first, and only then is the shared state they write into reset.
     * An import that was still running would otherwise append this generation's morph deltas after the
     * buffer was emptied for the next one, which is the shape of a bug this project has already had
     * once: the morph buffer grew by every model's deltas on every reload until it was noticed.
     *
     * <p>{@link GltfResourceHook} is cleared in the same window and for the same reason it exists --
     * it caches the {@code .bin} buffers an asset points at, keyed on resource location, so without
     * this a pack that overrides a buffer would keep rendering the one it replaced.
     *
     * <p>Nothing waits for the result. The reload is over as far as the game is concerned; the models
     * arrive over the next few frames, and a handle asked for one before it lands answers {@code null}
     * exactly as it does the first time an asset is wanted.
     */
    @SubscribeEvent
    public static void onEndClientResourceReload(EndClientResourceReloadEvent event) {
        if (event.error()
                .isPresent()) {
            return;
        }

        MODELS.quiesce();
        PARTS.quiesce();
        BUILT.quiesce();

        MorphBuffer.getInstance().reset();
        GltfResourceHook.clearCache();

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

    /**
     * How a mod assembles a model that has no file to import. Run again after every resource reload.
     */
    @FunctionalInterface
    public interface Builder {
        GemRenderGltfModel build(ResourceLocation id) throws Exception;
    }
}
