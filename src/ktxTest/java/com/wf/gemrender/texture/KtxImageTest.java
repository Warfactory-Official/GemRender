package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class KtxImageTest {
	private static final int SIZE = 16;

	@Test
	@DisplayName("a UASTC-compressed image comes back within a few levels of what went in")
	void uastcRoundTrips() throws IOException {
		byte[] source = gradient(SIZE, SIZE);
		byte[] file = KtxEncoder.encode(SIZE, SIZE, source, true);

		KtxImage decoded = decode(file);

		assertThat(decoded.width()).isEqualTo(SIZE);
		assertThat(decoded.height()).isEqualTo(SIZE);
		assertThat(decoded.rgba()).hasSize(SIZE * SIZE * KtxImage.BYTES_PER_PIXEL);

		int worst = 0;
		long total = 0;
		for (int i = 0; i < source.length; i++) {
			int error = Math.abs((source[i] & 0xFF) - (decoded.rgba()[i] & 0xFF));
			worst = Math.max(worst, error);
			total += error;
		}

		assertThat(worst)
				.as("worst per-channel error after a UASTC round trip")
				.isLessThan(64);

		assertThat(total / (double) source.length)
				.as("mean per-channel error after a UASTC round trip")
				.isLessThan(8.0);
	}

	@Test
	@DisplayName("alpha survives, which is the channel a three-channel assumption would drop")
	void alphaSurvives() throws IOException {
		byte[] source = new byte[SIZE * SIZE * 4];
		for (int p = 0; p < SIZE * SIZE; p++) {
			source[p * 4] = (byte) 200;
			source[p * 4 + 1] = (byte) 40;
			source[p * 4 + 2] = (byte) 40;
			source[p * 4 + 3] = (byte) (p < SIZE * SIZE / 2 ? 255 : 0);
		}

		KtxImage decoded = decode(KtxEncoder.encode(SIZE, SIZE, source, true));

		assertThat(decoded.rgba()[3] & 0xFF).isGreaterThan(200);
		assertThat(decoded.rgba()[(SIZE * SIZE - 1) * 4 + 3] & 0xFF).isLessThan(55);
	}

	@Test
	@DisplayName("the channels are in the order the decoder claims")
	void channelsAreInOrder() throws IOException {
		byte[] source = new byte[SIZE * SIZE * 4];
		for (int p = 0; p < SIZE * SIZE; p++) {
			source[p * 4] = (byte) 255;
			source[p * 4 + 1] = 0;
			source[p * 4 + 2] = 0;
			source[p * 4 + 3] = (byte) 255;
		}

		KtxImage decoded = decode(KtxEncoder.encode(SIZE, SIZE, source, true));

		assertThat(decoded.rgba()[0] & 0xFF).isGreaterThan(230);
		assertThat(decoded.rgba()[1] & 0xFF).isLessThan(25);
		assertThat(decoded.rgba()[2] & 0xFF).isLessThan(25);
	}

	@Test
	@DisplayName("ETC1S transcodes too, and is much smaller and much lossier")
	void etc1sAlsoTranscodes() throws IOException {
		byte[] source = gradient(SIZE, SIZE);

		byte[] uastc = KtxEncoder.encode(SIZE, SIZE, source, true);
		byte[] etc1s = KtxEncoder.encode(SIZE, SIZE, source, false);

		assertThat(decode(uastc).rgba()).hasSize(source.length);
		assertThat(decode(etc1s).rgba()).hasSize(source.length);
		assertThat(etc1s.length).isLessThan(uastc.length);
	}

	@Test
	@DisplayName("a file that is not a KTX2 is refused rather than misread")
	void garbageIsRefused() {
		ByteBuffer buffer = MemoryUtil.memAlloc(64);
		try {
			for (int i = 0; i < 64; i++) {
				buffer.put((byte) i);
			}
			buffer.flip();

			assertThatThrownBy(() -> KtxImage.decode(buffer))
					.isInstanceOf(IOException.class)
					.hasMessageContaining("KTX2");
		} finally {
			MemoryUtil.memFree(buffer);
		}
	}

	@Test
	@DisplayName("reading a stream is the same as decoding its bytes")
	void readingAStreamWorks() throws IOException {
		byte[] file = KtxEncoder.encode(SIZE, SIZE, gradient(SIZE, SIZE), true);

		KtxImage fromStream = KtxImage.read(new ByteArrayInputStream(file));

		assertThat(fromStream.rgba()).isEqualTo(decode(file).rgba());
	}

	private static KtxImage decode(byte[] file) throws IOException {
		ByteBuffer buffer = MemoryUtil.memAlloc(file.length);
		try {
			buffer.put(file)
					.flip();
			return KtxImage.decode(buffer);
		} finally {
			MemoryUtil.memFree(buffer);
		}
	}

	private static byte[] gradient(int width, int height) {
		byte[] rgba = new byte[width * height * 4];
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				rgba[i++] = (byte) (x * 255 / (width - 1));
				rgba[i++] = (byte) (y * 255 / (height - 1));
				rgba[i++] = (byte) 128;
				rgba[i++] = (byte) 255;
			}
		}
		return rgba;
	}
}
