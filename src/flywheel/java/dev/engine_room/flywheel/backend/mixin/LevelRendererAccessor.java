package dev.engine_room.flywheel.backend.mixin;

import java.util.SortedSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.server.level.BlockDestructionProgress;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Accessor("ticks")
	int flywheel$getTicks();

	// The three below used to be reached with @Shadow from Flywheel's own LevelRenderer mixin. On 26.1
	// the render hooks are RenderLevelStageEvent listeners rather than injections, and a listener is
	// handed the LevelRenderer, so plain accessors serve instead.
	@Accessor("level")
	ClientLevel flywheel$getLevel();

	@Accessor("renderBuffers")
	RenderBuffers flywheel$getRenderBuffers();

	@Accessor("destructionProgress")
	Long2ObjectMap<SortedSet<BlockDestructionProgress>> flywheel$getDestructionProgress();
}
