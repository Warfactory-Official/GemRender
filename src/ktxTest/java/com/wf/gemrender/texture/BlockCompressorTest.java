package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockCompressorTest {
	@Test
	@DisplayName("the output is exactly the size BC7 says it should be")
	void sizeIsBlockCountTimesSixteen() throws IOException {
		BlockCompressor.Blocks blocks = BlockCompressor.toBc7(64, 32, gradient(64, 32));

		assertThat(blocks.width()).isEqualTo(64);
		assertThat(blocks.height()).isEqualTo(32);
		assertThat(blocks.glFormat()).isEqualTo(BlockCompressor.GL_COMPRESSED_RGBA_BPTC_UNORM);
		assertThat(blocks.data()).hasSize(16 * 8 * BlockCompressor.BYTES_PER_BLOCK);

		assertThat(blocks.uncompressedBytes()).isEqualTo(blocks.data().length * 4);
	}

	@Test
	@DisplayName("dimensions that are not multiples of four round up to whole blocks")
	void partialBlocksRoundUp() throws IOException {
		BlockCompressor.Blocks blocks = BlockCompressor.toBc7(17, 5, gradient(17, 5));

		assertThat(blocks.data()).hasSize(5 * 2 * BlockCompressor.BYTES_PER_BLOCK);
		assertThat(BlockCompressor.blockBytes(17, 5)).isEqualTo(blocks.data().length);
	}

	@Test
	@DisplayName("a flat image compresses to one block repeated")
	void aFlatImageHasIdenticalBlocks() throws IOException {
		BlockCompressor.Blocks blocks = BlockCompressor.toBc7(32, 32, solid(32, 32, 0x20, 0xC0, 0x40));

		byte[] first = block(blocks, 0);
		for (int i = 1; i < blocks.data().length / BlockCompressor.BYTES_PER_BLOCK; i++) {
			assertThat(block(blocks, i))
					.as("block %d of a flat image", i)
					.isEqualTo(first);
		}
	}

	@Test
	@DisplayName("an image that varies does not")
	void aVaryingImageHasVaryingBlocks() throws IOException {
		BlockCompressor.Blocks blocks = BlockCompressor.toBc7(32, 32, gradient(32, 32));

		byte[] first = block(blocks, 0);
		boolean anyDifferent = false;
		for (int i = 1; i < blocks.data().length / BlockCompressor.BYTES_PER_BLOCK; i++) {
			anyDifferent |= !Arrays.equals(block(blocks, i), first);
		}

		assertThat(anyDifferent)
				.as("a gradient must not encode to one block repeated; that would be zeroed input")
				.isTrue();
	}

	@Test
	@DisplayName("two different images do not encode to the same bytes")
	void differentImagesDiffer() throws IOException {
		byte[] red = BlockCompressor.toBc7(32, 32, solid(32, 32, 0xFF, 0x00, 0x00))
				.data();
		byte[] blue = BlockCompressor.toBc7(32, 32, solid(32, 32, 0x00, 0x00, 0xFF))
				.data();

		assertThat(red).isNotEqualTo(blue);
	}

	@Test
	@DisplayName("the same image always encodes to the same bytes")
	void encodingIsDeterministic() throws IOException {
		byte[] source = gradient(48, 48);
		byte[] first = BlockCompressor.toBc7(48, 48, source)
				.data();

		for (int attempt = 0; attempt < 3; attempt++) {
			assertThat(BlockCompressor.toBc7(48, 48, source)
					.data()).isEqualTo(first);
		}
	}

	@Test
	@DisplayName("a wrong-sized buffer is refused rather than read past")
	void refusesAMismatchedBuffer() {
		assertThatThrownBy(() -> BlockCompressor.toBc7(16, 16, new byte[16 * 16 * 3]))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("expected");

		assertThatThrownBy(() -> BlockCompressor.toBc7(0, 16, new byte[0]))
				.isInstanceOf(IOException.class);
	}

	private static byte[] block(BlockCompressor.Blocks blocks, int index) {
		int start = index * BlockCompressor.BYTES_PER_BLOCK;
		return Arrays.copyOfRange(blocks.data(), start, start + BlockCompressor.BYTES_PER_BLOCK);
	}

	private static byte[] solid(int width, int height, int r, int g, int b) {
		byte[] rgba = new byte[width * height * KtxImage.BYTES_PER_PIXEL];
		for (int i = 0; i < rgba.length; i += KtxImage.BYTES_PER_PIXEL) {
			rgba[i] = (byte) r;
			rgba[i + 1] = (byte) g;
			rgba[i + 2] = (byte) b;
			rgba[i + 3] = (byte) 0xFF;
		}
		return rgba;
	}

	private static byte[] gradient(int width, int height) {
		byte[] rgba = new byte[width * height * KtxImage.BYTES_PER_PIXEL];
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				rgba[i] = (byte) (x * 255 / Math.max(1, width - 1));
				rgba[i + 1] = (byte) (y * 255 / Math.max(1, height - 1));
				rgba[i + 2] = (byte) ((x ^ y) & 0xFF);
				rgba[i + 3] = (byte) 0xFF;
				i += KtxImage.BYTES_PER_PIXEL;
			}
		}
		return rgba;
	}
}
