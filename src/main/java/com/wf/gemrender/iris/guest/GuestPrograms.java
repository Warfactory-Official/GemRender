package com.wf.gemrender.iris.guest;

import com.google.common.collect.ImmutableSet;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.Ids;
import com.wf.gemrender.mixin.iris.IrisRenderingPipelineAccessor;
import com.wf.gemrender.render.TextureUnits;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Guest programs of the current Iris pipeline; a pipeline change or resource reload drops them all. */
final class GuestPrograms {
    //? if >=1.21 {
    private static final ShaderAttributeInputs INPUTS = new ShaderAttributeInputs(IrisVertexFormats.ENTITY, false,
            false, false, false, false);
    //?} else {
    /*private static final ShaderAttributeInputs INPUTS = new ShaderAttributeInputs(IrisVertexFormats.ENTITY, false,
            false, false, false);
    *///?}

    private static final Map<Key, Optional<GuestProgram>> PROGRAMS = new HashMap<>();
    private static final Map<Role, Optional<ForwardOit>> OIT = new EnumMap<>(Role.class);

    @Nullable
    private static IrisRenderingPipeline owner;
    @Nullable
    private static String vertexLibrary;
    @Nullable
    private static String oitLibrary;

    private GuestPrograms() {
    }

    private record Key(Role role, float cutoff, @Nullable OitPass pass) {
    }

    static void track(IrisRenderingPipeline pipeline) {
        if (pipeline != owner) {
            release();
            owner = pipeline;
        }
    }

    static void release() {
        PROGRAMS.values()
                .forEach(program -> program.ifPresent(GuestProgram::close));
        PROGRAMS.clear();
        OIT.clear();
        GuestOit.release();
        vertexLibrary = null;
        oitLibrary = null;
        owner = null;
    }

    /** {@code null}: the pack has no program for {@code role}, or it failed to build (logged once). */
    @Nullable
    static GuestProgram get(IrisRenderingPipeline pipeline, Role role, float cutoff, @Nullable OitPass pass) {
        return PROGRAMS.computeIfAbsent(new Key(role, cutoff, pass),
                key -> Optional.ofNullable(build(pipeline, key)))
                .orElse(null);
    }

    /** {@code null}: no OIT for {@code role} under this pack. */
    @Nullable
    static ForwardOit oit(IrisRenderingPipeline pipeline, Role role) {
        return OIT.computeIfAbsent(role, key -> {
            ProgramSource source = role.shadow ? null : resolver(pipeline).resolveNullable(role.programId);
            ForwardOit oit = source == null ? null : ForwardOit.of(source);
            if (oit != null && !GuestOit.supports(oit)) {
                GemRender.LOGGER.info("Guest OIT off for {}: {} coefficient sets exceed the texture units",
                        source.getName(), oit.sets());
                oit = null;
            }
            return Optional.ofNullable(oit);
        })
                .orElse(null);
    }

    private static net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver resolver(
            IrisRenderingPipeline pipeline) {
        return ((IrisRenderingPipelineAccessor) pipeline).gemrender$resolver();
    }

    @Nullable
    private static GuestProgram build(IrisRenderingPipeline pipeline, Key key) {
        Role role = key.role();
        ProgramSource source = resolver(pipeline).resolveNullable(role.programId);
        if (source == null) {
            GemRender.LOGGER.warn("Shaderpack has no program for {}; GemRender {} copies are not drawn under it",
                    role.programId, role.name()
                            .toLowerCase(Locale.ROOT));
            return null;
        }

        int[] drawBuffers = role.shadow && source.getDirectives()
                .hasUnknownDrawBuffers() ? new int[] {0, 1} : source.getDirectives()
                .getDrawBuffers();
        IrisRenderingPipelineAccessor accessor = (IrisRenderingPipelineAccessor) pipeline;
        GlFramebuffer before;
        GlFramebuffer after;
        Supplier<ImmutableSet<Integer>> flipped;
        if (role.shadow) {
            ShadowRenderTargets shadow = accessor.gemrender$shadowRenderTargets();
            if (shadow == null) {
                return null;
            }
            before = after = shadow.createShadowFramebuffer(ImmutableSet.of(), drawBuffers);
            flipped = pipeline::getFlippedBeforeShadow;
        } else {
            RenderTargets targets = accessor.gemrender$renderTargets();
            before = targets.createGbufferFramebuffer(pipeline.getFlippedAfterPrepare(), drawBuffers);
            after = targets.createGbufferFramebuffer(pipeline.getFlippedAfterTranslucent(), drawBuffers);
            flipped = () -> pipeline.isBeforeTranslucent ? pipeline.getFlippedAfterPrepare()
                    : pipeline.getFlippedAfterTranslucent();
        }

        String name = "gemrender_guest_" + role.name()
                .toLowerCase(Locale.ROOT) + "_" + Integer.toHexString(Float.floatToIntBits(key.cutoff()))
                + (key.pass() == null ? "" : "_" + key.pass()
                .name()
                .toLowerCase(Locale.ROOT));
        ForwardOit oit = key.pass() == null ? null : oit(pipeline, role);
        GuestBuild.begin(new GuestBuild(stages -> transform(stages, key.pass(), oit), samplers(key.pass(), oit)));
        try {
            ExtendedShader shader = ShaderCreator.create(pipeline, name, source, role.programId, before, after,
                    new AlphaTest(AlphaTestFunction.GREATER, key.cutoff()), IrisVertexFormats.ENTITY, INPUTS,
                    pipeline.getFrameUpdateNotifier(), pipeline, flipped, FogMode.PER_VERTEX, false, false,
                    role.shadow, false, pipeline.getCustomUniforms());
            GemRender.LOGGER.info("Guest program {} built over {}", name, source.getName());
            return new GuestProgram(shader, before, after);
        } catch (IOException | RuntimeException e) {
            GemRender.LOGGER.error("Could not build GemRender's guest program over {} ({}); those copies are not "
                    + "drawn under this pack", source.getName(), name, e);
            before.destroy();
            if (after != before) {
                after.destroy();
            }
            return null;
        } finally {
            GuestBuild.end();
        }
    }

    private static Map<Integer, String> samplers(@Nullable OitPass pass, @Nullable ForwardOit oit) {
        Map<Integer, String> samplers = new LinkedHashMap<>();
        samplers.put(TextureUnits.BONES, "_gemrender_bones");
        samplers.put(TextureUnits.MORPHS, "_gemrender_morphs");
        if (pass == OitPass.COEFFICIENTS || pass == OitPass.EVALUATE) {
            samplers.put(TextureUnits.GUEST_OIT_DEPTH_RANGE, "_gr_oitDepthRange");
            samplers.put(TextureUnits.GUEST_OIT_BLUE_NOISE, "_gr_oitBlueNoise");
        }
        if (pass == OitPass.EVALUATE) {
            for (int set = 0; set < oit.sets(); set++) {
                samplers.put(TextureUnits.GUEST_OIT_COEFFICIENTS + set, "_gr_oitCoefficients" + set);
            }
        }
        return samplers;
    }

    private static Map<PatchShaderType, String> transform(Map<PatchShaderType, String> patched,
                                                          @Nullable OitPass pass, @Nullable ForwardOit oit) {
        Map<PatchShaderType, String> stages = new EnumMap<>(PatchShaderType.class);
        stages.putAll(patched);
        stages.put(PatchShaderType.VERTEX, GuestTransform.vertex(patched.get(PatchShaderType.VERTEX),
                vertexLibrary()));
        if (pass != null) {
            GuestTransform.Fragment fragment = GuestTransform.demote(patched.get(PatchShaderType.FRAGMENT));
            stages.put(PatchShaderType.FRAGMENT, fragment.source() + '\n' + oitLibrary() + '\n'
                    + GuestOit.producer(pass, oit, fragment.outputs()));
        }
        return stages;
    }

    private static String vertexLibrary() {
        if (vertexLibrary == null) {
            vertexLibrary = resource("flywheel/skin_lbs.glsl") + resource("flywheel/morph.glsl")
                    + resource("shaders/guest/guest.vert");
        }
        return vertexLibrary;
    }

    static String oitLibrary() {
        if (oitLibrary == null) {
            oitLibrary = resource("shaders/guest/oit.glsl");
        }
        return oitLibrary;
    }

    private static String resource(String path) {
        ResourceLocation id = Ids.of(GemRender.MOD_ID, path);
        try (InputStream in = Minecraft.getInstance()
                .getResourceManager()
                .open(id)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8) + "\n";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
