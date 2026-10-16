package com.wf.gemrender.mixin.iris;

import net.irisshaders.iris.gl.blending.BlendMode;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BlendModeOverride.class, remap = false)
public interface BlendModeOverrideAccessor {
    /** {@code null} for {@link BlendModeOverride#OFF}. */
    @Accessor("blendMode")
    BlendMode gemrender$blendMode();
}
