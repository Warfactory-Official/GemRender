package com.wf.gemrender.ui;

import com.mojang.blaze3d.font.GlyphInfo;
import com.wf.gemrender.mixin.ui.BakedGlyphAccessor;
import com.wf.gemrender.mixin.ui.FontAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

/**
 * Glyph metrics, UVs and page texture without vanilla's per-call method-reference allocation
 * ({@code FontSet.getGlyph} / {@code getGlyphInfo} => {@code computeIfAbsent(cp, this::..)}). BMP
 * code points of the default font are cached in 256-entry blocks; any {@code FontSet} texture reset
 * drops every cache.
 */
public final class CanvasFont {
    static final int INTENSITY_FLAG = 1 << 31;

    private static int generation;

    private int seenGeneration = -1;
    int version;
    private FontSet cachedSet;
    private boolean cachedFishy;
    private final Glyph[][] glyphs = new Glyph[256][];

    private RenderType[] types = new RenderType[4];
    private int[] ids = new int[4];
    private int count;

    CanvasFont() {
    }

    public static void invalidate() {
        generation++;
    }

    Font font() {
        return Minecraft.getInstance().font;
    }

    FontSet fontSet() {
        return fontSet(Style.DEFAULT_FONT);
    }

    FontSet fontSet(ResourceLocation id) {
        return ((FontAccessor) font()).gemrender$fontSet(id);
    }

    boolean filterFishy() {
        return ((FontAccessor) font()).gemrender$filterFishyGlyphs();
    }

    /** Called once per {@code begin}: revalidates against the current default font set. */
    void refresh() {
        var set = fontSet();
        boolean fishy = filterFishy();
        if (seenGeneration != generation || set != cachedSet || fishy != cachedFishy) {
            seenGeneration = generation;
            version++;
            cachedSet = set;
            cachedFishy = fishy;
            Arrays.fill(glyphs, null);
            Arrays.fill(types, 0, count, null);
            count = 0;
        }
    }

    /** Everything a quad and an advance need, flattened once per code point. */
    static final class Glyph {
        boolean empty;
        float advance;
        float advanceBold;
        float boldOffset;
        float shadowOffset;
        float u0;
        float u1;
        float v0;
        float v1;
        float left;
        float right;
        float up;
        float down;
        int texture;
    }

    private final Glyph scratch = new Glyph();

    /** Cached for BMP code points of the cached set; else {@link #scratch}, valid until the next call. */
    Glyph glyph(
            FontSet set,
            boolean fishy, int cp) {
        if (set != cachedSet || fishy != cachedFishy || cp >= 0x10000) {
            return fill(scratch, set, fishy, cp);
        }
        Glyph[] block = glyphs[cp >>> 8];
        if (block == null) {
            block = glyphs[cp >>> 8] = new Glyph[256];
        }
        Glyph glyph = block[cp & 0xFF];
        if (glyph == null) {
            glyph = block[cp & 0xFF] = fill(new Glyph(), set, fishy, cp);
        }
        return glyph;
    }

    private Glyph fill(Glyph out,
                       FontSet set,
                       boolean fishy, int cp) {
        GlyphInfo info = set.getGlyphInfo(cp, fishy);
        BakedGlyph baked = set.getGlyph(cp);
        out.advance = info.getAdvance(false);
        out.advanceBold = info.getAdvance(true);
        out.boldOffset = info.getBoldOffset();
        out.shadowOffset = info.getShadowOffset();
        out.empty = baked instanceof EmptyGlyph;
        if (!out.empty) {
            BakedGlyphAccessor g = (BakedGlyphAccessor) baked;
            out.u0 = g.gemrender$u0();
            out.u1 = g.gemrender$u1();
            out.v0 = g.gemrender$v0();
            out.v1 = g.gemrender$v1();
            out.left = g.gemrender$left();
            out.right = g.gemrender$right();
            out.up = g.gemrender$up();
            out.down = g.gemrender$down();
            out.texture = texture(baked);
        }
        return out;
    }

    /** GL texture id of the glyph's page, {@link #INTENSITY_FLAG} set for single-channel pages. */
    private int texture(BakedGlyph glyph) {
        RenderType type = glyph.renderType(Font.DisplayMode.NORMAL);
        for (int i = 0; i < count; i++) {
            if (types[i] == type) {
                return ids[i];
            }
        }
        type.setupRenderState();
        int id = RenderSystem.getShaderTexture(0);
        type.clearRenderState();
        if ("text_intensity".equals(type.toString())) {
            id |= INTENSITY_FLAG;
        }
        if (count == types.length) {
            types = Arrays.copyOf(types, count * 2);
            ids = Arrays.copyOf(ids, count * 2);
        }
        types[count] = type;
        ids[count++] = id;
        return id;
    }
}
