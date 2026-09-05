package com.wf.gemrender.volume;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;

public class VolumeInstance extends AbstractInstance {
	public float centerX;

	public float centerY;

	public float centerZ;

	public int volume;

	public VolumeInstance(InstanceType<? extends VolumeInstance> type, InstanceHandle handle) {
		super(type, handle);
	}

	public VolumeInstance center(float x, float y, float z) {
		centerX = x;
		centerY = y;
		centerZ = z;
		return this;
	}

	public VolumeInstance volume(int volume) {
		this.volume = volume;
		return this;
	}
}
