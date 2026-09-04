package com.wf.gemrender.particle;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;

public class ParticleInstance extends AbstractInstance {
	public float originX;

	public float originY;

	public float originZ;

	public int particle;

	public ParticleInstance(InstanceType<? extends ParticleInstance> type, InstanceHandle handle) {
		super(type, handle);
	}

	public ParticleInstance origin(float x, float y, float z) {
		originX = x;
		originY = y;
		originZ = z;
		return this;
	}

	public ParticleInstance particle(int particle) {
		this.particle = particle;
		return this;
	}
}
