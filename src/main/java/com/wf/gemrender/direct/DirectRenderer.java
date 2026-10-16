package com.wf.gemrender.direct;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import com.wf.gemrender.gltf.blend.BlendKey;
import com.wf.gemrender.render.PoseCache;
import com.wf.gemrender.render.BoneBuffer;
import com.wf.gemrender.render.MorphBuffer;
import com.wf.gemrender.texture.Paint;
import com.wf.gemrender.texture.PaintArray;
import com.wf.gemrender.texture.VariantUv;
import com.wf.gemrender.water.PassState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.opengl.GL33C.glGetInteger;

public final class DirectRenderer {
    static final int UNIT_ATLAS = 0;
    static final int UNIT_OVERLAY = 1;
    static final int UNIT_LIGHTMAP = 2;

    private static final float QUANTUM_SECONDS = 1.0f / 128.0f;
    /** Packed light no level produces => {@link TextureSlots#emissive}. */
    private static final int EMISSIVE = -1;

    private static final Map<DirectPass, PassQueue> QUEUES = new EnumMap<>(DirectPass.class);

    private static final PassState PASS_STATE = new PassState();

    private static final GltfPose.Scratch SCRATCH = new GltfPose.Scratch();

    private static final BlendKey BLEND_PROBE = new BlendKey();

    private static final AnimationBlend BLEND_LOADED = new AnimationBlend();

    private static boolean guiQueued;

    private static boolean itemFlush;

    private static boolean inLevel;

    //? if >=26.1 {
	
	/*private static DirectPass sink = DirectPass.LEVEL;

	private static net.minecraft.world.item.ItemDisplayContext itemContext =
			net.minecraft.world.item.ItemDisplayContext.NONE;
*///?}

    @Nullable
    private static DirectGuest guest;

    private static int drawsLastFlush;
    private static int instancesLastFlush;
    private static int palettesLastFlush;

    private DirectRenderer() {
    }

    public static void submit(GemRenderGltfModel model, @Nullable GltfAnimation clip, float seconds,
                              Matrix4f pose, int light, int overlay, int argb, DirectPass pass) {
        submit(model, clip, seconds, pose, light, overlay, argb, pass, VariantUv.NONE);
    }

    public static void submit(GemRenderGltfModel model, @Nullable GltfAnimation clip, float seconds,
                              Matrix4f pose, int light, int overlay, int argb, DirectPass pass, VariantUv variant) {
        submit(model, clip, seconds, pose, light, overlay, argb, pass, variant, TextureSlots.NONE);
    }

    public static void submit(GemRenderGltfModel model, @Nullable GltfAnimation clip, float seconds,
                              Matrix4f pose, int light, int overlay, int argb, DirectPass pass, VariantUv variant,
                              TextureSlots slots) {
        RenderSystem.assertOnRenderThread();

        if (!DirectProgram.getInstance()
                .ensureCreated()) {
            return;
        }

        ResidentModel resident = ResidentModels.get(model);
        if (resident == null || resident.parts()
                .isEmpty()) {
            return;
        }

        DirectPass queued = queueFor(pass);
        DirectStats.submitBegin();
        guiQueued |= queued == DirectPass.GUI;

        PassQueue queue = QUEUES.computeIfAbsent(queued, key -> new PassQueue());
        PaletteSlot slot = stagePalette(queue, model, clip, seconds, queued);

        enqueue(queue, resident, pose, slot, light, overlay, argb, variant, Paint.NONE, 0, 0, slots);

        DirectStats.submitEnd(queued);
    }

    /**
     * A blended pose; one evaluation per distinct quantized blend per pass, as the single-clip form
     * shares per (clip, instant).
     */
    public static void submit(GemRenderGltfModel model, AnimationBlend blend, Matrix4f pose, int light,
                              int overlay, int argb, DirectPass pass, VariantUv variant) {
        submit(model, blend, pose, light, overlay, argb, pass, variant, TextureSlots.NONE);
    }

    public static void submit(GemRenderGltfModel model, AnimationBlend blend, Matrix4f pose, int light,
                              int overlay, int argb, DirectPass pass, VariantUv variant, TextureSlots slots) {
        BLEND_PROBE.set(model, 0, blend, QUANTUM_SECONDS, PoseCache.weightSteps(0));
        if (BLEND_PROBE.isSingleClip() || BLEND_PROBE.isRest()) {
            GltfAnimation clip = BLEND_PROBE.isRest() ? null : BLEND_PROBE.clip(0);
            submit(model, clip, clip == null ? 0.0f : BLEND_PROBE.time(0), pose, light, overlay, argb, pass,
                    variant, slots);
            return;
        }
        RenderSystem.assertOnRenderThread();

        if (!DirectProgram.getInstance()
                .ensureCreated()) {
            return;
        }

        ResidentModel resident = ResidentModels.get(model);
        if (resident == null || resident.parts()
                .isEmpty()) {
            return;
        }

        DirectPass queued = queueFor(pass);
        DirectStats.submitBegin();
        guiQueued |= queued == DirectPass.GUI;

        PassQueue queue = QUEUES.computeIfAbsent(queued, key -> new PassQueue());
        boolean shared = !BLEND_PROBE.isPrivate();
        PaletteSlot slot = shared ? queue.blendPalettes.get(BLEND_PROBE) : null;
        if (slot != null) {
            DirectStats.palette(queued, true);
        } else {
            DirectStats.palette(queued, false);
            Matrix4f[] palette = SCRATCH.palette(model.jointCount());
            float[] morphBlock = model.morphs()
                    .isEmpty() ? null : SCRATCH.morphBlock(model.morphs()
                    .blockFloats());
            BLEND_PROBE.load(BLEND_LOADED);
            GltfPose.evaluate(model.layout(), BLEND_LOADED, palette, model.morphs(), morphBlock, SCRATCH);
            slot = stage(model, palette, morphBlock);
            if (shared) {
                queue.blendPalettes.put(BLEND_PROBE.copy(), slot);
            }
        }

        enqueue(queue, resident, pose, slot, light, overlay, argb, variant, Paint.NONE, 0, 0, slots);

        DirectStats.submitEnd(queued);
    }

    public static void submit(GemRenderGltfModel model, float[] state, Matrix4f pose, int light,
                              int overlay, int argb, DirectPass pass) {
        submit(model, state, pose, light, overlay, argb, pass, VariantUv.NONE);
    }

    public static void submit(GemRenderGltfModel model, float[] state, Matrix4f pose, int light,
                              int overlay, int argb, DirectPass pass, VariantUv variant) {
        RenderSystem.assertOnRenderThread();

        if (!DirectProgram.getInstance()
                .ensureCreated()) {
            return;
        }

        ResidentModel resident = ResidentModels.get(model);
        if (resident == null || resident.parts()
                .isEmpty()) {
            return;
        }

        DirectPass queued = queueFor(pass);
        DirectStats.submitBegin();
        guiQueued |= queued == DirectPass.GUI;

        Matrix4f[] palette = SCRATCH.palette(model.jointCount());
        float[] morphBlock = model.morphs()
                .isEmpty() ? null : SCRATCH.morphBlock(model.morphs()
                .blockFloats());
        GltfPose.evaluate(model.layout(), state, palette, model.morphs(), morphBlock, SCRATCH);

        PassQueue queue = QUEUES.computeIfAbsent(queued, key -> new PassQueue());
        PaletteSlot slot = stage(model, palette, morphBlock);
        DirectStats.palette(queued, false);

        enqueue(queue, resident, pose, slot, light, overlay, argb, variant, Paint.NONE, 0, 0, TextureSlots.NONE);

        DirectStats.submitEnd(queued);
    }

    /**
     * A palette the caller already posed (e.g. a world visual's), painted like
     * {@link com.wf.gemrender.render.GemRenderInstance#paint}. {@code paint} only on a {@code paintable()} model;
     * {@code restPalette} = its {@code restPalette()}, ignored unpainted.
     */
    public static void submit(GemRenderGltfModel model, Matrix4f[] palette, @Nullable float[] morphBlock,
                              Matrix4f pose, int light, int overlay, int argb, DirectPass pass, VariantUv variant,
                              Paint paint, int reference, Matrix4f[] restPalette) {
        submit(model, palette, morphBlock, pose, light, overlay, argb, pass, variant, paint, reference, restPalette,
                TextureSlots.NONE);
    }

    /** As above; {@code slots} binds the model's texture slots (unbound => those meshes not drawn). */
    public static void submit(GemRenderGltfModel model, Matrix4f[] palette, @Nullable float[] morphBlock,
                              Matrix4f pose, int light, int overlay, int argb, DirectPass pass, VariantUv variant,
                              Paint paint, int reference, Matrix4f[] restPalette, TextureSlots slots) {
        RenderSystem.assertOnRenderThread();

        if (!DirectProgram.getInstance()
                .ensureCreated()) {
            return;
        }

        ResidentModel resident = ResidentModels.get(model);
        if (resident == null || resident.parts()
                .isEmpty()) {
            return;
        }

        DirectPass queued = queueFor(pass);
        DirectStats.submitBegin();
        guiQueued |= queued == DirectPass.GUI;

        PassQueue queue = QUEUES.computeIfAbsent(queued, key -> new PassQueue());
        PaletteSlot slot = stage(model, palette, morphBlock);
        int restBase = paint.isNone() ? 0 : BoneBuffer.direct()
                .addSharedPalette(restPalette, model.jointCount());
        DirectStats.palette(queued, false);

        enqueue(queue, resident, pose, slot, light, overlay, argb, variant, paint, reference, restBase, slots);

        DirectStats.submitEnd(queued);
    }

    private static void enqueue(PassQueue queue, ResidentModel resident, Matrix4f pose, PaletteSlot slot, int light,
                                int overlay, int argb, VariantUv variant, Paint paint, int reference, int restBase,
                                TextureSlots slots) {
        for (ResidentModel.Part part : resident.parts()) {
            Batch batch;
            int partLight = light;
            if (part.slot() == null) {
                batch = queue.batch(part);
            } else {
                ResourceLocation texture = slots.texture(part.slot());
                if (texture == null) {
                    continue;
                }
                batch = queue.batch(part, texture);
                partLight = slots.emissive(part.slot()) ? EMISSIVE : light;
            }
            write(batch, batch.reserve(), pose, slot, partLight, overlay, argb, variant, paint, reference, restBase);
        }
    }

    private static PaletteSlot stage(GemRenderGltfModel model, Matrix4f[] palette,
                                     @Nullable float[] morphBlock) {
        BoneBuffer bones = BoneBuffer.direct();
        int boneBase = bones.addPalette(palette, model.jointCount());
        int morphBase = morphBlock == null ? 0
                : bones.addMorphBlock(morphBlock, model.morphs()
                .blockFloats());
        return new PaletteSlot(boneBase, morphBase);
    }

    private static PaletteSlot stagePalette(PassQueue queue, GemRenderGltfModel model,
                                            @Nullable GltfAnimation clip, float seconds, DirectPass pass) {
        float time = clip == null ? 0.0f : clip.loop(seconds);
        int instant = clip == null ? 0 : Math.round(time / QUANTUM_SECONDS);

        PaletteKey key = new PaletteKey(model, clip, instant);
        PaletteSlot cached = queue.palettes.get(key);
        if (cached != null) {
            DirectStats.palette(pass, true);
            return cached;
        }
        DirectStats.palette(pass, false);

        Matrix4f[] palette = SCRATCH.palette(model.jointCount());
        float[] morphBlock = model.morphs()
                .isEmpty() ? null : SCRATCH.morphBlock(model.morphs()
                .blockFloats());

        GltfPose.evaluate(model.layout(), clip, instant * QUANTUM_SECONDS, palette, model.morphs(),
                morphBlock, SCRATCH);

        PaletteSlot slot = stage(model, palette, morphBlock);
        queue.palettes.put(key, slot);
        return slot;
    }

    private static void write(Batch batch, int offset, Matrix4f pose, PaletteSlot slot, int light,
                              int overlay, int argb, VariantUv variant, Paint paint, int reference, int restBase) {
        ByteBuffer buffer = batch.instances;
        long address = MemoryUtil.memAddress(buffer) + offset;

        pose.getToAddress(address);

        MemoryUtil.memPutInt(address + 64, slot.boneBase());
        MemoryUtil.memPutInt(address + 68, slot.morphBase());

        if (light == EMISSIVE) {
            MemoryUtil.memPutFloat(address + 72, -1.0f);
            MemoryUtil.memPutFloat(address + 76, -1.0f);
        } else {
            MemoryUtil.memPutFloat(address + 72, ((light & 0xFFFF) + 8.0f) / 256.0f);
            MemoryUtil.memPutFloat(address + 76, ((light >>> 16 & 0xFFFF) + 8.0f) / 256.0f);
        }

        MemoryUtil.memPutByte(address + 80, (byte) (argb >> 16 & 0xFF));
        MemoryUtil.memPutByte(address + 81, (byte) (argb >> 8 & 0xFF));
        MemoryUtil.memPutByte(address + 82, (byte) (argb & 0xFF));
        MemoryUtil.memPutByte(address + 83, (byte) (argb >>> 24 & 0xFF));

        MemoryUtil.memPutFloat(address + 84, ((overlay & 0xFFFF) + 0.5f) / 16.0f);
        MemoryUtil.memPutFloat(address + 88, ((overlay >>> 16 & 0xFFFF) + 0.5f) / 16.0f);

        MemoryUtil.memPutFloat(address + 92, variant.u());
        MemoryUtil.memPutFloat(address + 96, variant.v());

        // reference, restBase < 2^24: exact as floats.
        MemoryUtil.memPutFloat(address + 100, paint.layer());
        MemoryUtil.memPutFloat(address + 104, paint.tilesPerBlock());
        MemoryUtil.memPutFloat(address + 108, reference);
        MemoryUtil.memPutFloat(address + 112, restBase);
    }

    private static DirectPass queueFor(DirectPass pass) {
        //? if >=26.1 {

        /*return sink;
         *///?} else {
        if (pass != DirectPass.LEVEL) {
            return pass;
        }
        if (!inLevel) {
            return DirectPass.GUI;
        }
        return guest != null && guest.active() && guest.shadowPass() ? DirectPass.SHADOW : pass;
        //?}
    }

    public static void installGuest(DirectGuest installed) {
        guest = installed;
    }

    static void releaseGuest() {
        if (guest != null) {
            guest.release();
        }
    }

    public static void beginLevel() {
        inLevel = true;
    }

    public static void endLevel() {
        inLevel = false;
    }

    public static void beginItemFlush() {
        itemFlush = true;
    }

    public static void endItemFlush() {
        itemFlush = false;
    }

    //? if >=26.1 {
	
	/*public static void beginSink(DirectPass pass) {
		sink = pass;
	}

	public static void endSink() {
		flush(sink);
		sink = DirectPass.LEVEL;
	}

	public static void beginItemContext(net.minecraft.world.item.ItemDisplayContext context) {
		itemContext = context;
	}

	public static void endItemContext() {
		itemContext = net.minecraft.world.item.ItemDisplayContext.NONE;
	}

	public static net.minecraft.world.item.ItemDisplayContext itemContext() {
		return itemContext;
	}
*///?}

    public static void flushGui() {
        if (itemFlush && guiQueued) {
            return;
        }
        flush(DirectPass.GUI);
    }

    public static void flush(DirectPass pass) {
        RenderSystem.assertOnRenderThread();

        if (pass == DirectPass.GUI) {
            guiQueued = false;
        }

        PassQueue queue = QUEUES.get(pass);
        if (queue == null || queue.isEmpty()) {
            if (queue != null) {
                queue.reset();
            }
            return;
        }

        if (pass != DirectPass.GUI && guest != null && guest.active()) {
            flushGuest(pass, queue);
            return;
        }

        DirectProgram program = DirectProgram.getInstance();
        if (!program.ensureCreated()) {
            queue.reset();
            return;
        }

        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        boolean previousCull = glGetInteger(GL_CULL_FACE) != 0;
        PASS_STATE.save();

        DirectStats.flushBegin(pass);

        try {
            BoneBuffer.direct()
                    .uploadAndBind();
            MorphBuffer.getInstance()
                    .bind();
            PaintArray.bind();

            program.use();
            program.matrices(new Matrix4f(RenderSystem.getModelViewMatrix()),
                    new Matrix4f(DirectVanilla.projection()));

            Vector3f[] lights = DirectVanilla.lightDirections();
            program.lightDirections(lights[0], lights[1]);

            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(GL_LEQUAL);
            // Inherited mask may drop alpha (26.1 GUI pipelines): an atlas slot cleared to alpha 0 stays invisible.
            com.wf.gemrender.render.GlState.colorMask(true, true, true, true);

            DirectVanilla.bindLightAndOverlay(UNIT_OVERLAY, UNIT_LIGHTMAP);

            drawsLastFlush = 0;
            instancesLastFlush = 0;
            drawPile(queue, program, false);
            drawPile(queue, program, true);
            palettesLastFlush = queue.palettes.size() + queue.blendPalettes.size();
        } finally {
            DirectVanilla.unbindTextures(UNIT_ATLAS, UNIT_OVERLAY, UNIT_LIGHTMAP);

            glBindVertexArray(previousVao);
            GlStateManager._glUseProgram(previousProgram);
            PASS_STATE.restore();
            if (previousCull) {
                GlStateManager._enableCull();
            } else {
                GlStateManager._disableCull();
            }
            GlStateManager._activeTexture(GL_TEXTURE0 + UNIT_ATLAS);

            DirectStats.flushEnd(pass, drawsLastFlush, instancesLastFlush);

            queue.reset();
        }
    }

    private static void flushGuest(DirectPass pass, PassQueue queue) {
        DirectStats.flushBegin(pass);
        List<Batch> batches = new ArrayList<>(queue.order.size());
        int instances = 0;
        for (Batch batch : queue.order) {
            if (batch.count > 0) {
                batches.add(batch);
                instances += batch.count;
            }
        }
        try {
            BoneBuffer.direct()
                    .uploadAndBind();
            MorphBuffer.getInstance()
                    .bind();
            guest.flush(pass, batches);
        } finally {
            drawsLastFlush = batches.size();
            instancesLastFlush = instances;
            palettesLastFlush = queue.palettes.size() + queue.blendPalettes.size();
            DirectStats.flushEnd(pass, batches.size(), instances);
            queue.reset();
        }
    }

    private static void drawPile(PassQueue queue, DirectProgram program, boolean blended) {
        for (Batch batch : queue.order) {
            if (batch.count == 0 || batch.part.material()
                    .blended() != blended) {
                continue;
            }

            DirectMaterial material = batch.part.material();

            if (blended) {
                GlStateManager._enableBlend();

                GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE,
                        GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager._depthMask(false);
            } else {
                GlStateManager._disableBlend();
                GlStateManager._depthMask(true);
            }

            if (material.doubleSided()) {
                GlStateManager._disableCull();
            } else {
                GlStateManager._enableCull();
            }

            program.alphaCutoff(material.alphaCutoff());
            DirectVanilla.bindTexture(UNIT_ATLAS, batch.texture != null ? batch.texture : material.texture());

            batch.instances.position(0)
                    .limit(batch.count * ResidentMesh.INSTANCE_STRIDE);
            batch.part.mesh()
                    .draw(batch.instances, batch.count);
            batch.instances.clear();

            drawsLastFlush++;
            instancesLastFlush += batch.count;
        }
    }

    static void freeAll() {
        for (PassQueue queue : QUEUES.values()) {
            for (Batch batch : queue.order) {
                batch.free();
            }
            queue.order.clear();
            queue.batches.clear();
            queue.slotBatches.clear();
            queue.palettes.clear();
            queue.blendPalettes.clear();
        }
        QUEUES.clear();
    }

    public static int drawsLastFlush() {
        return drawsLastFlush;
    }

    public static int palettesLastFlush() {
        return palettesLastFlush;
    }

    public static int instancesLastFlush() {
        return instancesLastFlush;
    }

    private static final class Batch implements DirectGuest.Batch {
        private final ResidentModel.Part part;
        @Nullable
        private final ResourceLocation texture;

        private ByteBuffer instances;
        private int count;

        Batch(ResidentModel.Part part, @Nullable ResourceLocation texture) {
            this.part = part;
            this.texture = texture;
            this.instances = MemoryUtil.memAlloc(16 * ResidentMesh.INSTANCE_STRIDE);
        }

        int reserve() {
            int offset = count * ResidentMesh.INSTANCE_STRIDE;
            if (offset + ResidentMesh.INSTANCE_STRIDE > instances.capacity()) {
                ByteBuffer grown = MemoryUtil.memAlloc(instances.capacity() * 2);
                MemoryUtil.memCopy(MemoryUtil.memAddress(instances), MemoryUtil.memAddress(grown), offset);
                MemoryUtil.memFree(instances);
                instances = grown;
            }
            count++;
            return offset;
        }

        void reset() {
            count = 0;
        }

        @Override
        public DirectMaterial material() {
            return part.material();
        }

        @Override
        public ResourceLocation texture() {
            return texture != null ? texture : part.material()
                    .texture();
        }

        @Override
        public int instances() {
            return count;
        }

        @Override
        public void draw() {
            instances.position(0)
                    .limit(count * ResidentMesh.INSTANCE_STRIDE);
            part.mesh()
                    .drawGuest(instances, count);
            instances.clear();
        }

        void free() {
            MemoryUtil.memFree(instances);
            instances = null;
        }
    }

    private static final class PassQueue {
        private final Map<ResidentModel.Part, Batch> batches = new IdentityHashMap<>();
        private final Map<SlotKey, Batch> slotBatches = new HashMap<>();
        private final SlotKey probe = new SlotKey();
        private final List<Batch> order = new ArrayList<>();

        private final Map<PaletteKey, PaletteSlot> palettes = new HashMap<>();

        private final Map<BlendKey, PaletteSlot> blendPalettes = new HashMap<>();

        Batch batch(ResidentModel.Part part) {
            Batch batch = batches.get(part);
            if (batch == null) {
                batch = new Batch(part, null);
                batches.put(part, batch);
                order.add(batch);
            }
            return batch;
        }

        Batch batch(ResidentModel.Part part, ResourceLocation texture) {
            Batch batch = slotBatches.get(probe.set(part, texture));
            if (batch == null) {
                batch = new Batch(part, texture);
                slotBatches.put(new SlotKey().set(part, texture), batch);
                order.add(batch);
            }
            return batch;
        }

        boolean isEmpty() {
            for (Batch batch : order) {
                if (batch.count > 0) {
                    return false;
                }
            }
            return true;
        }

        void reset() {
            for (Batch batch : order) {
                batch.reset();
            }
            palettes.clear();
            blendPalettes.clear();
        }
    }

    private record PaletteKey(GemRenderGltfModel model, @Nullable GltfAnimation clip, int instant) {
        @Override
        public boolean equals(Object other) {
            return other instanceof PaletteKey key && key.model == model && key.instant == instant
                    && Objects.equals(key.clip, clip);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(model) * 31 + instant * 31 + Objects.hashCode(clip);
        }
    }

    private record PaletteSlot(int boneBase, int morphBase) {
    }

    /** Mutable for an allocation-free lookup ({@code PassQueue.probe}); stored keys never change. */
    private static final class SlotKey {
        private ResidentModel.Part part;
        private ResourceLocation texture;

        SlotKey set(ResidentModel.Part part, ResourceLocation texture) {
            this.part = part;
            this.texture = texture;
            return this;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SlotKey key && key.part == part && key.texture.equals(texture);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(part) * 31 + texture.hashCode();
        }
    }
}
