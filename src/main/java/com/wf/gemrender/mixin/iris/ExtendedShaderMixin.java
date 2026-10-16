package com.wf.gemrender.mixin.iris;

import com.google.common.collect.ImmutableSet;
import com.wf.gemrender.iris.guest.GuestBuild;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Set;

@Mixin(value = ExtendedShader.class, remap = false)
abstract class ExtendedShaderMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/program/ProgramSamplers;builder(ILjava/util/Set;)"
                    + "Lnet/irisshaders/iris/gl/program/ProgramSamplers$Builder;"), index = 1)
    private Set<Integer> gemrender$reserveGuestUnits(Set<Integer> reserved) {
        GuestBuild build = GuestBuild.current();
        return build == null ? reserved : build.reserve(ImmutableSet.copyOf(reserved));
    }
}
