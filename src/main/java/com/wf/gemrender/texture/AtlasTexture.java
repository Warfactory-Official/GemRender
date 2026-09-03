package com.wf.gemrender.texture;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;

public final class AtlasTexture extends DynamicTexture {
	private final int bands;

	public AtlasTexture(NativeImage sheet, int bands) {
		super(sheet);
		this.bands = bands;
	}

	public int bands() {
		return bands;
	}
}
