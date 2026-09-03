package com.wf.gemrender.texture;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;
import org.lwjgl.util.ktx.KTX;
import org.lwjgl.util.ktx.ktxBasisParams;
import org.lwjgl.util.ktx.ktxTexture;
import org.lwjgl.util.ktx.ktxTexture2;
import org.lwjgl.util.ktx.ktxTextureCreateInfo;

public final class KtxEncoder {
	private static final int VK_FORMAT_R8G8B8A8_UNORM = 37;

	private KtxEncoder() {
	}

	public static byte[] encode(int width, int height, byte[] rgba, boolean uastc) throws IOException {
		if (rgba.length != width * height * KtxImage.BYTES_PER_PIXEL) {
			throw new IllegalArgumentException("expected " + width * height * KtxImage.BYTES_PER_PIXEL
					+ " bytes of RGBA, got " + rgba.length);
		}

		ByteBuffer pixels = MemoryUtil.memAlloc(rgba.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			pixels.put(rgba)
					.flip();

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
			check(KTX.ktxTexture2_Create(info, KTX.KTX_TEXTURE_CREATE_ALLOC_STORAGE, handle),
					"create");

			long address = handle.get(0);
			ktxTexture2 texture = ktxTexture2.create(address);
			ktxTexture base = ktxTexture.create(address);
			try {
				check(KTX.ktxTexture_SetImageFromMemory(base, 0, 0, 0, pixels), "set image");

				ktxBasisParams params = ktxBasisParams.calloc(stack)
						.structSize(ktxBasisParams.SIZEOF)
						.uastc(uastc)
						.threadCount(1);
				if (uastc) {
					params.uastcFlags(KTX.KTX_PACK_UASTC_LEVEL_VERYSLOW);
				} else {
					params.compressionLevel(KTX.KTX_ETC1S_DEFAULT_COMPRESSION_LEVEL)
							.qualityLevel(255);
				}
				check(KTX.ktxTexture2_CompressBasisEx(texture, params), "compress");

				PointerBuffer out = stack.mallocPointer(1);
				PointerBuffer size = stack.mallocPointer(1);
				check(KTX.ktxTexture_WriteToMemory(base, out, size), "write");

				int length = (int) size.get(0);
				byte[] bytes = new byte[length];
				MemoryUtil.memByteBuffer(out.get(0), length)
						.get(bytes);

				LibCStdlib.nfree(out.get(0));
				return bytes;
			} finally {
				KTX.ktxTexture_Destroy(base);
			}
		} finally {
			MemoryUtil.memFree(pixels);
		}
	}

	private static void check(int result, String what) throws IOException {
		if (result != KTX.KTX_SUCCESS) {
			throw new IOException("libktx could not " + what + ": " + KTX.ktxErrorString(result));
		}
	}
}
