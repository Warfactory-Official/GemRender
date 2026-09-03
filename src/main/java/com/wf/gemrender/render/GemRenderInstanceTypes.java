package com.wf.gemrender.render;

import org.lwjgl.system.MemoryUtil;

import com.wf.gemrender.GemRender;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.api.layout.UnsignedIntegerRepr;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps;
import net.minecraft.resources.ResourceLocation;

/** The instance type to pass to {@code instancer(...)}; layout is tabulated in docs/INTEGRATION.md. */
public final class GemRenderInstanceTypes {
	public static final InstanceType<GemRenderInstance> SKINNED = SimpleInstanceType.builder(GemRenderInstance::new)
			.layout(LayoutBuilder.create()
					.vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
					.vector("light", FloatRepr.UNSIGNED_SHORT, 2)
					.scalar("boneBase", UnsignedIntegerRepr.UNSIGNED_INT)
					.scalar("morphBase", UnsignedIntegerRepr.UNSIGNED_INT)
					.vector("boneSphere", FloatRepr.FLOAT, 4)
					.matrix("pose", FloatRepr.FLOAT, 4)
					.build())
			.writer((ptr, instance) -> {
				MemoryUtil.memPutByte(ptr, instance.red);
				MemoryUtil.memPutByte(ptr + 1, instance.green);
				MemoryUtil.memPutByte(ptr + 2, instance.blue);
				MemoryUtil.memPutByte(ptr + 3, instance.alpha);
				ExtraMemoryOps.put2x16(ptr + 4, instance.light);
				MemoryUtil.memPutInt(ptr + 8, instance.boneBase);
				MemoryUtil.memPutInt(ptr + 12, instance.morphBase);
				MemoryUtil.memPutFloat(ptr + 16, instance.boneSphere.x);
				MemoryUtil.memPutFloat(ptr + 20, instance.boneSphere.y);
				MemoryUtil.memPutFloat(ptr + 24, instance.boneSphere.z);
				MemoryUtil.memPutFloat(ptr + 28, instance.boneSphere.w);
				ExtraMemoryOps.putMatrix4f(ptr + 32, instance.pose);
			})

			.vertexShader(shader("instance/skinned.vert"))
			.cullShader(shader("instance/cull/skinned.glsl"))
			.build();

	private GemRenderInstanceTypes() {
	}

	private static ResourceLocation shader(String path) {
		return ResourceLocation.fromNamespaceAndPath(GemRender.MOD_ID, path);
	}
}
