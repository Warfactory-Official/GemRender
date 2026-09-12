package com.wf.gemrender.direct;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33C.GL_TEXTURE0;

//? if <26.1 {
import com.wf.gemrender.mixin.direct.RenderSystemAccessor;
import net.minecraft.client.renderer.GameRenderer;
//?}
//? if >=26.1 {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.opengl.GL33C;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
*///?}

public final class DirectVanilla {
    //? if >=26.1 {
	
	/*private static final Map<GpuBufferSlice, Matrix4f> PROJECTIONS = new ConcurrentHashMap<>();

	private static final Matrix4f FALLBACK_PROJECTION = new Matrix4f();

	private static final Vector3f DIFFUSE_LIGHT_0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
	private static final Vector3f DIFFUSE_LIGHT_1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
	private static final Vector3f NETHER_DIFFUSE_LIGHT_0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
	private static final Vector3f NETHER_DIFFUSE_LIGHT_1 = new Vector3f(-0.2f, -1.0f, 0.7f).normalize();
	private static final Vector3f INVENTORY_DIFFUSE_LIGHT_0 = new Vector3f(0.2f, -1.0f, 1.0f).normalize();
	private static final Vector3f INVENTORY_DIFFUSE_LIGHT_1 = new Vector3f(-0.2f, -1.0f, 0.0f).normalize();

	private static final Vector3f[] FLAT = transformed(new Matrix4f().rotationY((float) (-Math.PI / 8))
			.rotateX((float) (Math.PI * 3.0 / 4.0)), DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1);

	private static final Vector3f[] ITEMS_3D = transformed(new Matrix4f().scaling(1.0f, -1.0f, 1.0f)
			.rotateYXZ(1.0821041f, 3.2375858f, 0.0f)
			.rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0f), DIFFUSE_LIGHT_0,
			DIFFUSE_LIGHT_1);

	private static final Vector3f[] ENTITY_IN_UI = { INVENTORY_DIFFUSE_LIGHT_0, INVENTORY_DIFFUSE_LIGHT_1 };

	private static final Vector3f[] LEVEL = { DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1 };
	private static final Vector3f[] LEVEL_NETHER = { NETHER_DIFFUSE_LIGHT_0, NETHER_DIFFUSE_LIGHT_1 };

	private static Lighting.Entry entry = Lighting.Entry.LEVEL;
*///?}

    private DirectVanilla() {
    }

    //? if >=26.1 {
	/*private static Vector3f[] transformed(Matrix4f pose, Vector3f light0, Vector3f light1) {
		return new Vector3f[] { pose.transformDirection(light0, new Vector3f()),
				pose.transformDirection(light1, new Vector3f()) };
	}

	public static void recordProjection(GpuBufferSlice slice, Matrix4f matrix) {
		PROJECTIONS.computeIfAbsent(slice, key -> new Matrix4f())
				.set(matrix);
	}

	public static void recordLightEntry(Lighting.Entry lightingEntry) {
		entry = lightingEntry;
	}
*///?}

    static Matrix4f projection() {
        //? if >=26.1 {
		/*GpuBufferSlice slice = RenderSystem.getProjectionMatrixBuffer();
		if (slice == null) {
			return FALLBACK_PROJECTION;
		}
		Matrix4f matrix = PROJECTIONS.get(slice);
		return matrix == null ? FALLBACK_PROJECTION : matrix;
*///?} else {
        return RenderSystem.getProjectionMatrix();
        //?}
    }

    static Vector3f[] lightDirections() {
        //? if >=26.1 {
		/*return switch (entry) {
			case ITEMS_FLAT -> FLAT;
			case ITEMS_3D -> ITEMS_3D;
			case ENTITY_IN_UI, PLAYER_SKIN -> ENTITY_IN_UI;
			case LEVEL -> nether() ? LEVEL_NETHER : LEVEL;
		};
*///?} else {
        return RenderSystemAccessor.gemrender$shaderLightDirections();
        //?}
    }

    //? if >=26.1 {
	/*private static boolean nether() {
		var level = Minecraft.getInstance().level;
		return level != null && level.dimensionType()
				.cardinalLightType() == net.minecraft.world.level.CardinalLighting.Type.NETHER;
	}
*///?}

    static void bindLightAndOverlay(int overlayUnit, int lightUnit) {
        //? if >=26.1 {
		/*var gameRenderer = Minecraft.getInstance().gameRenderer;

		GpuSampler sampler = RenderSystem.getSamplerCache()
				.getClampToEdge(FilterMode.LINEAR);
		bindView(overlayUnit, gameRenderer.overlayTexture()
				.getTextureView(), sampler);
		bindView(lightUnit, gameRenderer.lightmap(), sampler);
*///?} else {
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        renderer.lightTexture()
                .turnOnLightLayer();
        renderer.overlayTexture()
                .setupOverlayColor();

        bindId(overlayUnit, RenderSystem.getShaderTexture(overlayUnit));
        bindId(lightUnit, RenderSystem.getShaderTexture(lightUnit));
        //?}
    }

    static void unbindTextures(int atlasUnit, int overlayUnit, int lightUnit) {
        //? if >=26.1 {
		
		/*GL33C.glBindSampler(atlasUnit, 0);
		GL33C.glBindSampler(overlayUnit, 0);
		GL33C.glBindSampler(lightUnit, 0);
*///?} else {
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        renderer.overlayTexture()
                .teardownOverlayColor();
        renderer.lightTexture()
                .turnOffLightLayer();
        //?}
    }

    static void bindTexture(int unit, ResourceLocation texture) {
        AbstractTexture bound = Minecraft.getInstance()
                .getTextureManager()
                .getTexture(texture);
        //? if >=26.1 {
        /*bindView(unit, bound.getTextureView(), bound.getSampler());
         *///?} else {
        bindId(unit, bound.getId());
        //?}
    }

    //? if >=26.1 {
	/*private static void bindView(int unit, GpuTextureView view, GpuSampler sampler) {
		GlStateManager._activeTexture(GL_TEXTURE0 + unit);
		GlStateManager._bindTexture(((GlTexture) view.texture()).glId());
		GL33C.glBindSampler(unit, ((GlSampler) sampler).getId());
	}
*///?}

    static void bindId(int unit, int id) {
        GlStateManager._activeTexture(GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(id);
    }
}
