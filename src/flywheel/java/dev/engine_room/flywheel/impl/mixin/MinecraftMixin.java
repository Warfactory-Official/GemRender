package dev.engine_room.flywheel.impl.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.engine_room.flywheel.impl.FlwImpl;
import net.minecraft.client.Minecraft;

/**
 * Freezes Flywheel's registries at the moment the initial resource reload starts.
 * <p>
 * Upstream also injects into two synthetic lambdas in this class -- {@code lambda$new$8} and
 * {@code lambda$reloadResourcePacks$21} -- to fire {@code EndClientResourceReloadEvent} when a reload
 * finishes. Those indices are a function of how many lambdas the compiler happened to emit before
 * them, so they are wrong on 26.1 (the equivalents are {@code lambda$new$4} and
 * {@code lambda$reloadResourcePacks$0}) and would be wrong again on the next Minecraft build that
 * adds a lambda earlier in the file. NeoForge fires {@code ClientResourceLoadFinishedEvent} at both
 * of those points and tells the listener which one it is, so the event is raised from
 * {@code FlywheelNeoForge} instead and nothing here has to guess at a lambda number.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "<init>",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;createReload("
							+ "Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;"
							+ "Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)"
							+ "Lnet/minecraft/server/packs/resources/ReloadInstance;"))
	private void flywheel$onBeginInitialResourceReload(CallbackInfo ci) {
		FlwImpl.freezeRegistries();
	}
}
