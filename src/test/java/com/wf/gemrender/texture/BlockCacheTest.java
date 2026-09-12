package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockCacheTest {

	private static final int BC7 = BlockCompressor.GL_COMPRESSED_RGBA_BPTC_UNORM;

	private static BlockCompressor.Blocks blocks(int width, int height, byte fill) {
		byte[] data = new byte[BlockCompressor.blockBytes(width, height)];
		Arrays.fill(data, fill);
		return new BlockCompressor.Blocks(width, height, BC7, data);
	}

	private static byte[] rgba(int width, int height, int seed) {
		byte[] pixels = new byte[width * height * KtxImage.BYTES_PER_PIXEL];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = (byte) (i * 31 + seed);
		}
		return pixels;
	}

	@Test
	@DisplayName("blocks written under a key come back byte for byte")
	void roundTrips(@TempDir Path root) {
		BlockCache cache = new BlockCache(root, 1 << 20);
		BlockCompressor.Blocks written = blocks(64, 32, (byte) 0x5A);

		cache.write("abc", written);
		BlockCompressor.Blocks read = cache.read("abc");

		assertThat(read).isNotNull();
		assertThat(read.width()).isEqualTo(64);
		assertThat(read.height()).isEqualTo(32);
		assertThat(read.glFormat()).isEqualTo(BC7);
		assertThat(read.data()).isEqualTo(written.data());
	}

	@Test
	@DisplayName("a key nothing was written under is a miss, not an error")
	void missesAreQuiet(@TempDir Path root) {
		assertThat(new BlockCache(root, 1 << 20).read("nothing")).isNull();
	}

	@Test
	@DisplayName("the key is the pixels, so the same sheet hits and a changed one does not")
	void keyIsTheContent() {
		byte[] sheet = rgba(16, 16, 0);

		assertThat(BlockCache.key(16, 16, sheet)).isEqualTo(BlockCache.key(16, 16, sheet.clone()));
		assertThat(BlockCache.key(16, 16, sheet)).isNotEqualTo(BlockCache.key(16, 16, rgba(16, 16, 1)));
	}

	@Test
	@DisplayName("two sheets of the same byte count but different shapes are different keys")
	void keyCoversTheShape() {
		assertThat(BlockCache.key(16, 64, rgba(32, 32, 0)))
				.isNotEqualTo(BlockCache.key(64, 16, rgba(32, 32, 0)));
	}

	@Test
	@DisplayName("a truncated entry is discarded rather than read as blocks")
	void truncatedEntriesAreDiscarded(@TempDir Path root) throws IOException {
		BlockCache cache = new BlockCache(root, 1 << 20);
		cache.write("abc", blocks(64, 32, (byte) 1));

		Path entry = root.resolve("abc" + BlockCache.SUFFIX);
		byte[] whole = Files.readAllBytes(entry);
		Files.write(entry, Arrays.copyOf(whole, whole.length - 16));

		assertThat(cache.read("abc")).isNull();
		assertThat(entry)
				.as("a file that cannot be read must not be left to fail again next launch")
				.doesNotExist();
	}

	@Test
	@DisplayName("a file that is not ours is discarded rather than read as blocks")
	void foreignFilesAreDiscarded(@TempDir Path root) throws IOException {
		Files.createDirectories(root);
		Path entry = root.resolve("abc" + BlockCache.SUFFIX);
		Files.write(entry, "not blocks, just some bytes that happen to sit here".getBytes());

		assertThat(new BlockCache(root, 1 << 20).read("abc")).isNull();
		assertThat(entry).doesNotExist();
	}

	@Test
	@DisplayName("a disabled cache misses and writes nothing")
	void zeroCapDisablesIt(@TempDir Path root) throws IOException {
		BlockCache cache = new BlockCache(root, 0);

		assertThat(cache.isEnabled()).isFalse();
		cache.write("abc", blocks(64, 32, (byte) 1));

		assertThat(cache.read("abc")).isNull();
		try (var listing = Files.list(root)) {
			assertThat(listing.toList()).isEmpty();
		}
	}

	@Test
	@DisplayName("the least recently used entry is evicted once the directory passes the cap")
	void evictsLeastRecentlyUsed(@TempDir Path root) throws IOException {

		BlockCompressor.Blocks one = blocks(64, 64, (byte) 1);
		int entry = BlockCompressor.blockBytes(64, 64);
		BlockCache cache = new BlockCache(root, entry * 2L + 128);

		cache.write("old", one);
		cache.write("cold", one);
		age(root.resolve("old" + BlockCache.SUFFIX), 20_000);
		age(root.resolve("cold" + BlockCache.SUFFIX), 10_000);

		assertThat(cache.read("old")).isNotNull();
		cache.write("new", one);

		assertThat(cache.read("old")).isNotNull();
		assertThat(cache.read("new")).isNotNull();
		assertThat(cache.read("cold"))
				.as("evicting by last use, not by when it was written")
				.isNull();
	}

	@Test
	@DisplayName("nothing is evicted while the directory is under the cap")
	void keepsEverythingUnderTheCap(@TempDir Path root) {
		BlockCompressor.Blocks one = blocks(64, 64, (byte) 1);
		BlockCache cache = new BlockCache(root, 1 << 20);

		cache.write("a", one);
		cache.write("b", one);
		cache.write("c", one);

		assertThat(cache.read("a")).isNotNull();
		assertThat(cache.read("b")).isNotNull();
		assertThat(cache.read("c")).isNotNull();
	}

	@Test
	@DisplayName("a write leaves no partial file behind")
	void writesAreAtomic(@TempDir Path root) throws IOException {
		BlockCache cache = new BlockCache(root, 1 << 20);
		cache.write("abc", blocks(64, 32, (byte) 1));

		try (var listing = Files.list(root)) {
			assertThat(listing.map(path -> path.getFileName()
					.toString())
					.toList()).containsExactly("abc" + BlockCache.SUFFIX);
		}
	}

	private static void age(Path entry, long millis) throws IOException {
		Files.setLastModifiedTime(entry, FileTime.fromMillis(System.currentTimeMillis() - millis));
	}

	@Test
	@DisplayName("a sheet keyed as a buffer and as an array is the same sheet")
	void bufferAndArrayAgree() {
		byte[] sheet = rgba(16, 16, 3);

		assertThat(BlockCache.key(16, 16, sheet))
				.isEqualTo(BlockCache.key(16, 16, java.nio.ByteBuffer.wrap(sheet)));
	}

	@Test
	@DisplayName("keying reads the buffer without consuming it")
	void keyingLeavesThePositionAlone() {
		java.nio.ByteBuffer sheet = java.nio.ByteBuffer.wrap(rgba(16, 16, 4));

		BlockCache.key(16, 16, sheet);

		assertThat(sheet.position()).isZero();
		assertThat(sheet.remaining()).isEqualTo(16 * 16 * KtxImage.BYTES_PER_PIXEL);
	}

	@Test
	@DisplayName("a single changed byte anywhere in the sheet changes the key")
	void everyQuarterIsCovered() {
		byte[] sheet = rgba(32, 32, 0);
		String whole = BlockCache.key(32, 32, sheet);

		for (int at : new int[] { 0, sheet.length / 4, sheet.length / 2, sheet.length - 1 }) {
			byte[] changed = sheet.clone();
			changed[at] ^= 0x01;
			assertThat(BlockCache.key(32, 32, changed))
					.as("a change at byte %d must not hash the same", at)
					.isNotEqualTo(whole);
		}
	}
}
