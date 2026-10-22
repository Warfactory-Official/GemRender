package com.wf.gemrender.volume;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import org.lwjgl.system.MemoryUtil;

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
        //? if >=26.2 {
        /*long p = slabPtr();
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4, y);
        MemoryUtil.memPutFloat(p + 8, z);
        *///?}
        return this;
    }

    public VolumeInstance volume(int volume) {
        this.volume = volume;
        //? if >=26.2 {
        /*MemoryUtil.memPutInt(slabPtr() + 12, volume);
        *///?}
        return this;
    }
}
