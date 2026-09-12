package com.wf.gemrender.texture;

import com.wf.gemrender.GemRender;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.CRC32C;

public final class BlockCache {

    public static final String DIRECTORY = ".gemrender";

    static final String SUFFIX = ".bc7";

    private static final int MAGIC = 0x47524237;

    private static final int VERSION = 1;

    private static final long MEGABYTE = 1024L * 1024L;

    private static final long DEFAULT_MAX_MEGABYTES = 512L;

    private static final int QUARTERS = 4;

    @Nullable
    private static BlockCache instance;

    @Nullable
    private final Path root;

    private final long maxBytes;

    private boolean complained;

    public BlockCache(@Nullable Path root, long maxBytes) {
        this.root = maxBytes <= 0 ? null : root;
        this.maxBytes = maxBytes;
    }

    public static synchronized BlockCache instance() {
        if (instance == null) {
            instance = new BlockCache(gameDirectory(), Long.getLong("gemrender.blockcache",
                    DEFAULT_MAX_MEGABYTES) * MEGABYTE);
        }
        return instance;
    }

    @Nullable
    private static Path gameDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return null;
        }
        return minecraft.gameDirectory.toPath()
                .resolve(DIRECTORY)
                .resolve("blocks");
    }

    public static String key(int width, int height, ByteBuffer pixels) {
        int size = pixels.remaining();
        int base = pixels.position();

        StringBuilder key = new StringBuilder(QUARTERS * 8);
        CRC32C sum = new CRC32C();

        for (int quarter = 0; quarter < QUARTERS; quarter++) {
            int from = (int) ((long) size * quarter / QUARTERS);
            int to = (int) ((long) size * (quarter + 1) / QUARTERS);

            sum.reset();
            sum.update(ByteBuffer.allocate(Integer.BYTES * 4)
                    .putInt(VERSION)
                    .putInt(width)
                    .putInt(height)
                    .putInt(quarter)
                    .flip());
            sum.update(pixels.slice(base + from, to - from));

            key.append(HexFormat.of()
                    .toHexDigits((int) sum.getValue()));
        }
        return key.toString();
    }

    public static String key(int width, int height, byte[] rgba) {
        return key(width, height, ByteBuffer.wrap(rgba));
    }

    @Nullable
    private static BlockCompressor.Blocks readEntry(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != VERSION) {
            return null;
        }

        int width = in.readInt();
        int height = in.readInt();
        int glFormat = in.readInt();
        int length = in.readInt();

        if (width <= 0 || height <= 0 || length != BlockCompressor.blockBytes(width, height)) {
            return null;
        }

        byte[] blocks = in.readNBytes(length);
        return blocks.length == length ? new BlockCompressor.Blocks(width, height, glFormat, blocks) : null;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static FileTime lastUsed(Path entry) {
        try {
            return Files.getLastModifiedTime(entry);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private static void touch(Path entry) {
        try {
            Files.setLastModifiedTime(entry, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {

        }
    }

    private static boolean discard(Path entry) {
        try {
            return Files.deleteIfExists(entry);
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return root != null;
    }

    @Nullable
    public BlockCompressor.Blocks read(String key) {
        if (root == null) {
            return null;
        }

        Path file = root.resolve(key + SUFFIX);
        BlockCompressor.Blocks blocks;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            blocks = readEntry(in);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException | RuntimeException e) {
            GemRender.LOGGER.warn("Discarding the cached blocks in {} ({})", file, e.toString());
            blocks = null;
        }

        if (blocks == null) {
            discard(file);
            return null;
        }

        touch(file);
        return blocks;
    }

    public synchronized void write(String key, BlockCompressor.Blocks blocks) {
        if (root == null) {
            return;
        }

        Path file = root.resolve(key + SUFFIX);
        Path partial = root.resolve(key + ".partial");
        try {
            Files.createDirectories(root);

            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(partial)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(blocks.width());
                out.writeInt(blocks.height());
                out.writeInt(blocks.glFormat());
                out.writeInt(blocks.data().length);
                out.write(blocks.data());
            }

            move(partial, file);
            evict();
        } catch (IOException | RuntimeException e) {
            if (!complained) {
                complained = true;
                GemRender.LOGGER.warn("Could not cache compressed blocks in {}; atlases will be encoded "
                        + "on every launch ({})", root, e.toString());
            }
            discard(partial);
        }
    }

    private void evict() throws IOException {
        List<Path> entries = new ArrayList<>();
        long total = 0;

        try (Stream<Path> listing = Files.list(root)) {
            for (Path entry : listing.filter(path -> path.getFileName()
                            .toString()
                            .endsWith(SUFFIX))
                    .toList()) {
                try {
                    total += Files.size(entry);
                    entries.add(entry);
                } catch (IOException e) {

                }
            }
        }

        if (total <= maxBytes) {
            return;
        }

        entries.sort(Comparator.comparing(BlockCache::lastUsed));

        int evicted = 0;
        for (Path entry : entries) {
            if (total <= maxBytes) {
                break;
            }
            long size;
            try {
                size = Files.size(entry);
            } catch (IOException e) {
                continue;
            }
            if (discard(entry)) {
                total -= size;
                evicted++;
            }
        }

        if (evicted > 0) {
            GemRender.LOGGER.info("Block cache: evicted {} entr{} to stay under {} MB", evicted,
                    evicted == 1 ? "y" : "ies", maxBytes / MEGABYTE);
        }
    }
}
