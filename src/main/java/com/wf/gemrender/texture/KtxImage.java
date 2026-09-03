package com.wf.gemrender.texture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.ktx.KTX;
import org.lwjgl.util.ktx.ktxTexture;
import org.lwjgl.util.ktx.ktxTexture2;

public record KtxImage(int width, int height, byte[] rgba) {
	private static final int VK_FORMAT_R8G8B8A8_UNORM = 37;

	private static final int VK_FORMAT_R8G8B8A8_SRGB = 43;

	public static final int BYTES_PER_PIXEL = 4;

	public static KtxImage read(InputStream in) throws IOException {
		byte[] bytes = in.readAllBytes();

		ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
		try {
			buffer.put(bytes)
					.flip();
			return decode(buffer);
		} finally {
			MemoryUtil.memFree(buffer);
		}
	}

	public static KtxImage decode(ByteBuffer ktx2) throws IOException {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer handle = stack.mallocPointer(1);

			int created = KTX.ktxTexture2_CreateFromMemory(ktx2,
					KTX.KTX_TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT, handle);
			if (created != KTX.KTX_SUCCESS) {
				throw new IOException("not a readable KTX2 file: " + KTX.ktxErrorString(created));
			}

			long address = handle.get(0);
			ktxTexture2 texture = ktxTexture2.create(address);
			try {
				return toRgba(texture, address);
			} finally {
				KTX.ktxTexture_Destroy(ktxTexture.create(address));
			}
		}
	}

	private static KtxImage toRgba(ktxTexture2 texture, long address) throws IOException {
		if (KTX.ktxTexture2_NeedsTranscoding(texture)) {
			int transcoded = KTX.ktxTexture2_TranscodeBasis(texture, KTX.KTX_TTF_RGBA32, 0);
			if (transcoded != KTX.KTX_SUCCESS) {
				throw new IOException("could not transcode Basis data to RGBA8: "
						+ KTX.ktxErrorString(transcoded));
			}
		}

		int format = texture.vkFormat();
		if (format != VK_FORMAT_R8G8B8A8_UNORM && format != VK_FORMAT_R8G8B8A8_SRGB) {
			throw new IOException("KTX2 is VkFormat " + format
					+ ", which is neither Basis data nor RGBA8; GemRender cannot use it");
		}

		int width = texture.baseWidth();
		int height = texture.baseHeight();

		long offset;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pOffset = stack.mallocPointer(1);
			int found = KTX.ktxTexture_GetImageOffset(ktxTexture.create(address), 0, 0, 0, pOffset);
			if (found != KTX.KTX_SUCCESS) {
				throw new IOException("KTX2 has no level 0: " + KTX.ktxErrorString(found));
			}
			offset = pOffset.get(0);
		}

		ByteBuffer data = KTX.ktxTexture_GetData(ktxTexture.create(address));
		int bytes = width * height * BYTES_PER_PIXEL;
		if (data == null || data.capacity() < offset + bytes) {
			throw new IOException("KTX2 image data is shorter than its own " + width + "x" + height
					+ " header claims");
		}

		byte[] rgba = new byte[bytes];
		data.position((int) offset)
				.get(rgba);
		return new KtxImage(width, height, rgba);
	}
}
