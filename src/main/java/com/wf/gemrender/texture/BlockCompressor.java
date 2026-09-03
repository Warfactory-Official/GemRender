package com.wf.gemrender.texture;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.ktx.KTX;
import org.lwjgl.util.ktx.ktxBasisParams;
import org.lwjgl.util.ktx.ktxTexture;
import org.lwjgl.util.ktx.ktxTexture2;
import org.lwjgl.util.ktx.ktxTextureCreateInfo;

public final class BlockCompressor {
	public static final int BLOCK = 4;

	public static final int GL_COMPRESSED_RGBA_BPTC_UNORM = 0x8E8C;

	public static final int BYTES_PER_BLOCK = 16;

	private static final int VK_FORMAT_R8G8B8A8_UNORM = 37;

	public record Blocks(int width, int height, int glFormat, byte[] data) {
		public int uncompressedBytes() {
			return width * height * KtxImage.BYTES_PER_PIXEL;
		}
	}

	private BlockCompressor() {
	}

	public static int blockBytes(int width, int height) {
		return ceilBlocks(width) * ceilBlocks(height) * BYTES_PER_BLOCK;
	}

	public static Blocks toBc7(int width, int height, byte[] rgba) throws IOException {
		if (width <= 0 || height <= 0) {
			throw new IOException("cannot compress a " + width + "x" + height + " image");
		}
		int expected = width * height * KtxImage.BYTES_PER_PIXEL;
		if (rgba.length != expected) {
			throw new IOException("expected " + expected + " bytes for " + width + "x" + height
					+ ", got " + rgba.length);
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			ktxTextureCreateInfo info = ktxTextureCreateInfo.calloc(stack)
					.vkFormat(VK_FORMAT_R8G8B8A8_UNORM)
					.baseWidth(width)
					.baseHeight(height)
					.baseDepth(1)
					.numDimensions(2)
					.numLevels(1)
					.numLayers(1)
					.numFaces(1)
					.isArray(false)
					.generateMipmaps(false);

			PointerBuffer handle = stack.mallocPointer(1);

			int created = KTX.ktxTexture2_Create(info, KTX.KTX_TEXTURE_CREATE_ALLOC_STORAGE, handle);
			if (created != KTX.KTX_SUCCESS) {
				throw new IOException("could not create a KTX2 texture: " + KTX.ktxErrorString(created));
			}

			long address = handle.get(0);
			try {
				return encode(address, width, height, rgba, stack);
			} finally {
				KTX.ktxTexture_Destroy(ktxTexture.create(address));
			}
		}
	}

	private static Blocks encode(long address, int width, int height, byte[] rgba, MemoryStack stack)
			throws IOException {
		ByteBuffer pixels = MemoryUtil.memAlloc(rgba.length);
		try {
			pixels.put(rgba)
					.flip();
			int set = KTX.ktxTexture_SetImageFromMemory(ktxTexture.create(address), 0, 0, 0, pixels);
			if (set != KTX.KTX_SUCCESS) {
				throw new IOException("could not load pixels into a KTX2 texture: "
						+ KTX.ktxErrorString(set));
			}
		} finally {
			MemoryUtil.memFree(pixels);
		}

		ktxTexture2 texture = ktxTexture2.create(address);

		ktxBasisParams params = ktxBasisParams.calloc(stack)
				.structSize(ktxBasisParams.SIZEOF)
				.uastc(true)
				.uastcFlags(KTX.KTX_PACK_UASTC_LEVEL_FASTEST)
				.threadCount(Math.max(1, Runtime.getRuntime()
						.availableProcessors() / 2));

		int compressed = KTX.ktxTexture2_CompressBasisEx(texture, params);
		if (compressed != KTX.KTX_SUCCESS) {
			throw new IOException("Basis encode failed: " + KTX.ktxErrorString(compressed));
		}

		int transcoded = KTX.ktxTexture2_TranscodeBasis(texture, KTX.KTX_TTF_BC7_RGBA, 0);
		if (transcoded != KTX.KTX_SUCCESS) {
			throw new IOException("could not transcode to BC7: " + KTX.ktxErrorString(transcoded));
		}

		PointerBuffer pOffset = stack.mallocPointer(1);
		int found = KTX.ktxTexture_GetImageOffset(ktxTexture.create(address), 0, 0, 0, pOffset);
		if (found != KTX.KTX_SUCCESS) {
			throw new IOException("encoded KTX2 has no level 0: " + KTX.ktxErrorString(found));
		}
		long offset = pOffset.get(0);

		int size = blockBytes(width, height);
		ByteBuffer data = KTX.ktxTexture_GetData(ktxTexture.create(address));
		if (data == null || data.capacity() < offset + size) {
			throw new IOException("encoded KTX2 is shorter than the " + size + " bytes BC7 needs for "
					+ width + "x" + height);
		}

		byte[] blocks = new byte[size];
		data.position((int) offset)
				.get(blocks);
		return new Blocks(width, height, GL_COMPRESSED_RGBA_BPTC_UNORM, blocks);
	}

	private static int ceilBlocks(int pixels) {
		return (pixels + BLOCK - 1) / BLOCK;
	}
}
