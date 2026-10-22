package com.wf.gemrender.light;

import com.wf.gemrender.render.FlashbackState;
import com.wf.gemrender.render.GlState;
import com.wf.gemrender.render.TextureUnits;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
//? if >=1.21 {
import net.minecraft.world.level.chunk.status.ChunkStatus;
//?} else {
/*import net.minecraft.world.level.chunk.ChunkStatus;
*///?}
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL30C.GL_R16UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;

/**
 * 1 bit per block ({@link BlockState#isSolidRender} = occluder), {@code SIZE^3} toroidal around the camera
 * section: window = sections {@code cam - SECTIONS/2 .. cam + SECTIONS/2 - 1}. Texel = 16 x-adjacent blocks,
 * R16UI {@code (SIZE/16, SIZE, SIZE)}. Render thread.
 */
final class LightOccupancy {
    static final int SIZE = 128;

    static final int UNIT = TextureUnits.LIGHT_OCCUPANCY;

    private static final int SECTIONS = SIZE / 16;
    private static final int ROW = SIZE / 16;
    private static final long NONE = Long.MIN_VALUE;
    private static final long FILL_BUDGET_NANOS = Long.getLong("gemrender.lightfillbudget", 200_000L);

    private final ShortBuffer bits = MemoryUtil.memCallocShort(ROW * SIZE * SIZE);
    /** Section each slot holds; {@link #NONE} = zeros. */
    private final long[] keys = new long[SECTIONS * SECTIONS * SECTIONS];
    private final boolean[] dirty = new boolean[keys.length];
    private final int[] dirtySlots = new int[keys.length];
    private int dirtyCount;
    private final int[] stale = new int[keys.length];
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private int textureId;
    @Nullable
    private ClientLevel level;
    private int camSx;
    private int camSy;
    private int camSz;

    private long fillNanos;
    private long sectionsFilled;
    private long lifetimeFillNanos;
    private long lifetimeSections;
    private long blockUpdates;
    private int pending;

    LightOccupancy() {
        Arrays.fill(keys, NONE);
    }

    private static int slot(int sx, int sy, int sz) {
        int m = SECTIONS - 1;
        return ((sz & m) * SECTIONS + (sy & m)) * SECTIONS + (sx & m);
    }

    boolean inWindow(int sx, int sy, int sz) {
        int h = SECTIONS / 2;
        return sx >= camSx - h && sx < camSx + h && sy >= camSy - h && sy < camSy + h && sz >= camSz - h
                && sz < camSz + h;
    }

    /** Recentre on the camera section, refill stale slots nearest first within the budget. */
    void update(ClientLevel level, int camSx, int camSy, int camSz) {
        long start = System.nanoTime();
        if (this.level != level) {
            this.level = level;
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] != NONE) {
                    clearSlot(i);
                }
            }
        }
        this.camSx = camSx;
        this.camSy = camSy;
        this.camSz = camSz;

        int h = SECTIONS / 2;
        int count = 0;
        for (int sz = camSz - h; sz < camSz + h; sz++) {
            for (int sy = camSy - h; sy < camSy + h; sy++) {
                for (int sx = camSx - h; sx < camSx + h; sx++) {
                    int i = slot(sx, sy, sz);
                    long key = SectionPos.asLong(sx, sy, sz);
                    if (keys[i] == key) {
                        continue;
                    }
                    // Aliased slot from the old window: zeros (unoccluded) until refilled.
                    if (keys[i] != NONE) {
                        clearSlot(i);
                    }
                    stale[count++] = (Math.max(Math.abs(sx - camSx), Math.max(Math.abs(sy - camSy),
                            Math.abs(sz - camSz))) << 16) | i;
                }
            }
        }
        Arrays.sort(stale, 0, count);
        int filled = 0;
        // A video export fills the whole window: a budgeted fill leaves its first frames unoccluded (light through walls).
        long budget = FlashbackState.exporting() ? Long.MAX_VALUE : FILL_BUDGET_NANOS;
        for (int k = 0; k < count; k++) {
            if (filled > 0 && System.nanoTime() - start > budget) {
                break;
            }
            fill(level, stale[k] & 0xFFFF);
            filled++;
        }
        pending = count - filled;
        long spent = System.nanoTime() - start;
        sectionsFilled += filled;
        fillNanos += spent;
        if (filled > 0) {
            lifetimeSections += filled;
            lifetimeFillNanos += spent;
        }
    }

    /** Slot index -> the window section that maps to it. */
    private void fill(ClientLevel level, int slot) {
        int m = SECTIONS - 1;
        int h = SECTIONS / 2;
        int sx = wrapTo(slot & m, camSx - h);
        int sy = wrapTo(slot / SECTIONS & m, camSy - h);
        int sz = wrapTo(slot / (SECTIONS * SECTIONS), camSz - h);
        keys[slot] = SectionPos.asLong(sx, sy, sz);
        markDirty(slot);

        LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
        int index = level.getSectionIndexFromSectionY(sy);
        if (chunk == null || index < 0 || index >= level.getSectionsCount()) {
            zero(slot);
            return;
        }
        LevelChunkSection section = chunk.getSection(index);
        PalettedContainer<BlockState> states = section.getStates();
        if (section.hasOnlyAir() || !states.maybeHas(state -> solid(state, level, cursor))) {
            zero(slot);
            return;
        }
        int x0 = sx << 4, y0 = sy << 4, z0 = sz << 4;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                int row = 0;
                for (int x = 0; x < 16; x++) {
                    if (solid(states.get(x, y, z), level, cursor.set(x0 + x, y0 + y, z0 + z))) {
                        row |= 1 << x;
                    }
                }
                bits.put(index(sx, y0 + y, z0 + z), (short) row);
            }
        }
    }

    /** Smallest {@code v ≡ residue (mod SECTIONS)} with {@code v >= low}. */
    private static int wrapTo(int residue, int low) {
        return low + Math.floorMod(residue - low, SECTIONS);
    }

    private static int index(int sx, int by, int bz) {
        return ((bz & (SIZE - 1)) * SIZE + (by & (SIZE - 1))) * ROW + (sx & (ROW - 1));
    }

    private void zero(int slot) {
        int m = SECTIONS - 1;
        int y0 = (slot / SECTIONS & m) * 16;
        int z0 = slot / (SECTIONS * SECTIONS) * 16;
        for (int z = 0; z < 16; z++) {
            for (int y = 0; y < 16; y++) {
                bits.put(index(slot & m, y0 + y, z0 + z), (short) 0);
            }
        }
    }

    private void clearSlot(int slot) {
        keys[slot] = NONE;
        zero(slot);
        markDirty(slot);
    }

    private void markDirty(int slot) {
        if (!dirty[slot]) {
            dirty[slot] = true;
            dirtySlots[dirtyCount++] = slot;
        }
    }

    /** Client block change; ignored unless the slot holds that section. */
    void blockChanged(BlockPos pos, BlockState state) {
        ClientLevel level = this.level;
        if (level == null) {
            return;
        }
        int sx = pos.getX() >> 4, sy = pos.getY() >> 4, sz = pos.getZ() >> 4;
        int slot = slot(sx, sy, sz);
        if (keys[slot] != SectionPos.asLong(sx, sy, sz)) {
            return;
        }
        blockUpdates++;
        set(pos.getX(), pos.getY(), pos.getZ(), solid(state, level, pos));
        markDirty(slot);
    }

    private static boolean solid(BlockState state, ClientLevel level, BlockPos pos) {
        //? if >=26.1 {
        /*return state.isSolidRender();
        *///?} else {
        return state.isSolidRender(level, pos);
        //?}
    }

    void set(int x, int y, int z, boolean solid) {
        int i = index(x >> 4, y, z);
        int bit = 1 << (x & 15);
        int row = bits.get(i);
        bits.put(i, (short) (solid ? row | bit : row & ~bit));
    }

    /** Loaded or dropped chunk column: its slots refill. */
    void chunkChanged(int cx, int cz) {
        for (int i = 0; i < keys.length; i++) {
            long key = keys[i];
            if (key != NONE && SectionPos.x(key) == cx && SectionPos.z(key) == cz) {
                clearSlot(i);
            }
        }
    }

    /** Test: window around the camera section, every slot claimed as filled (zeros). */
    void claimWindow(int camSx, int camSy, int camSz) {
        this.camSx = camSx;
        this.camSy = camSy;
        this.camSz = camSz;
        int h = SECTIONS / 2;
        for (int sz = camSz - h; sz < camSz + h; sz++) {
            for (int sy = camSy - h; sy < camSy + h; sy++) {
                for (int sx = camSx - h; sx < camSx + h; sx++) {
                    keys[slot(sx, sy, sz)] = SectionPos.asLong(sx, sy, sz);
                    markDirty(slot(sx, sy, sz));
                }
            }
        }
    }

    void upload() {
        int previousUnit = TextureUnits.activate(UNIT);
        GlState.resetUnpack();
        if (textureId == 0) {
            textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_3D, textureId);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAX_LEVEL, 0);
            glTexImage3D(GL_TEXTURE_3D, 0, GL_R16UI, ROW, SIZE, SIZE, 0, GL_RED_INTEGER, GL_UNSIGNED_SHORT, bits);
            clearDirty();
        } else if (dirtyCount > 0) {
            glBindTexture(GL_TEXTURE_3D, textureId);
            if (dirtyCount > keys.length / 4) {
                glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, 0, ROW, SIZE, SIZE, GL_RED_INTEGER, GL_UNSIGNED_SHORT, bits);
            } else {
                glPixelStorei(GL_UNPACK_ROW_LENGTH, ROW);
                glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, SIZE);
                int m = SECTIONS - 1;
                for (int k = 0; k < dirtyCount; k++) {
                    int slot = dirtySlots[k];
                    int x = slot & m, y = (slot / SECTIONS & m) * 16, z = slot / (SECTIONS * SECTIONS) * 16;
                    glPixelStorei(GL_UNPACK_SKIP_PIXELS, x);
                    glPixelStorei(GL_UNPACK_SKIP_ROWS, y);
                    glPixelStorei(GL_UNPACK_SKIP_IMAGES, z);
                    glTexSubImage3D(GL_TEXTURE_3D, 0, x, y, z, 1, 16, 16, GL_RED_INTEGER, GL_UNSIGNED_SHORT, bits);
                }
                glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);
            }
            clearDirty();
        } else {
            glBindTexture(GL_TEXTURE_3D, textureId);
        }
        TextureUnits.restore(previousUnit);
    }

    private void clearDirty() {
        for (int k = 0; k < dirtyCount; k++) {
            dirty[dirtySlots[k]] = false;
        }
        dirtyCount = 0;
    }

    /** Harness: {@code occFillUs} = mean per frame since reset; {@code occUsPerSection} lifetime. */
    String report(long frames) {
        return "occFillUs=" + (frames == 0 ? 0 : fillNanos / frames / 1000) + " occUsPerSection="
                + (lifetimeSections == 0 ? 0 : lifetimeFillNanos / lifetimeSections / 1000)
                + " occLifetimeSections=" + lifetimeSections + " occSections=" + sectionsFilled
                + " occBlocks=" + blockUpdates + " occPending=" + pending;
    }

    void resetRun() {
        fillNanos = 0;
        sectionsFilled = 0;
        blockUpdates = 0;
    }
}
