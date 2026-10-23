package com.wf.gemrender.rope;

import com.wf.gemrender.Ids;

import com.wf.gemrender.GemRender;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

/**
 * The instance type to pass to {@code instancer(...)} for a rope. The layout is tabulated in
 * docs/INTEGRATION.md; the writer below must stay in step with it field for field, because a
 * LayoutBuilder field lands exactly where a hand-written writer puts it and there is no padding.
 */
public final class GemRenderRopeTypes {
    public static final InstanceType<RopeInstance> ROPE = build("rope");

    private GemRenderRopeTypes() {
    }

    /**
     * Your own rope shader, over the same layout. Include {@code gemrender:rope.glsl} and the curve
     * you draw is the curve the cull shader tests, which is the point of it being a file.
     */
    public static InstanceType<RopeInstance> custom(ResourceLocation vertexShader,
                                                    ResourceLocation cullShader) {
        return build(vertexShader, cullShader);
    }

    private static InstanceType<RopeInstance> build(String name) {
        return build(shader("instance/" + name + ".vert"), shader("instance/cull/" + name + ".glsl"));
    }

    private static InstanceType<RopeInstance> build(ResourceLocation vertexShader,
                                                    ResourceLocation cullShader) {
        return SimpleInstanceType.builder(RopeInstance::new)
                .layout(LayoutBuilder.create()
                        .vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
                        .vector("lightA", FloatRepr.UNSIGNED_SHORT, 2)
                        .vector("lightB", FloatRepr.UNSIGNED_SHORT, 2)
                        .vector("a", FloatRepr.FLOAT, 3)
                        .vector("b", FloatRepr.FLOAT, 3)
                        .vector("shape", FloatRepr.FLOAT, 4)
                        .vector("sway", FloatRepr.FLOAT, 4)
                        .vector("sphere", FloatRepr.FLOAT, 4)
                        .build())
                .writer((ptr, instance) -> {
                    MemoryUtil.memPutByte(ptr, instance.red);
                    MemoryUtil.memPutByte(ptr + 1, instance.green);
                    MemoryUtil.memPutByte(ptr + 2, instance.blue);
                    MemoryUtil.memPutByte(ptr + 3, instance.alpha);
                    ExtraMemoryOps.put2x16(ptr + 4, instance.light);
                    ExtraMemoryOps.put2x16(ptr + 8, instance.lightB);
                    MemoryUtil.memPutFloat(ptr + 12, instance.a.x);
                    MemoryUtil.memPutFloat(ptr + 16, instance.a.y);
                    MemoryUtil.memPutFloat(ptr + 20, instance.a.z);
                    MemoryUtil.memPutFloat(ptr + 24, instance.b.x);
                    MemoryUtil.memPutFloat(ptr + 28, instance.b.y);
                    MemoryUtil.memPutFloat(ptr + 32, instance.b.z);
                    MemoryUtil.memPutFloat(ptr + 36, instance.shape.x);
                    MemoryUtil.memPutFloat(ptr + 40, instance.shape.y);
                    MemoryUtil.memPutFloat(ptr + 44, instance.shape.z);
                    MemoryUtil.memPutFloat(ptr + 48, instance.shape.w);
                    MemoryUtil.memPutFloat(ptr + 52, instance.sway.x);
                    MemoryUtil.memPutFloat(ptr + 56, instance.sway.y);
                    MemoryUtil.memPutFloat(ptr + 60, instance.sway.z);
                    MemoryUtil.memPutFloat(ptr + 64, instance.sway.w);
                    MemoryUtil.memPutFloat(ptr + 68, instance.sphere.x);
                    MemoryUtil.memPutFloat(ptr + 72, instance.sphere.y);
                    MemoryUtil.memPutFloat(ptr + 76, instance.sphere.z);
                    MemoryUtil.memPutFloat(ptr + 80, instance.sphere.w);
                })
                .vertexShader(vertexShader)
                .cullShader(cullShader)
                .build();
    }

    private static ResourceLocation shader(String path) {
        return Ids.of(GemRender.MOD_ID, path);
    }
}
