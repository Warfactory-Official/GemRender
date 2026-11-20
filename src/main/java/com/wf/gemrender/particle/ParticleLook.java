package com.wf.gemrender.particle;

import com.mojang.blaze3d.platform.NativeImage;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.Ids;
import com.wf.gemrender.texture.Pixels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToIntBiFunction;
import net.minecraft.world.level.block.Blocks;

/**
 * Per-particle colour and light, packed into the record's last float.
 *
 * <p>{@code 1 + (r5 << 19 | g6 << 13 | b5 << 8 | block << 4 | sky)}: at most {@code 2^24}, exact in a float.
 * {@link #NONE} (0) keeps the style's tint and light; any other value multiplies the tint by the colour and
 * replaces the style light.
 */
public final class ParticleLook {
    public static final int NONE = 0;

    public static final int WHITE = 0xFFFFFF;

    /** Vanilla {@code TerrainParticle} base colour, before the block tint. */
    public static final float TERRAIN_SHADE = 0.6f;

    private static final Map<TextureAtlasSprite, Integer> SPRITE_AVERAGES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ParticleLook() {
    }

    public static int of(int rgb, int blockLight, int skyLight) {
        int r = (rgb >> 19) & 31;
        int g = (rgb >> 10) & 63;
        int b = (rgb >> 3) & 31;
        return 1 + (r << 19 | g << 13 | b << 8 | (clampLight(blockLight) << 4) | clampLight(skyLight));
    }

    /** Colour at full brightness: emissive, or a caller that does not want world light. */
    public static int of(int rgb) {
        return of(rgb, 15, 15);
    }

    /** Colour lit by the world at {@code (x, y, z)} when spawned. Lightmap still follows day and night. */
    public static int lit(ClientLevel level, double x, double y, double z, int rgb) {
        return lit(level::getBrightness, x, y, z, rgb);
    }

    static int lit(ToIntBiFunction<LightLayer, BlockPos> brightness, double x, double y, double z, int rgb) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return of(rgb, brightness.applyAsInt(LightLayer.BLOCK, pos), brightness.applyAsInt(LightLayer.SKY, pos));
    }

    public static int red(int look) {
        return expand5((look - 1) >> 19 & 31);
    }

    public static int green(int look) {
        int g = (look - 1) >> 13 & 63;
        return g << 2 | g >> 4;
    }

    public static int blue(int look) {
        return expand5((look - 1) >> 8 & 31);
    }

    public static int blockLight(int look) {
        return (look - 1) >> 4 & 15;
    }

    public static int skyLight(int look) {
        return (look - 1) & 15;
    }

    /**
     * Mean colour of vanilla's terrain particles for {@code state}: particle sprite's alpha-weighted mean x
     * {@link #TERRAIN_SHADE} x block tint at {@code pos} (grass block untinted, as vanilla). Sprite means cached
     * per sprite.
     *
     * <p>Reads the level, so call it where the level is owned.
     */
    public static int blockColour(ClientLevel level, BlockPos pos, BlockState state) {
        TextureAtlasSprite sprite = particleSprite(state);
        return terrain(SPRITE_AVERAGES.computeIfAbsent(sprite, ParticleLook::spriteMean),
                blockTint(level, pos, state));
    }

    /** {@code mean x TERRAIN_SHADE x tint}; {@code tint == -1}: untinted. */
    static int terrain(int mean, int tint) {
        int t = tint == -1 ? WHITE : tint;
        return shade(mean >> 16, t >> 16) << 16 | shade(mean >> 8, t >> 8) << 8 | shade(mean, t);
    }

    private static int shade(int channel, int tint) {
        return Math.round((channel & 0xFF) * TERRAIN_SHADE * (tint & 0xFF) / 255.0f);
    }

    private static TextureAtlasSprite particleSprite(BlockState state) {
        //? if >=26.1 {
        /*return Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .getParticleMaterial(state)
                .sprite();
        *///?} else {
        return Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModelShaper()
                .getParticleIcon(state);
        //?}
    }

    private static int blockTint(ClientLevel level, BlockPos pos, BlockState state) {
        //? if >=26.1 {
        /*net.minecraft.client.color.block.BlockTintSource source = Minecraft.getInstance()
                .getBlockColors()
                .getTintSource(state, 0);
        return source == null ? -1 : source.colorAsTerrainParticle(state, level, pos);
        *///?} else {
        if (untintedTerrain(state)) {
            return -1;
        }
        return Minecraft.getInstance()
                .getBlockColors()
                .getColor(state, level, pos, 0);
        //?}
    }

    //? if <26.1 {
    static boolean untintedTerrain(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK);
    }
    //?}

    /** Mean of the sprite's source PNG; every animation frame counts. Fully transparent pixels do not. */
    private static int spriteMean(TextureAtlasSprite sprite) {
        ResourceLocation name = sprite.contents()
                .name();
        ResourceLocation file = Ids.of(name.getNamespace(),
                "textures/" + name.getPath() + ".png");
        try (InputStream in = Minecraft.getInstance()
                .getResourceManager()
                .getResourceOrThrow(file)
                .open(); NativeImage image = NativeImage.read(in)) {
            return mean(image);
        } catch (IOException e) {
            GemRender.LOGGER.warn("No particle colour for sprite {}: {}", name, e.toString());
            return WHITE;
        }
    }

    static int mean(NativeImage image) {
        long r = 0;
        long g = 0;
        long b = 0;
        long weight = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = Pixels.get(image, x, y);
                int a = abgr >>> 24;
                r += (long) (abgr & 0xFF) * a;
                g += (long) (abgr >> 8 & 0xFF) * a;
                b += (long) (abgr >> 16 & 0xFF) * a;
                weight += a;
            }
        }
        if (weight == 0) {
            return WHITE;
        }
        return (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
    }

    private static int clampLight(int light) {
        return Math.max(0, Math.min(15, light));
    }

    private static int expand5(int v) {
        return v << 3 | v >> 2;
    }
}
