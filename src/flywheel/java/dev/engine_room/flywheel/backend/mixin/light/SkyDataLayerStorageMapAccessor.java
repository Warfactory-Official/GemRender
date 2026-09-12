package dev.engine_room.flywheel.backend.mixin.light;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

// The nested class is spelled with a $, not a dot. Upstream ships the dotted form -- it is in the
// published 1.21.1 artifact too -- and Mixin turns every dot into a slash, so the target resolves to
// the directory-like name .../SkyLightSectionStorage/SkyDataLayerStorageMap, which no class has. The
// result is a WARN at startup and a mixin that never applies, and then a ClassCastException the
// first time SkyLightSectionStorageMixin#flywheel$skyDataLayer casts visibleSectionData to this
// interface. Both fields still exist on 26.1 under the same names.
@Mixin(targets = "net.minecraft.world.level.lighting.SkyLightSectionStorage$SkyDataLayerStorageMap")
public interface SkyDataLayerStorageMapAccessor {
	@Accessor("currentLowestY")
	int flywheel$currentLowestY();

	@Accessor("topSections")
	Long2IntOpenHashMap flywheel$topSections();
}
