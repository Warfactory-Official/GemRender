package dev.engine_room.flywheel.impl.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * 26.1 moved entity culling off {@code Entity} -- {@code noCulling} and {@code getBoundingBoxForCulling}
 * now live on the entity's renderer, where a renderer can widen the box for a model bigger than the
 * hitbox. Both are protected, so Flywheel's own visibility test needs an invoker to reach them.
 */
@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor<T extends Entity> {
	@Invoker("getBoundingBoxForCulling")
	AABB flywheel$getBoundingBoxForCulling(T entity);

	@Invoker("affectedByCulling")
	boolean flywheel$affectedByCulling(T entity);
}
