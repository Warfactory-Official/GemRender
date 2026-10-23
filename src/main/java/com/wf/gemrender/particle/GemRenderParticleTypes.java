package com.wf.gemrender.particle;

import com.wf.gemrender.Ids;

import com.wf.gemrender.GemRender;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.api.layout.UnsignedIntegerRepr;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

public final class GemRenderParticleTypes {
    public static final InstanceType<ParticleInstance> BILLBOARD = build("particle");

    public static final InstanceType<ParticleInstance> MESH = build("particle_mesh");

    /** Flat on a surface; spawn with {@link ParticleEmitter#spawnDecal}. Draw with {@link ParticleModels#decal}. */
    public static final InstanceType<ParticleInstance> DECAL = build("particle_decal");

    /** Camera-facing quad stretched along the velocity; length from {@link ParticleStyle.Builder#streak}. */
    public static final InstanceType<ParticleInstance> STREAK = build("particle_streak");

    /**
     * A model that tumbles end over end about a horizontal axis and comes to rest lying down: +Y ends
     * horizontal. {@code spinPhase} yaws the tumble axis and offsets the start angle. Casings, shells, debris
     * with a long axis. {@link ParticleModels#rigid} turns a glTF into one.
     */
    public static final InstanceType<ParticleInstance> BODY = build("particle_body");

    private GemRenderParticleTypes() {
    }

    public static InstanceType<ParticleInstance> custom(ResourceLocation vertexShader,
                                                        ResourceLocation cullShader) {
        return build(vertexShader, cullShader);
    }

    private static InstanceType<ParticleInstance> build(String name) {
        return build(shader("instance/" + name + ".vert"), shader("instance/cull/" + name + ".glsl"));
    }

    private static InstanceType<ParticleInstance> build(ResourceLocation vertexShader,
                                                        ResourceLocation cullShader) {
        return SimpleInstanceType.builder(ParticleInstance::new)
                .layout(LayoutBuilder.create()
                        .vector("origin", FloatRepr.FLOAT, 3)
                        .scalar("particle", UnsignedIntegerRepr.UNSIGNED_INT)
                        .build())
                .writer((ptr, instance) -> {
                    MemoryUtil.memPutFloat(ptr, instance.originX);
                    MemoryUtil.memPutFloat(ptr + 4, instance.originY);
                    MemoryUtil.memPutFloat(ptr + 8, instance.originZ);
                    MemoryUtil.memPutInt(ptr + 12, instance.particle);
                })
                .vertexShader(vertexShader)
                .cullShader(cullShader)
                .build();
    }

    private static ResourceLocation shader(String path) {
        return Ids.of(GemRender.MOD_ID, path);
    }
}
