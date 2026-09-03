package com.wf.gemrender.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

public final class MakeKtxAssets {
	private static final boolean UASTC = true;

	private static final String[] SOURCES = {
			"src/main/resources/assets/gemrender/textures/models/glass_core.png",
	};

	private MakeKtxAssets() {
	}

	public static void main(String[] args) throws IOException {
		Path root = Path.of(args.length > 0 ? args[0] : ".");

		for (String source : SOURCES) {
			Path png = root.resolve(source);
			Path ktx2 = Path.of(png.toString()
					.replaceFirst("\\.png$", ModelTextures.KTX2_SUFFIX));

			BufferedImage image = ImageIO.read(png.toFile());
			if (image == null) {
				throw new IOException("could not read " + png);
			}

			byte[] rgba = toRgba(image);
			byte[] encoded = KtxEncoder.encode(image.getWidth(), image.getHeight(), rgba, UASTC);
			Files.write(ktx2, encoded);

			System.out.printf("Wrote %s (%d bytes, %dx%d %s) from %s (%d bytes)%n",
					root.relativize(ktx2), encoded.length, image.getWidth(), image.getHeight(),
					UASTC ? "UASTC" : "ETC1S", root.relativize(png), Files.size(png));
		}
	}

	private static byte[] toRgba(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		byte[] rgba = new byte[width * height * KtxImage.BYTES_PER_PIXEL];

		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = image.getRGB(x, y);
				rgba[i++] = (byte) (argb >> 16);
				rgba[i++] = (byte) (argb >> 8);
				rgba[i++] = (byte) argb;
				rgba[i++] = (byte) (argb >>> 24);
			}
		}
		return rgba;
	}
}
