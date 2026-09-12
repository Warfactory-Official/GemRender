package dev.engine_room.flywheel.impl.compat;

import org.jetbrains.annotations.Nullable;

import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Stubbed on 26.1: Sodium has no 26.1 build, so its API is not on the classpath and this fork drops
 * the integration rather than carrying a dependency that cannot resolve. Restore upstream's version
 * when Sodium ships -- the call sites are unchanged, only {@code Internals} went. See NOTICE.md.
 */
public final class SodiumCompat {
	public static final boolean ACTIVE = false;

	private SodiumCompat() {
	}

	@Nullable
	public static <T extends BlockEntity> Object onSetBlockEntityVisualizer(BlockEntityType<T> type, @Nullable BlockEntityVisualizer<? super T> oldVisualizer, @Nullable BlockEntityVisualizer<? super T> newVisualizer, @Nullable Object predicate) {
		return null;
	}
}
