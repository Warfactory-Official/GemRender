# Vendored: Engine-Room Flywheel

This source set is a vendored fork of **Flywheel** by Jozufozu — https://github.com/Jozufozu/Flywheel —
taken from the `1.21.1/dev` branch and ported to Minecraft 26.1. It compiles, it runs, and GemRender
draws through it on 26.1.

**It is a later revision than the published 1.0.6 artifact** that 1.20.1 and 1.21.1 resolve from maven.
The difference that matters to GemRender: `IndirectCullingGroup#submitTransparent` here is
`#submitOrderIndependent`, beside a new plain `#submitTranslucent`. Two of GemRender's mixins branch on
that — `IndirectDrawManagerMixin` (a call, so javac catches it) and `IndirectCullingGroupMixin` (an
`@Inject` naming it by string, which javac cannot see and which would have failed at game load). The
version this reports to FML is `flywheel_vendored_version` in `versions/26.1/gradle.properties`, set to
`1.0.7-gemrender` so it is never mistaken for an upstream release.

It is here because Flywheel has no 26.1 release: `maven.createmod.net` publishes only 1.20.1 and 1.21.1
artifacts, and upstream's `26.1.2/dev` branch is an unstarted placeholder. GemRender is a Flywheel
consumer to its core, so there is no 26.1 build of this mod without one.

**It is built only on versions where `flywheel_vendored=true`.** On 1.20.1 and 1.21.1 this source set
is emptied by `build.gradle` and Flywheel is resolved from maven and jar-in-jar'd as normal. The fork
deliberately keeps the original `dev.engine_room.flywheel` package, so GemRender's own code imports the
same types on every version.

When upstream ships 26.1: set `flywheel_vendored=false` in `versions/26.1/gradle.properties`, fill in
`flywheel_artifact` and `flywheel_version`, and delete this directory. No GemRender source changes,
because the package never changed.

## Divergence from upstream

Recorded so the fork can be re-based rather than re-derived. See
`gemrender-internal/docs/MULTIVERSION.md` for the full port status.

- **Removed** the vanilla baked-model interop — `lib/model/baked/` (18 files), `lib/model/Models.java`,
  `lib/internal/FlwLibXplat.java`, `impl/FlwLibXplatImpl.java`, `impl/mixin/neoforge/ModelBlockRendererMixin.java`
  and its entry in `flywheel.impl.neoforge.mixins.json`. GemRender has no references to it and 26.1
  rewrote that API wholesale.
- **Removed** upstream's `vanillin` module, tests and test mod — never vendored.
- **Removed** `META-INF/neoforge.mods.toml`, `pack.mcmeta` and `logo.png`: the engine ships inside
  GemRender's jar, so GemRender's metadata declares it and its three mixin configs. Note the entrypoint
  **keeps** `@Mod("flywheel")` — FML refuses to load a jar whose annotation scan finds an entrypoint for
  a mod id the jar does not declare, and the constructor reads its own version and registers a config
  spec against its `ModContainer`. So GemRender's `neoforge.mods.toml` carries a second `[[mods]]` block
  for `flywheel` wherever the engine is vendored. The jar therefore *provides* Flywheel rather than
  bundling it: a real Flywheel in `mods/` would be a duplicate mod id and a hard load failure.
- **Renamed for 26.1** (each verified against the 26.1 jar, not assumed): `ResourceLocation` →
  `net.minecraft.resources.Identifier`; `ResourceLocationException` → `net.minecraft.IdentifierException`;
  `Identifier.isAllowedInResourceLocation` → `isAllowedInIdentifier`; `GlStateManager` from
  `com.mojang.blaze3d.platform` to `com.mojang.blaze3d.opengl`; `FastColor.ARGB32.*` →
  `net.minecraft.util.ARGB.*`; `net.minecraft.Util` → `net.minecraft.util.Util`; dropped NeoForge's
  removed `MethodsReturnNonnullByDefault` / `FieldsAreNonnullByDefault`.

- **Rewritten for 26.1**, where a rename was not enough:
  - `backend/engine/TextureBinder` — 26.1 split sampling out of the texture object into standalone GL
    sampler objects. A texture bound without a matching `glBindSampler` falls back to GL defaults, which
    for a mipmapped atlas is an incomplete texture that samples **black with no GL error**. Nothing in
    this fork may bind a texture without also binding a sampler.
  - `backend/engine/MaterialRenderState`, `backend/engine/indirect/OitFramebuffer` — de-wrapped onto
    `GlStateManager._*` rather than rewritten onto `RenderPipeline`. `GlStateManager` moved package but
    kept its methods, and `GlRenderPipeline` applies vanilla's own pipelines *by calling it*, so going
    through the same methods keeps vanilla's shadow state truthful.
  - `backend/engine/uniform/LevelUniforms`, `FogUniforms` — onto the environment attribute system, the
    per-dimension `WorldClock`s, and (for fog) NeoForge's `ViewportEvent.RenderFog`.
  - `impl/event/FlwLevelRenderHooks` (new) — replaces upstream's `LevelRenderer#renderLevel` injections
    with `RenderLevelStageEvent` listeners. None of upstream's injection points survive 26.1's frame
    graph. **Measured**: the hooks fire every frame with a live context and the correct camera
    model-view, and models drawn from them appear. It also has to **bind vanilla's main render target
    and set the viewport** around the engine's draws — 26.1 binds a framebuffer per `RenderPass` and
    leaves the last one in place between passes, where 1.20.1 and 1.21.1 kept the main target bound for
    the whole of `renderLevel`. Without that bind everything in the engine reports success and nothing
    reaches the screen.
  - `backend/util/VanillaState` (new) — fork-only glue for vanilla state 26.1 stopped exposing: the far
    plane, a `GpuTexture`'s GL name, and one scratch FBO that a `RenderTarget`'s textures are attached
    to per call.
  - `impl/FlwDebugScreenEntry` (new), `impl/mixin/EntityRendererAccessor` (new) — the removed
    `CustomizeGuiOverlayEvent.DebugText` and the now-protected culling-box methods.
- **Stubbed**: `SodiumCompat`, `IrisCompat`, `EmbeddiumCompat` — none of the three has a 26.1 build.

### Mixins that compiled and would not have applied

A `@Mixin` binds to its target by annotation and string, and javac checks neither. Every mixin in this
fork was audited against 26.1's own class files after the port compiled, and four of them named
something that is not there any more. Each would have been a hard failure at game load, with nothing in
the build to warn about it.

- `backend/mixin/GlStateManagerMixin` injected into `setupLevelDiffuseLighting` to catch the two level
  light directions before vanilla transformed them into screen space. **26.1 has no such method** —
  lighting is a UBO written once per dimension by `Lighting#updateLevel`, from constants it keeps
  private. The inject is removed and `LevelUniforms` now selects the same pair from the dimension's own
  `CardinalLighting.Type`, which is the input vanilla itself switches on. No hook at all, and one less
  thing to rebase.
- `impl/mixin/PoseStackAccessor` read `PoseStack.poseStack`, a `Deque<Pose>`. **26.1 has no such
  field**: a `PoseStack` is a growing `List<Pose>` plus an index. The accessor existed for
  `lib/util/RecyclingPoseStack`, which reached into that deque to reuse `Pose` objects — and 26.1's
  `pushPose` already reuses the entry that is there rather than allocating. Accessor deleted,
  `FlwLibLink#getPoseStack` deleted, `RecyclingPoseStack` is now an empty subclass kept for its place in
  the public lib surface.
- `impl/mixin/fix/FixFabulousDepthMixin` injected at `PostChain#process(F)V` inside `renderLevel`, to
  drop the depth mask around the transparency chain under Fabulous graphics. **`process` takes a
  `RenderTarget` and a `GraphicsResourceAllocator` in 26.1** and the chain runs inside the frame graph.
  Deleted: there is no imperative depth mask to drop any more, the pass sets its own through a pipeline.
- `impl/mixin/MinecraftMixin` injected into `lambda$new$8` and `lambda$reloadResourcePacks$21` to fire
  `EndClientResourceReloadEvent`. **A lambda's number is a function of how many the compiler emitted
  before it**: 26.1's equivalents are `lambda$new$4` and `lambda$reloadResourcePacks$0`, and they would
  move again on the next Minecraft build that adds one earlier in the file. Both injects are gone;
  `FlywheelNeoForge` raises the event from NeoForge's own `ClientResourceLoadFinishedEvent`, which fires
  at the same two moments and says which one it is. The `<init>` inject that freezes the registries
  stays — it names a real method with a real descriptor.

- `backend/mixin/light/SkyDataLayerStorageMapAccessor` targeted a nested class by string and spelled
  the nesting with a **dot**: `...lighting.SkyLightSectionStorage.SkyDataLayerStorageMap`. Mixin turns
  every dot into a slash, so the target names a class that cannot exist; it warns once at startup and
  never applies, and `SkyLightSectionStorageMixin#flywheel$skyDataLayer` then casts `visibleSectionData`
  to an interface nothing added. **This one is upstream's** — the same dotted string is in the published
  1.21.1 artifact — so it is not a porting mistake, it has simply never been reached. Respelled with
  `$`; both fields exist on 26.1 under the same names, and it now applies. Note what the earlier audit
  missed here: the class it names *does* exist, so checking targets against 26.1's class files passed it.
  Only the startup log knows, and it says so at WARN, once.

**A Stonecutter note that applies to this directory:** GemRender's string replacements rewrite *every*
source set, so switching the active version away from 26.1 rewrites this fork's `Identifier` back to
`ResourceLocation` and its `blaze3d.opengl.GlStateManager` back to `blaze3d.platform`. That is correct
and stable — the fork only ever compiles on 26.1, where the replacement puts both back, and a
26.1 → 1.20.1 → 1.21.1 round trip is byte-identical over all 1011 files. **Do not "fix" them.**

The port compiles **and runs** on NeoForge 26.1.2.109: sixteen animated glTF models draw through the
instancing backend, with the atlas, the bone-matrix upload, palette sharing and LOD all working. What is
exercised is the `AfterSky` and `AfterOpaqueFeatures` path; `beforeCrumbling` and the indirect/OIT
backend have not been seen to run. See the "what is unverified" section of
`gemrender-internal/docs/MULTIVERSION.md` before trusting the rest.

## Licence

Flywheel is MIT-licensed. GemRender is GPL-3, which MIT is compatible with. The original notice, which
must be preserved in any distribution:

```
Copyright (c) 2021-2024 Jozufozu

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```
