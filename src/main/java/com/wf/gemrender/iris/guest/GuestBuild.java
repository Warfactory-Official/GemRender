package com.wf.gemrender.iris.guest;

import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Open while {@link GuestPrograms} runs Iris's {@code ShaderCreator.create}; the {@code mixin.iris} hooks read it.
 * {@code samplers}: unit -> uniform, outside Iris's allocator.
 */
public record GuestBuild(UnaryOperator<Map<PatchShaderType, String>> transform, Map<Integer, String> samplers) {
    @Nullable
    private static GuestBuild current;

    static void begin(GuestBuild build) {
        if (current != null) {
            throw new IllegalStateException("Nested guest program build");
        }
        current = build;
    }

    static void end() {
        current = null;
    }

    @Nullable
    public static GuestBuild current() {
        return current;
    }

    public ImmutableSet<Integer> reserve(ImmutableSet<Integer> reserved) {
        return ImmutableSet.<Integer>builder()
                .addAll(reserved)
                .addAll(samplers.keySet())
                .build();
    }

    public void addSamplers(SamplerHolder holder) {
        samplers.forEach((unit, name) -> holder.addExternalSampler(unit, name));
    }
}
