package dev.engine_room.flywheel.impl.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.FlwImplXplat;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

@Mixin(value = LevelRenderer.class, priority = 1001)
abstract class LevelRendererMixin {
	@Shadow
	@Nullable
	private ClientLevel level;

	@Inject(method = "allChanged", at = @At("RETURN"))
	private void flywheel$reload(CallbackInfo ci) {
		if (level != null) {
			FlwImplXplat.INSTANCE.dispatchReloadLevelRendererEvent(level);
		}
	}

	@Redirect(method = "extractVisibleEntities",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender("
							+ "Lnet/minecraft/world/entity/Entity;"
							+ "Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
	private boolean flywheel$decideNotToRenderEntity(EntityRenderDispatcher dispatcher, Entity entity,
			Frustum frustum, double camX, double camY, double camZ) {
		return dispatcher.shouldRender(entity, frustum, camX, camY, camZ)
				&& !flywheel$skipVanillaRender(entity);
	}

	@Redirect(method = "extractVisibleEntities",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hasIndirectPassenger("
							+ "Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean flywheel$decideNotToRenderCarrier(Entity entity, Entity passenger) {
		return entity.hasIndirectPassenger(passenger) && !flywheel$skipVanillaRender(entity);
	}

	@Unique
	private static boolean flywheel$skipVanillaRender(Entity entity) {
		return VisualizationManager.supportsVisualization(entity.level())
				&& VisualizationHelper.skipVanillaRender(entity);
	}
}
