package com.wf.gemrender.particle;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.Vec3i;

public final class ParticlePool {
	private final ParticleInstance[] instances;

	public ParticlePool(VisualizationContext context, ParticleEmitter emitter,
			InstanceType<ParticleInstance> type, Model model) {
		Instancer<ParticleInstance> instancer = context.instancerProvider()
				.instancer(type, model);

		Vec3i renderOrigin = context.renderOrigin();
		Vec3i origin = emitter.origin();

		float x = origin.getX() - renderOrigin.getX();
		float y = origin.getY() - renderOrigin.getY();
		float z = origin.getZ() - renderOrigin.getZ();

		int capacity = emitter.capacity();
		int base = emitter.slotBase();

		instances = new ParticleInstance[capacity];
		for (int i = 0; i < capacity; i++) {
			ParticleInstance instance = instancer.createInstance();
			instance.origin(x, y, z)
					.particle(base + i);
			instance.setChanged();
			instances[i] = instance;
		}
	}

	public int size() {
		return instances.length;
	}

	public void delete() {
		for (ParticleInstance instance : instances) {
			if (instance != null) {
				instance.delete();
			}
		}
	}
}
