package dev.engine_room.flywheel.impl;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.engine_room.flywheel.api.Flywheel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Flywheel's block in the F3 overlay.
 * <p>
 * 26.1 retired {@code CustomizeGuiOverlayEvent.DebugText}, where every mod appended to one list, in
 * favour of named entries registered through {@code RegisterDebugEntriesEvent} that the player can
 * toggle individually. The lines themselves still come from {@link FlwDebugInfo}.
 */
public final class FlwDebugScreenEntry implements DebugScreenEntry {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Flywheel.ID, "debug_info");

	@Override
	public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
		List<String> lines = new ArrayList<>();
		FlwDebugInfo.addDebugInfo(Minecraft.getInstance(), lines);

		for (String line : lines) {
			displayer.addLine(line);
		}
	}
}
