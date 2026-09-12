package com.wf.gemrender.client;

import java.util.List;

import com.wf.gemrender.GemRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;
*///?}

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class SpikeHud {
	private static final int MARGIN = 6;
	private static final int LINE_HEIGHT = 11;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int EXPECT = 0xFF9CD67A;
	private static final int SCENE = 0xFF7FC8E8;
	private static final int ALARM = 0xFFFF6B6B;
	private static final int BACKDROP = 0xC0000000;

	private static volatile List<String> lines = List.of();

	private static volatile String expected = "";

	private static volatile String progress = "";

	private static volatile String status = "";

	private static volatile String scene = "";

	private static volatile boolean statusAlarm;

	private SpikeHud() {
	}

	public static void describe(List<String> what, String whatToExpect) {
		lines = List.copyOf(what);
		expected = whatToExpect;
	}

	public static void progress(String text) {
		progress = text;
	}

	public static void status(String text, boolean alarm) {
		status = text;
		statusAlarm = alarm;
	}

	public static void scene(String text) {
		scene = text;
	}

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		List<String> what = lines;
		if (what.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		var graphics = event.getGuiGraphics();

		int width = mc.getWindow()
				.getGuiScaledWidth();
		int bottom = mc.getWindow()
				.getGuiScaledHeight();

		List<FormattedCharSequence> wrapped = expected.isEmpty() ? List.of()
				: mc.font.split(net.minecraft.network.chat.Component.literal("expect: " + expected),
						width - 2 * MARGIN);

		String live = status;
		String where = scene;
		int rows = what.size() + (live.isEmpty() ? 0 : 1) + (where.isEmpty() ? 0 : 1) + wrapped.size();
		int top = bottom - MARGIN - rows * LINE_HEIGHT;

		graphics.fill(0, top - MARGIN, width, bottom, BACKDROP);

		int y = top;
		for (int i = 0; i < what.size(); i++) {
			String line = i == 0 && !progress.isEmpty() ? progress + "  " + what.get(i) : what.get(i);
			draw(graphics, mc.font, line, MARGIN, y, TEXT);
			y += LINE_HEIGHT;
		}
		if (!where.isEmpty()) {
			draw(graphics, mc.font, where, MARGIN, y, SCENE);
			y += LINE_HEIGHT;
		}
		if (!live.isEmpty()) {
			draw(graphics, mc.font, live, MARGIN, y, statusAlarm ? ALARM : TEXT);
			y += LINE_HEIGHT;
		}
		for (FormattedCharSequence line : wrapped) {
			draw(graphics, mc.font, line, MARGIN, y, EXPECT);
			y += LINE_HEIGHT;
		}
	}

	//? if >=26.1 {
	/*private static void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Font font,
			String text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, false);
	}

	private static void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Font font,
			FormattedCharSequence text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, false);
	}
*///?} else {
	private static void draw(net.minecraft.client.gui.GuiGraphics graphics, Font font, String text,
			int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}

	private static void draw(net.minecraft.client.gui.GuiGraphics graphics, Font font,
			FormattedCharSequence text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}
	//?}

}
