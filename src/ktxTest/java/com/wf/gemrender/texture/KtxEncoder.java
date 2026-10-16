package com.wf.gemrender.texture;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;
import org.lwjgl.util.ktx.ktxBasisParams;
import org.lwjgl.util.ktx.ktxTexture2;
import org.lwjgl.util.ktx.ktxTextureCreateInfo;
import org.lwjgl.util.ktx.ktxTexture_vtbl;

import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint32;

public final class KtxEncoder {
	private static final int VK_FORMAT_R8G8B8A8_UNORM = 37;
	private static final int PACK_UASTC_LEVEL_VERYSLOW = 4;
	private static final Libktx.Signature WRITE_TO_MEMORY = new Libktx.Signature(ffi_type_sint32,
			ffi_type_pointer, ffi_type_pointer, ffi_type_pointer);

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
			check(Libktx.create(info, Libktx.TEXTURE_CREATE_ALLOC_STORAGE, handle), "create");

			long address = handle.get(0);
			ktxTexture2 texture = ktxTexture2.create(address);
			try {
				check(Libktx.setImageFromMemory(address, pixels), "set image");

				ktxBasisParams params = ktxBasisParams.calloc(stack)
						.structSize(ktxBasisParams.SIZEOF)
						.uastc(uastc)
						.threadCount(1);
				if (uastc) {
					params.uastcFlags(PACK_UASTC_LEVEL_VERYSLOW);
				} else {
					params.compressionLevel(MemoryUtil.memGetInt(Libktx.symbol("KTX_ETC1S_DEFAULT_COMPRESSION_LEVEL")))
							.qualityLevel(255);
				}
				check(Libktx.compressBasisEx(texture, params), "compress");

				PointerBuffer out = stack.mallocPointer(1);
				PointerBuffer size = stack.mallocPointer(1);
				check((int) WRITE_TO_MEMORY.call(ktxTexture_vtbl.nWriteToMemory(Libktx.vtbl(address)), address,
						out.address(), size.address()), "write");

				int length = (int) size.get(0);
				byte[] bytes = new byte[length];
				MemoryUtil.memByteBuffer(out.get(0), length)
						.get(bytes);

				LibCStdlib.nfree(out.get(0));
				return bytes;
			} finally {
				Libktx.destroy(address);
			}
		} finally {
			MemoryUtil.memFree(pixels);
		}
	}

	private static void check(int result, String what) throws IOException {
		if (result != Libktx.SUCCESS) {
			throw new IOException("libktx could not " + what + ": " + Libktx.errorString(result));
		}
	}
}
