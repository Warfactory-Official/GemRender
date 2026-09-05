package com.wf.gemrender.volume;

import org.lwjgl.system.MemoryUtil;

import com.wf.gemrender.GemRender;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.api.layout.UnsignedIntegerRepr;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import net.minecraft.resources.ResourceLocation;

public final class GemRenderVolumeTypes {
	public static final InstanceType<VolumeInstance> VOLUME = build("volume");

	private GemRenderVolumeTypes() {
	}

	private static InstanceType<VolumeInstance> build(String name) {
		return SimpleInstanceType.builder(VolumeInstance::new)
				.layout(LayoutBuilder.create()
						.vector("center", FloatRepr.FLOAT, 3)
						.scalar("volume", UnsignedIntegerRepr.UNSIGNED_INT)
						.build())
				.writer((ptr, instance) -> {
					MemoryUtil.memPutFloat(ptr, instance.centerX);
					MemoryUtil.memPutFloat(ptr + 4, instance.centerY);
					MemoryUtil.memPutFloat(ptr + 8, instance.centerZ);
					MemoryUtil.memPutInt(ptr + 12, instance.volume);
				})
				.vertexShader(shader("instance/" + name + ".vert"))
				.cullShader(shader("instance/cull/" + name + ".glsl"))
				.build();
	}

	private static ResourceLocation shader(String path) {
		return ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, path);
	}
}
