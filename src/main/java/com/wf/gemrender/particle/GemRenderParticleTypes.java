package com.wf.gemrender.particle;

import org.lwjgl.system.MemoryUtil;

import com.wf.gemrender.GemRender;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.api.layout.UnsignedIntegerRepr;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import net.minecraft.resources.ResourceLocation;

public final class GemRenderParticleTypes {
	public static final InstanceType<ParticleInstance> BILLBOARD = build("particle");

	public static final InstanceType<ParticleInstance> MESH = build("particle_mesh");

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
		return ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, path);
	}
}
