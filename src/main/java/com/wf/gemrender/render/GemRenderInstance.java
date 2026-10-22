package com.wf.gemrender.render;

import com.wf.gemrender.texture.Paint;
import com.wf.gemrender.texture.VariantUv;
import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.ColoredLitInstance;
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

/**
 * The instance a visual writes: {@code pose}, {@code boneBase}, {@code morphBase}, {@code boneSphere}.
 *
 * <p>Leaving {@code boneSphere} at its default culls the geometry away without an error.
 */
public class GemRenderInstance extends ColoredLitInstance {
    public final Matrix4f pose = new Matrix4f();
    public final Vector4f boneSphere = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
    /**
     * Which variant of the model's sheet this copy wears, as an offset added to every texture
     * coordinate. Zero is the tile the mesh's coordinates were baked into.
     *
     * <p>Set it from {@code model.variant(i)}; the numbers are decided at import by where the packer
     * put each tile, and computing one by hand will read a neighbouring skin at the edges rather than
     * fail.
     */
    public final Vector2f uvOffset = new Vector2f();
    public int boneBase = 0;
    public int morphBase = 0;
    /**
     * {@link #NO_JOINT_VARIANTS}, or the float offset of a {@link BoneBuffer#addFloatBlock} holding one
     * {@code (u, v)} per joint: each vertex takes its dominant joint's offset instead of {@link #uvOffset}.
     * Per-bone skins (damage, per-part paint) in one draw. Re-add every frame, like the palette.
     */
    public int jointUvBase = NO_JOINT_VARIANTS;
    /**
     * {@link Paint#layer}; -1 = unpainted. Only for a {@code paintable()} model; painted => tint rgb ignored.
     */
    public int paint = -1;
    public float paintScale;
    /**
     * The model's {@code paintReference}.
     */
    public int paintReference;
    /**
     * {@link BoneBuffer#addSharedPalette} of the model's cached {@code restPalette()}, every frame like the palette.
     */
    public int paintRestBase;

    public static final int NO_JOINT_VARIANTS = -1;

    public GemRenderInstance(InstanceType<? extends GemRenderInstance> type, InstanceHandle handle) {
        super(type, handle);
    }

    public GemRenderInstance boneBase(int boneBase) {
        this.boneBase = boneBase;
        return write();
    }

    public GemRenderInstance boneSphere(Vector4fc sphere) {
        this.boneSphere.set(sphere);
        return write();
    }

    //? if >=26.2 {
    /*/^* Layout puts light at 4; CrankShaft's {@code ColoredLitInstance} writes 8. ^/
    @Override
    public GemRenderInstance light(int light) {
        ExtraMemoryOps.put2x16(slabPtr() + 4, light);
        return this;
    }
    *///?}

    /** Serializes every field to the slab; call after mutating a field directly, before {@code setChanged()}. */
    //? if >=26.2 {
    /*public GemRenderInstance write() {
    *///?} else {
    private GemRenderInstance write() {
    //?}
        //? if >=26.2 {
        /*long p = slabPtr();
        MemoryUtil.memPutInt(p + 8, boneBase);
        MemoryUtil.memPutInt(p + 12, morphBase);
        MemoryUtil.memPutFloat(p + 16, boneSphere.x);
        MemoryUtil.memPutFloat(p + 20, boneSphere.y);
        MemoryUtil.memPutFloat(p + 24, boneSphere.z);
        MemoryUtil.memPutFloat(p + 28, boneSphere.w);
        ExtraMemoryOps.putMatrix4f(p + 32, pose);
        MemoryUtil.memPutFloat(p + 96, uvOffset.x);
        MemoryUtil.memPutFloat(p + 100, uvOffset.y);
        MemoryUtil.memPutInt(p + 104, jointUvBase);
        MemoryUtil.memPutInt(p + 108, paint);
        MemoryUtil.memPutFloat(p + 112, paintScale);
        MemoryUtil.memPutInt(p + 116, paintReference);
        MemoryUtil.memPutInt(p + 120, paintRestBase);
        *///?}
        return this;
    }

    /**
     * Wears one of the model's variants. {@link VariantUv#NONE} is the base.
     */
    public GemRenderInstance variant(VariantUv variant) {
        uvOffset.set(variant.u(), variant.v());
        return write();
    }

    public GemRenderInstance jointVariants(int floatBase) {
        this.jointUvBase = floatBase;
        return write();
    }

    /**
     * Pattern in rest-pose model space (moves with its part), triplanar; masked texels become
     * {@code base * paint / reference}.
     * {@link Paint#NONE} = authored look. Skip while {@link com.wf.gemrender.iris.ShaderPacks#inUse}: a pack
     * drops the fragment stage that undoes the painted vertex outputs.
     */
    public GemRenderInstance paint(Paint paint, int reference, int restBase) {
        this.paint = paint.layer();
        this.paintScale = paint.tilesPerBlock();
        this.paintReference = reference;
        this.paintRestBase = restBase;
        return write();
    }

    public GemRenderInstance setPose(Matrix4f pose) {
        this.pose.set(pose);
        return write();
    }

    /**
     * Draws nothing, without giving the instance up.
     *
     * <p>For a pool: a crowd whose size changes every few seconds would churn the instancer's buffers if
     * it created and deleted instances to match, so the surplus is collapsed instead and reused when the
     * crowd grows again. The counterpart of Flywheel's {@code TransformedInstance.setZeroTransform}, and
     * it has to zero the bounding sphere as well: geometry that collapses to a point still costs a
     * vertex shader run per vertex unless the culling pass throws the instance away first.
     */
    public GemRenderInstance setZeroTransform() {
        pose.zero();
        boneSphere.set(0.0f, 0.0f, 0.0f, 0.0f);
        return write();
    }
}
