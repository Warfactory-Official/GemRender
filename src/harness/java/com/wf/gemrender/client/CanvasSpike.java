package com.wf.gemrender.client;

//? if <26.1 {
import com.wf.gemrender.GemRender;
import com.wf.gemrender.mixin.ui.FontAccessor;
import com.wf.gemrender.ui.Canvas;
import com.wf.gemrender.ui.CanvasTarget;
import com.wf.gemrender.ui.SharpText;
import com.wf.gemrender.ui.SharpTextScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

public final class CanvasSpike {
	private static final boolean ENABLED = Boolean.getBoolean("gemrender.canvasspike");

	private static int phase;
	private static int wait;
	private static int generationBefore;
	private static boolean sharpTypeSwapped;
	private static boolean screenSharpActive;
	private static String verdict = "";

	private CanvasSpike() {
	}

	public static void tick() {
		if (!ENABLED) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getOverlay() != null) {
			wait = 0;
			return;
		}
		switch (phase) {
			case 0 -> {
				if (mc.screen instanceof TitleScreen && ++wait > 20) {
					mc.setScreen(new SpikeScreen());
					phase = 1;
					wait = 0;
				}
			}
			case 1 -> {
				if (!verdict.isEmpty()) {
					generationBefore = readGeneration();
					GemRender.LOGGER.info("canvasSpike {}", verdict);
					mc.reloadResourcePacks();
					phase = 2;
					wait = 0;
				}
			}
			case 2 -> {
				if (++wait > 40) {
					int generationAfter = readGeneration();
					boolean programDropped = readProgram() == 0;
					GemRender.LOGGER.info("canvasSpike reload fontGeneration={}->{} fontSetMixin={} canvasReloadHook={}",
							generationBefore, generationAfter, generationAfter > generationBefore ? "ok" : "FAIL",
							programDropped ? "ok" : "FAIL");
					phase = 3;
					mc.stop();
				}
			}
			default -> {
			}
		}
	}

	private static int readGeneration() {
		try {
			Field field = Class.forName("com.wf.gemrender.ui.CanvasFont").getDeclaredField("generation");
			field.setAccessible(true);
			return field.getInt(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int readProgram() {
		try {
			Field shared = Canvas.class.getDeclaredField("shared");
			shared.setAccessible(true);
			Object canvas = shared.get(null);
			Field glField = Canvas.class.getDeclaredField("gl");
			glField.setAccessible(true);
			Object gl = glField.get(canvas);
			Field program = gl.getClass().getDeclaredField("program");
			program.setAccessible(true);
			return program.getInt(gl);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int[] pixel(int x, int y) {
		ByteBuffer buffer = BufferUtils.createByteBuffer(4);
		glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		return new int[] {buffer.get(0) & 0xFF, buffer.get(1) & 0xFF, buffer.get(2) & 0xFF};
	}

	private static int brightCount(int x0, int y0, int w, int h) {
		ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
		glReadPixels(x0, y0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		int count = 0;
		for (int i = 0; i < w * h; i++) {
			int r = buffer.get(i * 4) & 0xFF;
			int g = buffer.get(i * 4 + 1) & 0xFF;
			int b = buffer.get(i * 4 + 2) & 0xFF;
			if (r > 160 && g > 160 && b > 160) {
				count++;
			}
		}
		return count;
	}

	private static final class SpikeScreen extends Screen implements SharpTextScreen {
		private int frames;

		SpikeScreen() {
			super(Component.literal("canvas spike"));
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			frames++;
			if (frames < 5 || !verdict.isEmpty()) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			Canvas canvas = Canvas.shared();
			canvas.begin(graphics);
			canvas.fill(20, 20, 120, 80, 0xFFFF0000);
			canvas.text("Canvas Text", 20, 100, 0xFFFFFFFF, false);
			canvas.end();

			Font font = mc.font;
			screenSharpActive = SharpText.active();
			var set = ((FontAccessor) font).gemrender$fontSet(Style.DEFAULT_FONT);
			SharpText.begin();
			String type = set.getGlyph('A').renderType(Font.DisplayMode.NORMAL).toString();
			SharpText.end();
			sharpTypeSwapped = type.contains("gemrender_sharp_text");
			graphics.drawString(font, "Sharp Text", 20, 140, 0xFFFFFFFF, false);
			graphics.flush();

			double scale = mc.getWindow().getGuiScale();
			int height = mc.getMainRenderTarget().height;
			int previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
			glBindFramebuffer(GL_READ_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
			int[] rect = pixel((int) (70 * scale), height - 1 - (int) (50 * scale));
			boolean rectOk = rect[0] > 240 && rect[1] < 15 && rect[2] < 15;
			int textPx = brightCount((int) (20 * scale), height - (int) (112 * scale), (int) (90 * scale), (int) (12 * scale));
			int sharpPx = brightCount((int) (20 * scale), height - (int) (152 * scale), (int) (80 * scale), (int) (12 * scale));
			glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);

			CanvasTarget target = new CanvasTarget(32, 32);
			canvas.begin(target, 32, 32, 0xFF000000);
			canvas.fill(0, 0, 32, 32, 0xFF00FF00);
			canvas.end();
			ByteBuffer targetPixel = BufferUtils.createByteBuffer(32 * 32 * 4);
			int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
			glBindTexture(GL_TEXTURE_2D, target.texture());
			glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, targetPixel);
			glBindTexture(GL_TEXTURE_2D, previousTexture);
			int offset = (16 * 32 + 16) * 4;
			boolean targetOk = (targetPixel.get(offset) & 0xFF) < 15 && (targetPixel.get(offset + 1) & 0xFF) > 240;
			target.close();

			verdict = "rect=" + (rectOk ? "ok" : "FAIL " + rect[0] + "," + rect[1] + "," + rect[2])
					+ " text=" + (textPx > 20 ? "ok" : "FAIL") + "(" + textPx + ")"
					+ " vanillaSharpText=" + (sharpPx > 20 ? "ok" : "FAIL") + "(" + sharpPx + ")"
					+ " target=" + (targetOk ? "ok" : "FAIL")
					+ " screenMixin=" + (screenSharpActive ? "ok" : "FAIL")
					+ " bakedGlyphMixin=" + (sharpTypeSwapped ? "ok" : "FAIL(" + type + ")");
		}
	}
}
//?} else {
/*import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.Vanilla;
import com.wf.gemrender.ui.Canvas;
import com.wf.gemrender.ui.CanvasTarget;
import com.wf.gemrender.ui.SharpTextScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

public final class CanvasSpike {
	private static final boolean ENABLED = Boolean.getBoolean("gemrender.canvasspike");

	private static int phase;
	private static int wait;
	private static int generationBefore;
	private static boolean drawing = true;

	private CanvasSpike() {
	}

	public static void tick() {
		if (!ENABLED) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getOverlay() != null) {
			wait = 0;
			return;
		}
		switch (phase) {
			case 0 -> {
				if (mc.screen instanceof TitleScreen && ++wait > 20) {
					mc.setScreen(new SpikeScreen());
					phase = 1;
					wait = 0;
				}
			}
			case 1 -> {
				if (++wait > 30) {
					GemRender.LOGGER.info("canvasSpike {}", read(mc));
					generationBefore = readGeneration();
					drawing = false;
					mc.reloadResourcePacks();
					phase = 2;
					wait = 0;
				}
			}
			case 2 -> {
				if (++wait > 60) {
					int generationAfter = readGeneration();
					GemRender.LOGGER.info("canvasSpike reload fontGeneration={}->{} fontSetMixin={} canvasReloadHook={}",
							generationBefore, generationAfter, generationAfter > generationBefore ? "ok" : "FAIL",
							readProgram() == 0 ? "ok" : "FAIL");
					phase = 3;
					mc.stop();
				}
			}
			default -> {
			}
		}
	}

	private static String read(Minecraft mc) {
		double scale = mc.getWindow().getGuiScale();
		int height = mc.getMainRenderTarget().height;
		int previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, Vanilla.fboOf(mc.getMainRenderTarget()));
		int[] rect = pixel((int) (70 * scale), height - 1 - (int) (50 * scale));
		boolean rectOk = rect[0] > 240 && rect[1] < 15 && rect[2] < 15;
		int textPx = brightCount((int) (20 * scale), height - (int) (112 * scale), (int) (90 * scale), (int) (12 * scale));
		int sharpPx = brightCount((int) (20 * scale), height - (int) (152 * scale), (int) (80 * scale), (int) (12 * scale));
		glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);

		Canvas canvas = Canvas.shared();
		CanvasTarget target = new CanvasTarget(32, 32);
		canvas.begin(target, 32, 32, 0xFF000000);
		canvas.fill(0, 0, 32, 32, 0xFF00FF00);
		canvas.end();
		ByteBuffer targetPixel = BufferUtils.createByteBuffer(32 * 32 * 4);
		int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
		glBindTexture(GL_TEXTURE_2D, target.texture());
		glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, targetPixel);
		glBindTexture(GL_TEXTURE_2D, previousTexture);
		int offset = (16 * 32 + 16) * 4;
		boolean targetOk = (targetPixel.get(offset) & 0xFF) < 15 && (targetPixel.get(offset + 1) & 0xFF) > 240;
		target.close();
		return "rect=" + (rectOk ? "ok" : "FAIL " + rect[0] + "," + rect[1] + "," + rect[2])
				+ " text=" + (textPx > 20 ? "ok" : "FAIL") + "(" + textPx + ")"
				+ " vanillaSharpText=" + (sharpPx > 20 ? "ok" : "FAIL") + "(" + sharpPx + ")"
				+ " target=" + (targetOk ? "ok" : "FAIL")
				+ " sharpPipeline=" + (sharpPipelineBuilt() ? "ok" : "FAIL");
	}

	private static boolean sharpPipelineBuilt() {
		try {
			Field field = Class.forName("com.wf.gemrender.ui.SharpText").getDeclaredField("color");
			field.setAccessible(true);
			return field.get(null) != null;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int readGeneration() {
		try {
			Field field = Class.forName("com.wf.gemrender.ui.CanvasFont").getDeclaredField("generation");
			field.setAccessible(true);
			return field.getInt(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int readProgram() {
		try {
			Field shared = Canvas.class.getDeclaredField("shared");
			shared.setAccessible(true);
			Object canvas = shared.get(null);
			Field glField = Canvas.class.getDeclaredField("gl");
			glField.setAccessible(true);
			Object gl = glField.get(canvas);
			Field program = gl.getClass().getDeclaredField("program");
			program.setAccessible(true);
			return program.getInt(gl);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int[] pixel(int x, int y) {
		ByteBuffer buffer = BufferUtils.createByteBuffer(4);
		glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		return new int[] {buffer.get(0) & 0xFF, buffer.get(1) & 0xFF, buffer.get(2) & 0xFF};
	}

	private static int brightCount(int x0, int y0, int w, int h) {
		ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
		glReadPixels(x0, y0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		int count = 0;
		for (int i = 0; i < w * h; i++) {
			if ((buffer.get(i * 4) & 0xFF) > 160 && (buffer.get(i * 4 + 1) & 0xFF) > 160 && (buffer.get(i * 4 + 2) & 0xFF) > 160) {
				count++;
			}
		}
		return count;
	}

	private static final class SpikeScreen extends Screen implements SharpTextScreen {
		SpikeScreen() {
			super(Component.literal("canvas spike"));
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			if (!drawing) {
				return;
			}
			Canvas canvas = Canvas.shared();
			canvas.begin(graphics);
			canvas.fill(20, 20, 120, 80, 0xFFFF0000);
			canvas.text("Canvas Text", 20, 100, 0xFFFFFFFF, false);
			canvas.end();
			graphics.text(Minecraft.getInstance().font, "Sharp Text", 20, 140, 0xFFFFFFFF, false);
		}
	}
}
*///?}
