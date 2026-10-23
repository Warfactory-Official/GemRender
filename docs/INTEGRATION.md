# Integration

How to draw a glTF asset with GemRender from another mod: registering the asset, rendering it on a
block entity, and the one thing the API cannot do.

This is the consumer-facing guide, and it is self-contained: everything you need to draw a model is
here. Where a rule below looks arbitrary, the reasoning is in the maintainers' notes
(`gemrender-internal/docs/`), which are kept outside this repository.

This `main` guide covers Minecraft 26.1 and earlier. Minecraft 26.2+ builds, OpenGL/Vulkan rendering,
and publishing live on the `vulkan` branch in the sibling `GemRender-Vulkan` worktree.

GemRender ships for six targets here, built from one source tree:

| Minecraft | Loader | Java | Flywheel |
|---|---|---|---|
| 1.21.1 | NeoForge 21.1.248 | 21 | 1.0.6, inside the jar |
| 1.21.1 | Fabric Loader 0.19.3, Fabric API 0.116.15 | 21 | 1.0.6, inside the jar |
| 1.20.1 | MinecraftForge 47.4.23 | 17 | 1.0.6-281, inside the jar |
| 1.20.1 | Fabric Loader 0.19.3, Fabric API 0.92.2 | 17 | 1.0.6-281, inside the jar |
| 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.0.7 fork, provided by the jar |
| 26.1.2 | Fabric Loader 0.19.5, Fabric API 0.155.3 | 25 | 1.0.7 fork, provided by the jar |

A Flywheel in `mods/` displaces the bundled copy; on 26.1 it is a duplicate mod id and fails to load. Everything here is client side.

**Same public API on every target** except two seams, both vanilla's: how an item claims a renderer and
how armour does (26.1). Checked by signature diff of the NeoForge/Forge jars; Fabric jars not yet diffed.
Seams in [section 6](#6-items-armour-and-the-hand); complete list in [Version differences](#version-differences).

---

## The one idea

The normal way to animate a model is to produce a deformed mesh per copy per frame, which is exactly
what destroys instancing. GemRender instead uploads a **bone palette** once per frame and gives each
instance an *offset into it*. The vertex shader does the skinning.

Two consequences shape the whole API:

- **A whole asset is one Flywheel `Model`.** However many primitives, materials and nodes it has, a
  hundred copies share one instancer and each copy is a single instance. Parts move relative to each
  other through the palette, not through separate draws.
- **The vertex format is full.** Flywheel has one fixed mesh format with no spare attribute, so
  skinning data is smuggled through `color`, `light` and `overlay`. That is invisible while you use
  the API as intended and lethal the moment you read a mesh yourself. See
  [the vertex format trap](#the-vertex-format-trap).

Sections 1 to 3 are that shape: register an asset, then draw it on a block entity or on an entity.
There is a second path for the shape this one is bad at – a handful of vehicles whose parts each answer
to something different, rather than a crowd sharing a clock;
[section 4](#4-vehicles-and-animation-driven-by-something-other-than-time) is the vehicle.

Both assume the asset arrives with a skeleton in it. [Section 5](#5-rigging-a-model-that-has-none) is
for when it does not: loose part meshes and an animation written in code, joined into a rig here.

---

## 1. Register a model

There is no registry to get into and no builder to call. Declaring a handle in a static field *is*
the registration.

### Asset location

Assets are read straight from the resource manager with no implicit prefix, so the
`ResourceLocation` is the full path under `assets/`. Both `.gltf` and `.glb` work.

```
src/main/resources/assets/mymod/models/drill/drill.glb
                          ^ namespace     ^ becomes mymod:models/drill/drill.glb
```

### Declaring the handle

`GemRenderModels.handle(id)` is cheap, idempotent and lazy. It does not load anything. What it does
is mark the asset as *wanted*, which is what gets it re-imported after every resource reload. Hold it
in a `static final` so that a reload brings your model back without waiting for something to ask for
it again.

```java
public final class MyModels {
    public static final ResourceLocation DRILL =
            ResourceLocation.fromNamespaceAndPath("mymod", "models/drill/drill.glb");

    // Declaring the handle is the registration. Held in a static final so a resource
    // reload re-imports it, rather than waiting for something to ask for it again.
    public static final ModelCache.Handle<GemRenderGltfModel> DRILL_MODEL =
            GemRenderModels.handle(DRILL);

    private MyModels() {
    }
}
```

Read the model with `handle.get()`. `GemRenderModels.get(id)` is the same thing keyed by id.

**`get()` never blocks, and returns `null` until the model is there.** The first call starts the
import on a loader thread and answers `null`; a later frame answers the model. Importing is not
cheap — a model with a stitched atlas takes a few hundred milliseconds the first time it is ever
seen — and the alternative is a render thread stopped for that long at whatever moment a player first
looks at your block.

So handle `null` by drawing nothing and asking again next frame. Do not cache the answer, do not
treat it as a failure, and do not block on it:

```java
GemRenderGltfModel model = MyModels.DRILL_MODEL.get();
if (model == null) {
    return;          // not loaded yet, or broken. Either way, not this frame.
}
```

Calling `get()` every frame is the intended usage: the import is started once, further calls join it
rather than starting another, and a failure is remembered so a broken file is not re-parsed each
frame. `handle.isLoading()` distinguishes "still importing" from "broken" if you want to say so in a
tooltip; `handle.getBlocking()` waits, and is for a command or a test, never for a frame.

### Hidden nodes

glTF node named `hit_*` or `socket_*` (case-insensitive), or with `extras.gemrenderHidden: true`: subtree never drawn; still in the node table. For consumer-side data (hit volumes, attach markers, colliders) shipped inside the model.

### Bedrock geometry and its texture

A `.geo.json` names no texture — the format has none — so the importer looks for a `.png` beside it,
or a `gemrender:texture` in the geometry's description. When neither is right, because the same hull
is worn with several skins and the texture is decided elsewhere, hand it in:

```java
GemRenderModels.built(
        ResourceLocation.fromNamespaceAndPath("mymod", "hull/" + skin.getPath()),
        id -> BedrockImporter.load(GEOMETRY, skin));
```

The texture is baked into the material at import, so **one geometry with two textures is two models**.
That is what the id is for: build it from both, or the second skin quietly gets the first one's model.

When the skins are known up front, declare them together instead and get one model back – see below.

### Variants: one model, several skins

A mob's colour, a vehicle's livery, a team's paint, a gun's camo: same geometry, same rig, same clips,
different pixels. Declared together, they are stitched into one sheet at import and **all of them draw
in one batch** – what separates two copies is two floats on the instance.

```java
private static final ResourceLocation SKINS =
        ResourceLocation.fromNamespaceAndPath("mymod", "variants/drone");

public static final ModelCache.Handle<GemRenderGltfModel> DRONE = GemRenderModels.variants(
        SKINS, ResourceLocation.fromNamespaceAndPath("mymod", "models/drone.gltf"),
        List.of(Map.of(),                          // 0: as the file describes it
                Map.of(HULL, HULL_DESERT),         // 1
                Map.of(HULL, HULL_WINTER)));       // 2
```

A variant is a **texture substitution**, keyed on the location the asset itself names, so one entry
reskins every material that shares that texture and a material a variant does not mention keeps the one
it had. Variant 0 is the base, and is normally an empty map.

For Bedrock geometry, where there is only ever one texture to substitute, name the skins directly:

```java
GemRenderModels.skins(SKINS, GEOMETRY, List.of(PLAIN, DESERT, WINTER));
```

Put a variant on an instance with the offset the model hands you:

```java
instance.variant(gltf.variant(drone.liveryIndex()));
```

`gltf.variantCount()` is how many there are, and an index past the end is clamped rather than thrown –
a variant is content, the count changes when a pack is swapped, and a drone briefly wearing skin 0 is a
better failure inside a visual than an exception. The item and armour paths take the same value:
`ItemAppearance.variant(stack, context)` and `ArmorAppearance.variant(entity, stack, slot)`, asked per
stack, so a gun can wear one camo in the hand and another in the inventory.

**What it costs.** One addition in the vertex shader and 8 bytes on the instance. No second sampler, no
second material, no second draw: nine copies of a model in three skins is one draw and one palette.

**The ceiling is the sheet.** Every variant is a full copy of the model's textures, and the packer
declines rather than exceeding 4096 pixels – it logs what did not fit and the model imports unatlased.
A 64x64 texture leaves room for dozens; a model whose sheet is already 1028x3084 has room for three. A
PBR sheet is three times as tall before any of this, so variants tile across it rather than down.

**Every variant's textures have to be the same size as variant 0's**, because they share one packed
layout. A skin of another size throws at import with both sizes named. That is deliberate: dropping it
quietly would ship a model wearing the wrong skin, and that is a content bug which never announces
itself.

### Per-joint variants

One skin per bone instead of per copy (damage, per-part paint), still one draw:

```java
float[] uv = new float[gltf.jointCount() * 2];           // (u, v) per joint = gltf.variant(k) of choice
instance.jointVariants(BoneBuffer.getInstance().addFloatBlock(uv, uv.length)); // every frame, like the palette
instance.jointVariants(GemRenderInstance.NO_JOINT_VARIANTS);                    // back to uvOffset
```

- A vertex takes its **dominant** joint's offset (largest weight); a blended seam snaps, it does not fade.
- Joint index == node slot (`layout().nodeTable()`), so a subtree is `parentSlots()` walked up.
- Set, it replaces `uvOffset` for that instance.

### Paint

Pattern layer shared by every model: memory = paints + models, painted copies stay one batch.

```java
Paint woodland = PaintArray.texture(id("textures/paint/woodland.png"), 6.0f); // tiling, blocks per repeat
Paint olive = PaintArray.solid(0x4B5320);
Matrix4f[] rest = gltf.restPalette();                                          // once per model
if (gltf.paintable() && !ShaderPacks.inUse()) {                               // every frame:
    instance.paint(woodland, gltf.paintReference(), BoneBuffer.getInstance().addSharedPalette(rest, gltf.jointCount()));
}                                                                              // Paint.NONE = authored
DirectRenderer.submit(gltf, palette, morphs, pose, light, overlay, argb, DirectPass.GUI, variant,
        woodland, gltf.paintReference(), rest);                                // outside the level (GUI preview)
```

- `addSharedPalette`: same array (identity) => one staging per frame for every instance. One cached `rest` per model, not per instance.
- Mask: `<base>_paint.png` beside a base colour. Alpha = coverage; rgb = colour the base was painted in.
- Masked texel => `base * paint / reference`, `reference` = coverage-weighted mean mask rgb, one per model.
- Pattern: triplanar at the vertex's rest-pose model position (rest palette x pre-skin vertex) => pinned to its part. Posed position FORBIDDEN: a turning turret swims through the pattern. Face normal from derivatives.
- Variant base `X` => mask `X_paint` if present, else the model's.
- `<mask>_solid.png` (optional): red >= 128 => paint's mean colour (top mip) instead of its pattern, shading kept.
- Atlas byte: emissive alpha = `120c` solid, `136 + 119c` pattern; nearest-sampled, 121..135 gap absorbs BC7 error.
- `paintable()` false (no mask, or a mesh off the banded sheet) => painting draws garbage.
- Shader pack => fragment stage dropped => painted vertex outputs reach the pack raw. Skip paint there.
- Painted => instance tint rgb ignored (overlay carries the reference); alpha kept.
- `PaintArray.clear()` on reload invalidates every `Paint`. Layers `-Dgemrender.paintsize` (512)^2, unit 19.

### Reloads and failures

**Hold the handle, not the model.** A `GemRenderGltfModel` owns a stitched atlas texture and a range
of the shared morph buffer, both freed on reload. The handle is stable across reloads and what is
behind it is replaced, so a stored handle can never hand you a disposed model. A stored
`GemRenderGltfModel` can.

An import that throws does not propagate: you get `null`, the failure is cached so a broken file is
not re-parsed every frame, and the reason is logged once. A reload retries everything that failed,
because that is the moment the answer can have changed.

**Imports run on a small pool of loader threads, and nothing on them touches the GPU.** Parsing,
material baking, atlas stitching and mesh building are arithmetic; the texture registrations route
themselves through `RenderSystem.recordRenderCall`, and the vertex upload happens on the render
thread on the model's first draw. A `Builder` passed to `GemRenderModels.built` runs there too, so it
must build geometry and nothing else — no GL, no render state, no `Minecraft` field that is only safe
on the render thread.

A reload re-imports everything wanted in parallel and does not wait: the reload finishes, and the
models arrive over the next few frames. Nothing needs handling for that beyond the `null` above.

### The block cache

Compressing a stitched atlas to BC7 is the single most expensive thing an import does — 153 ms for a
1028x3084 sheet — and it is perfectly reproducible, so the blocks are kept in `.gemrender/blocks`
under the instance directory and the encode only ever happens on the launch that first sees a given
sheet. The same atlas costs **16 ms** on every launch after that.

Entries are named by a hash of the pixels, so nothing has to be invalidated: a pack that changes a
texture stitches a different sheet and asks a different question. The directory is capped at 512 MB
and evicts least-recently-used; `-Dgemrender.blockcache=<MB>` changes the cap, and `0` switches the
cache off. Deleting the directory is always safe.

---

## 2. Draw it on a block entity

Not a vanilla `BlockEntityRenderer`. You register a Flywheel `BlockEntityVisualizer`, which builds a
*visual* once and then updates instance data per frame instead of re-submitting geometry.

### Registering the visualizer

`SimpleBlockEntityVisualizer.builder(...).apply()` calls `VisualizerRegistry.setVisualizer` for you,
so do not call it a second time yourself. `skipVanillaRender` is how you stop the vanilla renderer
drawing the same block on top.

```java
@EventBusSubscriber(modid = "mymod", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MyVisualizers {
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer
                .builder(MyBlockEntities.DRILL.get())
                .factory(DrillVisual::new)
                // The visual draws the whole machine, so the vanilla renderer must not.
                .skipVanillaRender(be -> true)
                .apply());
    }
}
```

### The visual

Extend `AbstractBlockEntityVisual<T>` and implement `SimpleDynamicVisual`. The base class gives you
`pos` (world), `visualPos` (already `pos.subtract(renderOrigin)`, which is the one you want for the
instance transform), `blockState` and `relight(...)`.

```java
public class DrillVisual extends AbstractBlockEntityVisual<DrillBlockEntity>
        implements SimpleDynamicVisual {

    private GemRenderGltfModel gltf;
    private AnimationPhase phase;
    private GemRenderInstance instance;

    public DrillVisual(VisualizationContext ctx, DrillBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);

        // Nothing else: the model may still be importing. See below.
    }

    /** Takes an instance once the model is in. False while it is not. */
    private boolean acquire() {
        gltf = MyModels.DRILL_MODEL.get();
        if (gltf == null) {
            return false;
        }

        // Seeded off the block position, so this machine is at the same point in its
        // cycle after a restart, on another client, and after the chunk reloads.
        phase = AnimationPhase.scattered(gltf.animationOrAny("run"), pos.asLong());

        instance = instancerProvider()
                .instancer(GemRenderInstanceTypes.SKINNED, gltf.model())
                .createInstance();

        instance.pose.translation(visualPos.getX(), visualPos.getY(), visualPos.getZ());
        instance.colorArgb(0xFFFFFFFF);
        // Per instance, because the per-vertex light attribute carries joint indices.
        relight(instance);
        instance.setChanged();
        return true;
    }

    @Override
    public void beginFrame(Context ctx) {
        if (instance == null && !acquire()) {
            return;
        }

        float seconds = (level.getGameTime() + ctx.partialTick()) / 20.0f;

        // One lookup per instance, one evaluation per distinct instant across the whole
        // frame: machines at the same point in the same clip share a palette.
        PoseCache.Pose pose = PoseCache.getInstance()
                .pose(gltf.layout(), gltf.bounds(), gltf.morphs(),
                        phase.clip(), phase.timeAt(seconds));

        if (instance.boneBase != pose.boneBase()
                || instance.morphBase != pose.morphBase()
                || !instance.boneSphere.equals(pose.sphere())) {
            instance.boneBase = pose.boneBase();
            instance.morphBase = pose.morphBase();
            instance.boneSphere.set(pose.sphere());
            // A machine at rest resolves to the same shared pose every frame and so
            // never re-uploads. Keep that property: only setChanged on a real change.
            instance.setChanged();
        }
    }

    @Override
    public void updateLight(float partialTick) {
        if (instance != null) {
            relight(instance);
        }
    }

    @Override
    protected void _delete() {
        if (instance != null) {
            instance.delete();
        }
    }
}
```

**Do not resolve the model in the constructor.** Imports run on loader threads, so a visual built while
its asset is still importing gets `null` – and a visual that gave up there draws nothing for the rest of
its life, because nothing rebuilds it until the next resource reload. Asking the handle each frame until
it answers is a field read and a generation compare once the model is in. `GemRenderEntityVisual` in the
next section does this for you.

**Flywheel never culls your visual for you.** `AbstractBlockEntityVisual.isVisible(frustum)` and
`doDistanceLimitThisFrame(ctx)` look like framework hooks and are not — nothing in Flywheel calls
either, as its own javadoc says ("You may optionally do this check"). They are helpers your
`beginFrame` has to apply, so a visual that *overrides* `isVisible` and never calls it has written dead
code, and one that never mentions it updates every model in the level every frame, behind you included.

```java
@Override
public void beginFrame(Context ctx) {
    if (!isVisible(ctx.frustum()) || doDistanceLimitThisFrame(ctx)) {
        return;
    }
    ...
}
```

And override `isVisible` whenever the model is bigger than the block: the default is a sphere around
**one block**, so a machine that reaches past its controller stops updating — freezing mid-animation
with most of it still on screen — as soon as that one block leaves the frustum. `PosedBound.test` is
that test, against the bound `PoseCache.Pose.sphere()` gave you for the pose you last drew:

```java
@Override
public boolean isVisible(FrustumIntersection frustum) {
    return super.isVisible(frustum) || PosedBound.test(frustum, instance.pose, lastSphere);
}
```

Union, not replacement — the bound is last frame's. `GemRenderEntityVisual` does all of this for you on
the entity path, where the default is the entity's hitbox and the mismatch is the same one.

**You do not manage the buffers.** Uploading the bone palette, binding it to texture unit 10, binding
the morph deltas to unit 11 and clearing the pose cache all happen once a frame before any Flywheel
draw. You never call `BoneBuffer.uploadAndBind()` or `PoseCache.endFrame()` yourself.

### The four fields

A `GemRenderInstance` has five things a consumer sets, and only four of them matter for a model with one
skin. Three fail loudly, one fails quietly – which is why it is worth naming – and one has a default
that is simply correct.

| Field | What it is | If you forget it |
|---|---|---|
| `pose` | Model to world transform, applied after skinning. Use `visualPos`, not `pos` | The model renders at the render origin, typically thousands of blocks away |
| `light` | Packed lightmap, per instance. Set it with `relight(instance)` | The model is black. Per-vertex light is unavailable, it carries joint indices |
| `boneBase`, `morphBase` | Offsets into the shared buffers, from `PoseCache.Pose`. Never compute these yourself | Every copy shows another machine's pose, or the rest pose forever |
| `boneSphere` | Bounding sphere of the *posed* model, also from `PoseCache.Pose` | **Geometry vanishes near the edge of the screen.** The default is a deliberately small one-block sphere, so the mistake is visible rather than silently disabling culling forever |
| `uvOffset` | Which variant the copy wears, from `gltf.variant(i)`. Only for a model that declares any | The copy wears variant 0, which is the right default and the only value a model without variants has |

**Flywheel's own bounding sphere is unusable.** Do not fall back to `gltf.model().boundingSphere()`.
Flywheel builds it from every primitive's raw vertices heaped into one space, and a GemRender model's
primitives each live in their own space, so it does not bound the assembled model even at rest. On
the test radar it is centred twenty-two blocks away from the model.

### Animation phase

An `AnimationPhase` is a value, not a ticking field. It turns "what time is it" into "where is this
copy in its clip", so a machine holds no animation state, needs no saving or resynchronising, and
survives a chunk reload identically.

- `AnimationPhase.scattered(clip, pos.asLong())` is the one to reach for when placing machines. Every
  block gets its own deterministic place in the cycle.
- `AnimationPhase.of(clip)` runs every copy in lockstep. Cheaper, because they all share one
  evaluated palette, but they move as one.
- `AnimationPhase.REST` holds the pose the file declared.
- `.withSpeed(s)` scales playback; `0` freezes and negative runs backwards.

Do not use a random offset. It looks right on the first frame and wrong on every later one, because
the machine jumps the moment its visual is rebuilt.

### Several clips at once

One clip and one time is the common case. A thing whose parts answer to *different* quantities needs
more than one, and merging them into a single clip cannot work: two independent parameters would need
a clip per pair of values. Pass them as layers instead – same call, arrays instead of scalars:

```java
// A mob whose legs run on distance walked and whose jaws run on an attack timer.
clips[0] = walk.clip();  times[0] = walk.timeAt(entity.walkAnimation.position(partialTick));
clips[1] = bite.clip();  times[1] = bite.timeAt(entity.getAttackAnim(partialTick));
clips[2] = damageStateOrNull;  times[2] = 0.0f;

PoseCache.Pose posed = PoseCache.getInstance()
        .pose(gltf.layout(), gltf.bounds(), gltf.morphs(), clips, times, 0);
```

Layers are applied in order onto one pose, and a `null` layer sits out. Hold the two arrays on the
visual rather than allocating them per frame; a visual is single-threaded with respect to itself, so
they are safe as fields and unsafe as statics.

**Sharing is per layer, and it multiplies.** Two copies collide in the cache only when *every* layer
agrees, so a layer can only ever split the table further. That makes the cost of a layer entirely a
question of how much it varies:

- A layer that hardly ever varies is nearly free. Damage states, variants, a hatch that is open or
  shut: a handful of distinct values across a whole crowd, and clips with no duration at all – see
  `NodeHide` – fall in one time bucket, so the cache separates them by identity rather than instant.
- A layer that varies per copy costs a pose per copy, exactly as a single clip on a continuous clock
  does. Three hundred mobs mid-stride at three hundred different phases is three hundred evaluations
  whether that is one layer or four.

The counting argument in `AnimationPhase.snap` applies unchanged, once per layer.

### Blending: crossfades, masks, additive layers

Layers above overwrite in order. `AnimationBlend` weights them. Package `com.wf.gemrender.gltf.blend`.

| Type | Role |
|---|---|
| `AnimationBlend` | per-instance layer stack, refilled per frame: `override(clip, t, w[, mask])`, `additive(clip, t, w, mask, reference)`, `sync(layer, group)`. Grows once, then allocates nothing |
| `BlendMask` | per-node weight in `[0,1]`: `ALL`, `subtree(table, names...)`, `nodes(...)`, `weights(...)`, `drivenBy(table, clip)`, `complement(table)`. Immutable; build once per model |
| `AdditiveReference` | additive basis: `rest(table)`, `frame(table, clip, t)`. Build once |
| `Crossfade` | per-instance transitions: `play(clip, now, fade, curve[, clipTime, speed, loop])`, `write(now, blend[, weight, mask])`, `fading(now)` |
| `FadeCurve` | `LINEAR`, `SMOOTH` (`3f^2 - 2f^3`), `INERTIAL` |
| `BlendEvaluator` | `evaluate(table, blend, state, scratch)`: blend -> node state (external-pose path, section 4) |

Where it plugs in:

| Path | Call |
|---|---|
| Block entity | `PoseCache.pose(layout, bounds, morphs, blend, lod)` in place of the clip/time form |
| Entity | override `GemRenderEntityVisual.blend(partialTick, blend)`, fill, return `true`; `animate` still abstract: pose when `blend` returns `false` |
| Item | `ItemAppearance.blend(stack, context, partialTick, out)`, fill, return `true`; clip/seconds then unused. 26.1: called again for atlas redraw (`AnimationBlend.moves()`) => pure, no `play` |
| Own palette | `GltfPose.evaluate(layout, blend, palette, morphs, morphOut, scratch)`, or `DirectRenderer.submit(model, blend, ...)` |
| Rigid parts | `PartsPose.evaluate(model, blend, out, only, scratch)` |
| Armour | none: pose is the wearer's |

Single-clip API unchanged; a blend is opt-in per call site.

Math, per node, `e_i = weight_i * mask_i(node)`:

- Override layers: one normalized weighted average. `T, S, morph = sum(e_i x_i) / max(1, sum e_i)`; `sum e_i < 1` => rest fills the remainder; `sum e_i == 0` => rest.
- Rotation: same sum, each quaternion flipped into the running sum's hemisphere, then normalized (nlerp).
- Additive layers after, in order: `T += e (T - T_ref)`; `S *= 1 + e (S / S_ref - 1)` (`|S_ref| <= 1e-6` => skipped); `R = R * nlerp(I, conj(R_ref) R, e)` (bone-local, right side); `morph += e (w - w_ref)`. `e > 1` exaggerates.
- Crossfade weights, newest first: `w_k = remaining * curve(progress_k)`, oldest takes the rest. Interrupted fade continues from the pose on screen.
- `INERTIAL`: outgoing entries dropped at `play`; offset `source(now) - target(now)` per node (T, S vectors; R axis-angle; each morph weight) decays by a quintic (Bollo, GDC 2018) from the source velocity `(source(now) - source(now - h)) / h`, `h = 1/120 s`, projected on the offset, clamped `<= 0`. Newest entry younger than `h` => `h = -1/120 s` (forward): backward probe would predate it. One clip sampled instead of two.
- `sync(layer, group)`: followers take the heaviest layer's phase fraction over their own duration.

Layering one clip over another ("upper body replaces") = the base carries the complement mask, so the pair sums to 1 per node:

```java
BlendMask upper = BlendMask.subtree(table, "spine2");       // once
BlendMask lower = upper.complement(table);                   // once
AdditiveReference recoilBasis = AdditiveReference.frame(table, recoil, 0.0f);

blend.clear();
legs.write(now, blend, 1.0f, lower);                         // a Crossfade
blend.override(aim, aimTime, 1.0f, upper);
blend.additive(recoil, recoilTime, 1.0f, upper, recoilBasis);
PoseCache.Pose pose = PoseCache.getInstance().pose(gltf.layout(), gltf.bounds(), gltf.morphs(), blend, lod);
```

Sharing:

- Key per layer: clip, time bucket (`quantumSeconds(lod)`), weight quantized to `1 / PoseCache.weightSteps(lod)` (`max(16, 256 >> lod)`), mask, mode, reference. Evaluated from the quantized values, so sharers get the same pose.
- Layers quantizing to weight 0 drop out; one full-weight `ALL` override left => the single-clip key, shared with single-clip callers. A finished fade costs what the clip costs.
- Active `INERTIAL` offset => per-instance, never keyed: no key copy, no map entry; `Pose` recycled per thread next frame.
- Pose still claimed every frame; `PoseLod` coarsens time and weight.

Cost, palette evaluation (sample + compose), every node keyframed on T/R/S, Ryzen 9 7900X, JDK 21 (`BlendBench`, `-PblendBench=<file>`):

| nodes | single clip | 1 layer `w=0.6` | 2 layers | 2 layers split by subtree mask | 4 layers (3 override + 1 additive) |
|---:|---:|---:|---:|---:|---:|
| 50 | 3.7 us | 1.13x | 1.79x | 1.21x | 3.44x |
| 200 | 14.7 us | 1.13x | 1.81x | 1.29x | 3.57x |

- Clip sampling is 82% of a single-clip pose, compose 17%. Blend arithmetic ~0.5 us / 50 nodes; cost ~= layers x sample.
- Masked layer samples only drivers whose node has mask weight > 0 (drivers with `offset() == -1` always run).
- nlerp vs slerp, 2-way blend, poses `x` degrees apart, worst angle: 30 => 0.03 deg, 90 => 0.92 deg, 170 => ~6.6 deg; 0 at both ends. slerp not needed.

Traps:

- `Crossfade.write` prunes finished entries: `now` must not run backwards on one instance.
- Mask, `AdditiveReference` and `Crossfade` inertialization bound to their `NodeTable` (identity); evaluated on another table => `IllegalArgumentException`. `BlendMask.ALL` fits any. Clips unchecked.
- Layer times are clip-local, not wrapped (`Crossfade` wraps its own when `loop`).
- Override weight clamped to `[0,1]`; NaN/negative/infinite weight or time => `IllegalArgumentException`.

### Attaching something to a bone

A muzzle flash, a held item, a light, a particle emitter, a child model: all of them need to know where
a bone ended up this frame. The palette is already composed on the CPU, so asking costs a matrix copy.

```java
Matrix4f socket = pose.boneMatrix("muzzle", new Matrix4f());   // model space
socket.mulLocal(instance.pose);                                // relative to the render origin
Vector3f at = socket.transformPosition(new Vector3f());

double worldX = at.x + renderOrigin().getX();
```

`boneMatrix` takes a **node** slot – `NodeTable.slotOf`, `slotOfName`, or the name as above – and not a
slot out of `jointSlots`. A skin's block of the palette holds `global x inverseBind`, which is the
matrix that moves a bound vertex and is *not* where the joint is; every joint has a node slot as well,
and that is the one to ask for. Passing a skin slot throws rather than returning a plausible wrong
transform.

Two things follow from the pose being shared. The instant is the one it was **evaluated** at, which is
its time bucket's representative rather than exactly what you asked for – at most half a quantum out,
and coarser at distance by the same octave `PoseLod` coarsens the model by. And the matrices are valid
for the frame only: the cache hands them back to its pool at the end of it, so keep what you read
rather than the `Pose`.

---

## 3. Draw it on an entity

A mob, a vehicle, a projectile, a drone: everything section 2 does, on something that moves. Same one
draw per model per batch, same skinning in the vertex shader, same shared palettes, same temporal LOD.

Register a visualizer for the entity type, at client setup, on the mod bus:

```java
@SubscribeEvent
static void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> SimpleEntityVisualizer.builder(MyEntities.DRONE.get())
            .factory(DroneVisual::new)
            // The visual draws the whole entity, so the vanilla renderer must not.
            .skipVanillaRender(entity -> true)
            .apply());
}
```

Then extend `GemRenderEntityVisual<T>`, which owns the instance, the transform, the pose and the light:

```java
public class DroneVisual extends GemRenderEntityVisual<Drone> {
    private final GltfAnimation hover;

    public DroneVisual(VisualizationContext ctx, Drone drone, float partialTick) {
        super(ctx, drone, partialTick, MyModels.DRONE);   // the handle, not the model

        addComponent(new ShadowComponent(ctx, drone).radius(0.7f));

        this.hover = ...;
    }

    @Override
    protected void animate(float partialTick, GltfAnimation[] clips, float[] times) {
        clips[0] = hover;
        times[0] = (level.getGameTime() + partialTick) / 20.0f;
    }
}
```

That is the whole of the common case. `animate` writes a clip and an instant into each layer; the
arrays are `layers()` long, are reused between frames, and a layer left `null` sits out. Everything
[section 2 says about several clips at once](#several-clips-at-once) applies here unchanged.

**Where the model goes** is `transform(Matrix4f, float)`, which by default is the entity's interpolated
position and `getYRot()`. Override it for anything else – a mob whose body and head turn separately
usually wants `yBodyRot` here and a head bone driven from the difference, and a vehicle wants pitch and
roll too. The matrix is applied after skinning, so it moves the posed model as a whole.

**Shadow, fire and hitbox are Flywheel's own components** and none is added by default, because a flying
machine wants none of them. `addComponent(new ShadowComponent(...))`, `new FireComponent(...)`,
`new HitboxComponent(...)`.

Three things the base class handles that a hand-written visual gets wrong:

- **The model may not be loaded yet.** Imports run on loader threads, so an entity that comes into view
  during one gets `null` from its handle. The instance is taken on the first frame the model answers,
  not in the constructor. A visual that resolved it once would draw nothing for that entity's whole
  life.
- **Culling is on the model's bound, not the entity's box.** Flywheel tests an entity's own bounding box
  inflated a little, which is right for a model drawn at the size of the thing carrying it. A GemRender
  model is under no obligation to be entity-sized, and a two-block entity wearing a twenty-block machine
  would stop being updated as soon as its box left the frustum – which looks like a model frozen
  mid-animation with most of it still on screen.
- **Lighting is sampled through Flywheel's distance limiter**, so a crowd does not all re-read the
  lightmap on the same frame. The transform is written every frame regardless, because it has to be.

**What a crowd costs.** An entity moves, so its instance is rewritten every frame – a few dozen bytes,
and Flywheel uploads only what changed. The pose is the expensive half, and it is expensive exactly when
a crowd disagrees about the time: three hundred mobs mid-stride at three hundred different points in a
walk cycle is three hundred palette evaluations, because that is genuinely three hundred poses. That is
what `PoseLod` is for, and it is on by default. What is under your control is the instant `animate`
writes; see the counting argument in section 2.

---
## 4. Vehicles, and animation driven by something other than time

Everything above assumes the shape sections 2 and 3 are good at: many copies of one machine, all reading one
clock, so the pose cache collapses them. A vehicle is the other shape. There are a few of them, not a
few thousand, and each one's parts answer to different things – the left tread to how far that side
has travelled, the turret to where its gunner is looking. Nothing is shared between two of them,
ever, and the mechanism section 2 relies on has nothing to collapse.

So there is a second path, and on this shape it is not a little faster, it is a different cost class.
Sixty-four m1a2s with two independent layers, measured in the dev harness:

| path | distinct poses per frame | CPU | fps |
|---|---:|---:|---:|
| skinned, one palette per copy | 64 | 583 us | 1111 |
| **rigid parts** | **2** | **20 us** | 2067 |

It wins by giving up on sharing between copies and exploiting *time* instead: a layer is re-evaluated
only when its own quantised instant moves on, and only the parts that layer drives are rewritten. A
parked tank whose turret is slewing costs the turret.

**The trade, and one limit.** Each part becomes its own instance, so a 112-part tank is 112 instances
rather than one – good for tens of vehicles, wrong for thousands of machines. And the path reads
Bedrock `.geo.json` only; `GemRenderModels.partsHandle` on a `.glb` throws.

### Registering

Same rule as section 1 – declare it in a `static final` so a reload re-imports it.
`GemRenderModels.parts(id)` resolves through the same cache, with the same contract: `null` until it
has loaded. A visual built from a `null` model holds no parts and is not rebuilt when the model
arrives, so gate the visual on the model rather than building one around a `null`.

```java
public static final ModelCache.Handle<GemRenderPartsModel> TANK =
        GemRenderModels.partsHandle(
                ResourceLocation.fromNamespaceAndPath("mymod", "models/m1a2/m1a2.geo.json"));
```

### Layers

A layer is a clip plus the parts it is allowed to move. `drivenBy(clip)` reports which parts the clip
touches and `withAncestors(...)` widens that to everything above them, because moving a turret moves
the gun that hangs off it. Precompute it once – it never changes:

```java
private record Layer(GltfAnimation clip, boolean[] recompute) {
    static Layer of(GemRenderPartsModel model, String clipName) {
        GltfAnimation clip = model.animation(clipName);
        return new Layer(clip, model.withAncestors(model.drivenBy(clip)));
    }
}
```

### Driving a layer with a parameter

`AnimationPhase` answers "where is this copy in its clip" for a clock. `AnimationDrive` answers the
same question for anything else, and hands back the same clip-local seconds, so it drops into the
same slot. Two shapes cover everything a vehicle does:

- **`AnimationDrive.cyclic(clip, unitsPerCycle)`** – a quantity that accumulates without bound and
  means the same thing every cycle. Feed it the odometer directly, in whatever unit you already have:
  a wheel animated as one full turn is `cyclic(spin, 2 * PI * radius)` read off distance travelled.
  It wraps, so reversing runs it backwards and nothing has to be reset.
- **`AnimationDrive.ranged(clip, min, max)`** – a quantity that lives between two stops: a steering
  angle, an elevation, how far a hatch has opened. It scrubs the clip and holds the end frames
  outside the range. Give `max` below `min` to run it the other way. `isAtEnd(v)` is there for
  whatever drives the thing to know it has arrived.

```java
// Two treads on the odometer, a turret on its gunner. The treads share a clip and still move
// independently, because what differs is the parameter, not the animation.
private static final AnimationDrive LEFT_TREAD  = AnimationDrive.cyclic(treadClip,  TREAD_PITCH);
private static final AnimationDrive RIGHT_TREAD = AnimationDrive.cyclic(treadClip,  TREAD_PITCH);
private static final AnimationDrive TURRET      = AnimationDrive.ranged(traverse, -180.0f, 180.0f);
private static final AnimationDrive GUN         = AnimationDrive.ranged(elevate,   -10.0f,  20.0f);
```

Scrubbing a clip rather than rotating one bone is the reason to prefer this over a direct binding:
the parameter can drive any keyframed motion, so a suspension arm that also compresses, or a hatch
that rotates as it slides, costs exactly what a single-axis spin costs.

### The per-frame loop

The shape that produces the 20 us above. Bucket each layer's own instant, skip the ones that have not
moved, union the parts belonging to the ones that have, and evaluate once for the whole copy:

```java
@Override
public void beginFrame(Context ctx) {
    float quantum = PoseCache.getInstance().quantumSeconds();
    Arrays.fill(changed, false);
    boolean any = false;

    // Whatever the block entity or entity already tracks. These are read, never stored here.
    float[] parameters = { be.leftTrackMetres(), be.rightTrackMetres(), be.turretYaw(), be.gunPitch() };

    for (int layer = 0; layer < drives.length; layer++) {
        float wanted = drives[layer].timeAt(parameters[layer]);
        int bucket = Math.round(wanted / quantum);

        clips[layer] = drives[layer].clip();
        times[layer] = bucket * quantum;

        if (bucket != lastBucket[layer]) {
            lastBucket[layer] = bucket;
            any = true;
            or(changed, layers[layer].recompute());
        }
    }

    if (!any) {
        return;
    }

    PartsPose.evaluate(model, clips, times, transforms, changed, scratch);

    for (int part = 0; part < model.partCount(); part++) {
        TransformedInstance instance = instances[part];
        if (instance == null || !changed[part]) {
            continue;
        }
        composed.set(base).mul(transforms[part]);
        instance.pose.set(composed);
        instance.setChanged();
    }
}

private static void or(boolean[] into, boolean[] from) {
    for (int i = 0; i < into.length; i++) {
        into[i] |= from[i];
    }
}
```

`transforms` comes from `model.newTransforms()` and must be seeded once with the rest pose –
`PartsPose.evaluate(model, null, 0.0f, transforms, scratch)` – because parts no layer can reach keep
whatever is in it for good. `Scratch` is one per thread and not thread-safe.

Note what is absent: no elapsed time, no accumulating field, no state in the visual at all beyond
`lastBucket`, which is a cache and can be thrown away. The parameters are read from the thing that
already owns them.

### The one thing the asset has to get right

A bone the partition did not cut above is baked into its part's geometry, and writing its transform
then changes nothing – the motion vanishes silently rather than failing. Any bone a layer needs to
move must be declared in `gemrender:gameplay_bones` so the partition cuts above it. GemRender logs an
error naming the bone and the part it was baked into, which is the first thing to check when a turret
will not turn.

### When the pose is not a clip at all

Everything above scrubs a clip, because a clip is what an asset ships with. A mod that already has an
animation system – a state machine, a blend graph, a script – has something else: a transform per
bone, computed per frame, that no clip describes. Nothing about a bone palette requires a clip, so
that case is a supported one.

Write the bones into a **node state**, which is the same `float[]` a clip's drivers write into, and
compose it yourself:

```java
NodeTable table = gltf.layout().nodeTable();
float[] state = table.newScratch();          // hold this; it is per copy
table.resetToRest(state);                    // anything not written stays at rest

int slot = table.slotOfName("turret");       // -1 for a bone the model does not have
if (slot >= 0 && table.isPosable(slot)) {    // false for a node the file declared as a matrix
    table.setTranslation(state, slot, x, y, z);
    table.setRotation(state, slot, quaternion);
    table.setScale(state, slot, sx, sy, sz);
}

GltfPose.evaluate(gltf.layout(), state, palette, gltf.morphs(), morphBlock, scratch);
gltf.bounds().evaluate(palette, sphere);

instance.boneBase = BoneBuffer.getInstance().addPalette(palette, gltf.jointCount());
instance.boneSphere.set(sphere);
instance.setChanged();
```

`table.restTranslation(slot, axis)` and `restRotation(slot, out)` are there for expressing a pose as
an offset from the rest one, which is what an additive animation system produces.

**This gives up sharing, and that is the whole cost.** `PoseCache` keys on a clip and an instant, and
this has neither, so it is one palette evaluation and one upload per copy per frame — the cost class
[the table above](#3-vehicles-and-animation-driven-by-something-other-than-time) calls "skinned, one
palette per copy". For a few dozen vehicles that is nothing; for a crowd, use a clip. And the two
arrays are yours: compose on one thread and read on another and you will upload a half-written pose,
so either do both on the same thread or publish whole palettes and alternate between two of them.

---

## 5. Rigging a model that has none

Sections 1 to 3 assume the asset arrives with a skeleton in it. A lot of them do not. A mod that has
been drawing a machine for years usually has a folder of `.obj` parts and a hand-written animation:
the geometry and the motion both exist, and nothing joins them.

`RigBuilder` is that join. Declare the bones, hang the meshes on them, and what comes out is an
ordinary `GemRenderGltfModel` – one Flywheel model, one instance a copy, posed through the same bone
palette as an imported one. Nothing downstream can tell the difference.

**Every mesh binds rigidly to exactly one bone.** There is no vertex weighting here, which is what
makes it usable with formats that carry none. A part that has to deform is split into more parts, the
way a hard-surface model is built anyway.

### The order to call things in

Bones first, then `table()`, then clips and meshes. `table()` freezes the skeleton and hands back the
`NodeTable` the driver factories address slots through, so a bone declared after it is an error rather
than a bone nothing can drive.

```java
Map<String, RigGeometry> groups = WavefrontObj.load(MESH);

RigBuilder rig = new RigBuilder("crab");
int body = rig.bone("body", RigBuilder.ROOT, 0, 0, 0);
int claw = rig.bone("claw", body, 0.25f, 0.625f, 0.0625f);

NodeTable table = rig.table();
GltfAnimation wave = GltfAnimation.procedural("wave",
        NodeOscillate.about(table, claw, 1, 0, 0, rad(35), rad(20), 1.0f, 0.0f));

rig.attach(body, groups, "Body")
   .attach(claw, groups, "Claw");
GemRenderGltfModel model = rig.build(material, Map.of("wave", wave));
```

**A bone's translation is its pivot relative to its parent's pivot**, in the frame the meshes were
authored in – the same convention a Bedrock model uses. Attached geometry stays where the artist put
it: `attach` moves each mesh into its bone's frame by the inverse of that bone's rest transform, so a
rig with no clip running draws exactly the model you started with. That inverse is the inverse bind
matrix a glTF skin would have shipped; here the rig knows where every bone rests, so it is derived.

The same geometry may be attached to several bones. Six legs from one pair of meshes is the normal
case, and costs one copy of the vertices per leg.

### Where the meshes come from

`WavefrontObj.load(id)` reads a `.obj` as one `RigGeometry` per named `o`/`g` group. It flips the V
axis and fan-triangulates faces, both matching what NeoForge's own obj loader does with
`flip_v: true`, and it ignores materials – an obj's `.mtl` names a texture through a placeholder the
model json fills in, so the texture belongs in the `Material` you hand `build`.

`RigGeometry` is plain arrays, so anything can produce one: a format of your own, or geometry
generated on the spot.

### Materials

`build(Material, clips)` gives the whole rig one material. `attach(slot, geometry, material)` gives a
part its own; meshes are grouped by material and each group is one draw, so a propeller skinned
separately from its airframe costs two draws however many parts wear each.

The `build(GltfMaterial, clips)` overload is the importers' vocabulary – alpha mode and
two-sidedness rather than Flywheel shaders – and it resolves and owns the texture, decoding a
`.ktx2` and releasing it on reload. Use the `Material` overload for anything `GltfMaterial` cannot
say: a decal at depth-equal, an emissive overlay.

### Drivers: what a bone can be told to do

A clip is an ordered list of `PoseDriver`s. Keyframe channels read out of a file and the procedural
ones below are peers – nothing downstream distinguishes them.

| Driver | Motion | Driven by |
|---|---|---|
| `NodeSpin` | Turns without end at a constant rate | A clock, or `AnimationDrive.cyclic` on an accumulating phase |
| `NodeOscillate` | `base + amplitude * sin(2pi(t/period + phase))` | The limb shape: a walk cycle is one of these per joint at different phases |
| `NodeSwing` | Sweeps linearly between two stops and holds them outside | `AnimationDrive.ranged`; the clip is one unit long so the parameter maps straight on |
| `NodeHide` | Collapses a bone and its whole subtree to nothing | Nothing. Its cycle is zero, so a set of damage states separates by identity, not instant |

Two drivers on one bone give it two axes, applied in the order they were added, the same way
`matrix.rotateY(a).rotateZ(b)` composes. Put the constant part of an angle in the driver's `base`
rather than in the bone's rest rotation whenever a bone turns about more than one axis: rest rotations
compose before every driver, so `Ry(a)Rz(b)` at rest plus drivers on Y and Z gives
`Ry(a)Rz(b)Ry(dy)Rz(dz)`, which is not the `Ry(a+dy)Rz(b+dz)` the rig meant.

**Writing your own driver.** The four above are not a closed set. A motion that is not a sine and not
a sweep – a mandible that stays shut through the first half of a swing and snaps through the second –
is a record implementing `PoseDriver`, and `NodeRotation.compose` is the public helper that writes a
rotation correctly onto whatever the drivers before it left in the pose. Two rules, both from
`PoseDriver`'s own contract: be a pure function of the time argument, and have value equality. A
driver that reaches per-copy state through a field breaks sharing silently – the copy that lands in
its bucket gets somebody else's answer.

### Registering a built model

There is no file to import, so `GemRenderModels.built(id, builder)` takes the builder instead. Same
cache, same reload, same disposal; the id is a name rather than a path, and has to be stable and
unique because it is what the cache keys on. Hold the handle in a `static final`, or declare every one
of them at client setup, for the reason section 1 gives: the builder runs on the thread that first
asks, and a rig built lazily is an obj parse on a Flywheel task thread mid-frame.

```java
ModelCache.Handle<GemRenderGltfModel> handle = GemRenderModels.built(
        ResourceLocation.fromNamespaceAndPath("mymod", "rig/crab"),
        id -> buildCrab());
```

The first builder registered for an id wins, so declaring a handle from two places is a no-op rather
than a race.

### When not to use it

If the asset already has a skeleton, import it – a glTF or a `.geo.json` carries the rig, the
materials and the clips, and none of it has to be restated in code. `RigBuilder` is for the case where
restating it in code is the only option, and for that case it is the difference between a mob costing
one instance and a mob costing one per moving part.

---

## 6. Items, armour and the hand

The same models, drawn where vanilla draws items: in a hand, in an inventory, on the ground, in a
frame, on a head, and worn as armour. It is the same retained path as everywhere else -- geometry
uploaded once, skinning in the vertex shader, one instanced draw per model -- not a CPU mirror of it.

> Earlier versions of this document said this could not work. What follows is what changed.

### Why it looked structural

Three things are true and none of them is the obstacle they appeared to be:

- `VisualizationManager.get(...)` takes a `LevelAccessor`, so there is no manager, instancer or visual
  outside a level. **GemRender therefore owns the draw here instead of Flywheel** -- its own program,
  its own vertex arrays, its own instance buffers.
- The palette is bound from Flywheel's `DrawManager.render`, which runs only in the level pass. So this
  path binds its own (`BoneBuffer.direct()`), on the same texture unit, immediately before its draws.
- Instance transforms are relative to `renderOrigin()` and consumed by Flywheel's view and projection
  uniforms. Here the caller's `PoseStack` rides on the instance and the camera matrices are read at
  flush time.

What none of that touches is the part worth keeping. `skin_lbs.glsl` and `morph.glsl` are concatenated
into this path's vertex shader from the same shipped files Flywheel `#include`s, so a machine in a
hotbar is skinned by the code that skins it in the world, and neither can be quietly forked.

### Drawing an item

Two pieces, both on your mod.

**Build the renderer and give it a name.** Same call on every version:

```java
public static final ResourceLocation RIFLE = ResourceLocation.fromNamespaceAndPath("mymod", "rifle");

GemRenderItemRenderer.register(RIFLE, GemRenderItemRenderer.of(MY_MODEL.get(),
        MY_MODEL.get().animation("idle")));
```

**Then let the item claim it**, and this is one of the two places the version matters. Vanilla changed
which half of the claim is Java and which is data.

*Before 26.1*, an item model JSON inheriting `builtin/entity` is what makes a baked model report
`isCustomRenderer()` and route here at all -- without it nothing below is ever called and the item
draws as a missing model -- and the item names the renderer from client code:

```json
{ "parent": "builtin/entity", "gui_light": "side" }
```

```java
@Override
public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return GemRenderItemRenderer.get(RIFLE);
        }
    });
}
```

`gui_light: side` asks for three-dimensional item lighting rather than the flat lighting a sprite gets.
A model that looks correct in the world and flat in the inventory is this line.

*On 26.1* there is no `getCustomRenderer` and no `builtin/entity`. The item model names its renderer in
data and the item needs no client code at all:

```json
// assets/mymod/items/rifle.json
{
  "model": {
    "type": "minecraft:special",
    "base": "mymod:item/rifle_base",
    "model": { "type": "gemrender:model", "key": "mymod:rifle" }
  }
}
```

`base` is an ordinary item model, used only for the display transforms and the particle texture; the
geometry drawn is GemRender's. `key` is the name passed to `register`.

That one renderer covers **every** item context, on every version. Vanilla hands the display context to
the same hook, so in-hand, first person, the ground, the inventory, an item frame and a head are all
served by it.

### Per-context models, clips and placement

`GemRenderItemRenderer.of` is the fixed case. The general one is `ItemAppearance`, which asks your mod
four questions per copy, all of them functions of the stack **and** the `ItemDisplayContext`:

| Method | What it decides |
|---|---|
| `model` | which asset, or `null` to draw nothing |
| `clip` | which animation, or `null` for the model at rest |
| `seconds` | where in the clip this copy is |
| `transform` | how it sits, on top of vanilla's own item transform |
| `variant` | which of the model's variant skins this copy wears |
| `tint` | a colour multiplied over the model |

(Six questions, not four -- the heading is older than `variant`.)

That shape is deliberate, and it is what a real item model needs: a gun is a different mesh in the hand
than in the inventory, is held differently in first person than in third, and animates on a reload
timer rather than the world clock. `variant` is asked per (stack, context) like the rest, so the same
sheet and the same batch cover a chest full of differently painted guns -- still one draw.

`seconds` **must be a pure function of things that do not change within a frame.** Two calls for the
same stack in one frame that return different instants make the copy flicker -- and returning the same
instant is also what lets a chest of thirty-six identical items share one palette.

**`transform`'s origin is the CENTRE of the item cell, not a corner.** Vanilla's own convention is the
corner -- a vanilla item is a block model living in `[0,1]^3`, and every version translates by
`(-0.5, -0.5, -0.5)` after the display transform and before it calls a custom renderer. GemRender undoes
that, so a glTF's own origin is the anchor and one asset sits the same way on all three versions. So an
implementation that scales the model to fit within half a unit of the origin -- and moves the model's
centre of mass there if the asset did not put it there -- fills the cell and nothing more. Scaling is
not fitting: a bounding sphere that is not centred on the model's own origin still hangs out of the
cell after the most careful `scale`.

Fitting inside the cell matters on 26.1 and nowhere else. 26.1 renders each distinct item model once
into a slot of an offscreen atlas and blits it, and the slot is **scissored** -- so anything outside
the cell is clipped away there where 1.20.1 and 1.21.1 merely let it overflow. An item that means to
overflow says so in its client item JSON:

```json
{ "oversized_in_gui": true }
```

### Drawing armour

Vanilla's armour hook wants a `HumanoidModel`, so `GemRenderArmorModel` is one, and being one is what
makes it work rather than a formality. `HumanoidArmorLayer` copies the wearer's animated part
transforms onto the model before rendering it, so the six vanilla parts already hold this frame's walk
cycle; those rotations are written onto the glTF's own nodes and the model is posed from them, through
the same external-pose seam a vehicle's turret uses (§4).

```java
private static final GemRenderArmorModel ARMOR = new GemRenderArmorModel(
        (entity, stack, slot) -> switch (slot) {
            case HEAD -> HELMET_MODEL.get();
            case CHEST -> VEST_MODEL.get();
            default -> null;          // nothing on this slot
        });
```

The hook that hands vanilla that model is the **second** place the version matters. It keeps its name
on all three and changes its arguments on 26.1, because the wearer is no longer there to pass.

*Before 26.1:*

```java
@Override
public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(new IClientItemExtensions() {
        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                EquipmentSlot slot, HumanoidModel<?> original) {
            return ARMOR.prepare(entity, stack, slot);
        }
    });
}
```

*On 26.1* the signature is `getHumanoidArmorModel(ItemStack, EquipmentClientInfo.LayerType, Model)`:
armour is chosen during the submit phase, which is handed the wearer's `HumanoidRenderState` rather
than the wearer, and a layer type rather than a slot. `prepare` is unchanged and still takes all three,
so what a mod writes there is the same call with a `null` entity and the slot it derives from the layer
type.

**`ArmorAppearance`'s `entity` is `null` on 26.1, and only there.** The entity is extracted a phase
earlier and deliberately not carried forward, so there is no truthful way to produce one. Everything on
the stack is still available; a mod that varies a model by the wearer rather than by the item has to
carry what it needs on the item. This is the one behavioural difference in the whole API that a
signature does not show, which is why it is stated here and in the interface's own javadoc.

**One model per slot, not one model with hidden bones.** Vanilla asks four separate times, so a model
returned for two slots is drawn twice; `null` is how a helmet-only item says so.

**A piece is drawn once per `prepare`, and vanilla asks for it more than once.** `HumanoidArmorLayer`
renders the model once per `ArmorMaterial.Layer` -- two for anything dyeable -- again for an armour
trim, and again for the enchantment glint, all through the same `renderToBuffer`, because for a vanilla
model those are the same cubes under different textures. A GemRender model carries its own materials
and is drawn whole, so every call after the first is the same picture again: an enchanted chestplate
would cost two instances and composite any blended geometry twice. `prepare` is what marks a piece as
owing a draw, so it is not optional -- returning this model from `getHumanoidArmorModel` without it
draws nothing at all.

The glTF's node names are matched to the vanilla parts by `head`, `body`, `left_arm`, `right_arm`,
`left_leg`, `right_leg`. Pass a map to the constructor if your exporter called them something else. A
name that matches nothing is ignored rather than rejected -- a helmet has no leg bone.

**A wearer body with more joints** (elbows, knees, waist): its mod installs an `ArmorRig` with
`GemRenderArmorModel.setRig`; armour assets declared through `GemRenderModels.armor(asset)` then import with the
rig's `RigPatch` (joints added under the six part nodes, unskinned vertices weighted onto them), and `ArmorRig.pose`
runs after the six part rotations each draw. `GemRenderModels.get` / `handle` stay unpatched. AHF ships one.

### Batching, and where it happens

Nothing draws at the moment you submit. Copies join a batch and the batch is drawn when its pass
flushes, so **n copies of one model in one pass are one draw**, and copies of one model at one instant
share a single palette.

| Pass | Contains | Flushed at |
|---|---|---|
| `GUI` | inventories, hotbars, anything through `GuiGraphics`, and any entity rendered into a screen | `GuiGraphics.flush()` |
| `LEVEL` | armour, third-person held, dropped items, frames, block entity renderers | after entities, and after block entities |
| `HAND` | the first-person hand | the tail of the hand render |

Those are vanilla's own flush points, not points of GemRender's choosing, which is what makes deferring
safe: vanilla already batches its item geometry and flushes it exactly where ordering starts to matter.
The three are separate because each has its own projection, and batching across them would draw copies
under the wrong matrices.

**A pass is chosen by where the copy will be drawn, not by who queued it.** Vanilla renders entities
outside the level too -- the player in an inventory screen, the totem-of-undying animation -- through
the same renderers and the same armour layer, and nothing out there will ever flush the level pass. A
copy queued for `LEVEL` while the level render is not running is therefore queued for `GUI` instead,
which is where it will actually be drawn: in the frame it was queued in, inside whatever scissor and
lighting the screen set up for it. Armour on the player in your own inventory is that case, and it is
not one a consuming mod can see coming -- `HumanoidArmorLayer` hands out the same model either way.

### What it costs

Measured on the radar (1468 vertices, three primitives merged to one mesh), as the mean over ~10,000
frames of `-Pdirect=<n> -Pdirectstats`. The GPU figure brackets the **whole** flush -- palette upload,
uniform writes, draws and the state restore -- not only the draw.

| copies, one model | palettes | draws | GPU per flush | CPU per flush |
|---|---|---|---|---|
| 1 | 1 | 1 | 5.5 us | 1.8 us |
| 36 | 1 | 1 | 9.2 us | 1.3 us |
| 576 | 1 | 1 | 100 us | 6.6 us |
| 576, each at its own instant | 576 | **1** | 110 us | 15 us |

The other two passes behave the same way and need no trick, because vanilla does not flush inside
either. Both hands are one flush at the tail of the hand render: three copies there measured one draw,
one palette and one flush per frame. Everything drawn in the level -- armour on every wearer, every
dropped item, every frame -- is one flush after entities, because vanilla's entity loop contains no
flush at all; a dropped stack of more than one draws up to five copies of the model at one instant, so
they share a palette as well.

So a flush costs about **5 us before it draws anything**, and about **0.17 us per additional copy**.
Palette sharing is worth roughly 0.19 us per copy it saves; when it cannot help at all -- 576 copies at
576 different instants, a chest of guns each on its own reload timer -- the draw count still does not
move.

**The GUI needs one extra trick, because vanilla flushes per item.** `GuiGraphics.renderItem` ends with
an unconditional `this.flush()`, once per slot, so honouring every flush would mean a draw per slot --
36 items measured 266 us of GPU per frame against 9 us for the same 36 in one flush.

GemRender skips exactly that one flush -- the one inside `renderItem`, identified by its call site --
and vanilla still makes it, so vanilla's own item geometry is resolved as it always was. Everything
else drains the queue first: a decoration, a label, a scissor change, the tooltip, the frame's final
flush. A run of items with nothing drawn over them accumulates into one batch, so a chest of 36
undamaged, unstacked items is **one draw and one flush**, at the same 9 us as if they had been batched
by hand.

It cannot go further than that, and should not. Vanilla draws the count, durability bar and cooldown
sweep through `RenderType.guiOverlay`, which is `NO_DEPTH_TEST` with a `COLOR_WRITE` mask: those carry
no depth of their own and are correct only because they are painted after the item. Deferring past them
-- flushing once at the end of the screen, say -- puts the model over the durability bar. So an item
that *has* a decoration flushes at its decoration instead of at itself, which costs a flush and keeps
the picture right; only stacks of more than one, damaged items and items on cooldown pay it.

The skip is keyed on that one call site -- the `flush()` at the tail of `GuiGraphics.renderItem` -- and
on nothing else, so a `DirectRenderer.submit(..., DirectPass.GUI)` from your own screen code is drawn
at the next `GuiGraphics.flush()` like anything else. Measured with two real vanilla items drawn
between every copy: 36 models and 72 vanilla items still come out as **one flush and one draw**.

What still costs a flush is an item that draws something over itself -- a decoration, a label, a
cooldown sweep. `GuiGraphics.applyScissor` drains the queue (mixin): else queued items land under the next
scissor (creative tab icons clipped by the inventory player box).

### Texture slots

Material `extras.textureSlot: "<name>"` => its primitives take a texture chosen per submit (a player's skin on
first-person arm meshes):

```java
DirectRenderer.submit(model, palette, morphs, pose, light, overlay, argb, DirectPass.HAND, VariantUv.NONE,
        Paint.NONE, 0, null, slot -> slot.equals("player_arm") ? player.getSkin().texture() : null);
```

- Unbound (`null`) => that slot's meshes not drawn; the overloads without `TextureSlots` bind none.
- `TextureSlots.emissive(slot)` => slot unlit (displays): no diffuse, lightmap, dynamic lights; overlay still applies.
- Slot primitives: never atlased, UVs as authored, merged per (slot, material); batched per (mesh, texture).
- Direct path only: outside the Flywheel `Model` => world visuals draw none. `GemRenderGltfModel.slots()` lists them.
- Items: `ItemAppearance.slots(stack, context)`.
- Untextured material, `baseColorFactor != 1` => atlased (1x1 bake), even alone; off the sheet = white.

### What this path does not do

- **PBR is not applied.** A PBR model's sheet carries its extra bands and the vertex UVs address the
  base one, so what draws is the correct base colour under vanilla's item lighting -- plainer than the
  same model in the world, not wrong.
- **Blended geometry is not sorted.** The world path answers transparency with OIT; here blended
  batches are drawn after opaque ones but in queue order within that, so two blended items can
  composite wrongly against each other. Alpha-masked geometry, which is nearly all of it, is unaffected.
- **Shader packs.** This path draws with its own program, so a pack neither shades it nor breaks it;
  it looks the same under a pack as without one. That is the opposite of the world path's behaviour
  (§9).

## 7. Particles

Position, size, colour, spin = closed form of age. CPU writes 64 bytes once at spawn; nothing per frame.
3 000 particles = 3 000 cull invocations + one draw, zero Java.

### Pieces

| | |
|---|---|
| `GemRenderParticleTypes` | instance types below; `custom(vert, cull)` for your own shader |
| `ParticleStyle` | per-family curves: drag, gravity, growth, fade, contact response. Max 256 |
| `ParticleEmitter` | slot block + spawn ring. Lifetime = the effect's, **not** the visual's |
| `ParticlePool` | Flywheel side: one instance per slot; created in the visual, deleted with it |
| `ContactResponse` | `NONE`, `STOP`, `BOUNCE`, `DIE` |
| `ParticleLook` | per-particle colour + light, one int |

### Instance types

Same buffer, style, emitter; vertex shader differs.

| type | shape | spawn with | model |
|---|---|---|---|
| `BILLBOARD` | camera-facing quad, rolled by `spin` | `spawn` | `absorbance` / `glowing` / `additive` / `translucent` / `cutout` / `sprite` |
| `MESH` | model, +Y along velocity, rolled about it | `spawn` | any `Model` |
| `STREAK` | camera-facing quad along motion; head on the particle, length `max(streak * speed, width)`; settled => `width` dot | `spawn`; style `.streak(seconds)` | `glowing(texture)` / `additive(texture)`, v = 0 at the head |
| `DECAL` | quad flat on a plane; never moves | `spawnDecal(x, y, z, nx, ny, nz, life, size, roll, look)` | `decal(texture)` |
| `BODY` | model tumbling end over end; rests lying down, +Y horizontal | `spawn(probe, ...)` with a contact style | `rigid(gltfModel[, bake])` |

- `DECAL`: spawn velocity = surface normal, `spinPhase` = roll; style motion ignored.
- `BODY`: tumbles at `spin` about a horizontal axis, yawed from horizontal spawn velocity by `spinPhase`;
  rest angle snaps to nearest `pi/2 + k*pi`, eased over the rebound leg (`STOP`: at contact). Attitude
  assumes a floor; a rebound ending on a wall still lies "flat".
- `MESH` keeps the landing velocity after rest (attitude); `STREAK` reads `gemrender_particleMotion`
  (zero once settled).
- `BILLBOARD` near fade: alpha x `gemrender_nearFade(center, eye, size)` = 0 at `d <= 0.5 size`, 1 at
  `d >= 1.5 size`; 0 => zero-size quad. Camera inside a sprite => no full-screen layer.

### Registering a style

```java
private static final int EXHAUST = ParticleBuffer.getInstance()
        .registerStyle(ParticleStyle.builder()
                .drag(ParticleStyle.dragFromPerTickFactor(0.9f))
                .gravity(-1.6f)
                .size(0.5f, 2.8f)
                .tint(0xFF7A28)
                .alpha(0.75f, 0.4f)
                .cool(0.1f, 0.6f)
                .glow(1.0f)
                .build());
```

- 256 styles, 24 KiB; full => throws. Per tint / light level: [`ParticleLook`](#colour-and-light-per-particle), not a style each.
- **All units per second**, velocity included. Per-tick loops (`v *= 0.9`, `vy += 0.004`) ->
  `dragFromPerTickFactor`, `gravityFromPerTickDelta`; per-tick delta x 20 = per-second.

### Emitting

Emitter on the effect: Flywheel rebuilds every visual when the render origin moves; a trail must not restart.

```java
public final class ExhaustEffect implements Effect {
    private final ParticleEmitter emitter =
            ParticleEmitter.create(EXHAUST, 512, x, y, z);

    public void emit(Vec3 at, Vec3 velocity) {
        emitter.spawn(at.x, at.y, at.z,
                -velocity.x * 8.0, -velocity.y * 8.0, -velocity.z * 8.0,
                2.5f, 0.6f + random.nextFloat() * 0.4f);
    }
}
```

- `spawn` overwrites the oldest slot: never full, never allocates. Unwritten slot = dead, culled.
- Thinning (not decals): mid-life radius under 3 px from the camera => kept with p = `(px/3)^2` (floor 1/64), survivor
  size x `1/sqrt(p)`: coverage kept, count cut. Spawn returns its contact either way. `-Dgemrender.particleThinPx=0`
  off.
- Ring sizing: `capacity == rate x life` wraps as the oldest dies (`-Pparticles=3000` settles ~2880
  alive). Size 10-20% above.

### Drawing

```java
public final class ExhaustVisual extends AbstractVisual
        implements EffectVisual<ExhaustEffect>, SimpleTickableVisual {
    private final ParticlePool pool;

    public ExhaustVisual(VisualizationContext ctx, ExhaustEffect effect, float partialTick) {
        super(ctx, (Level) effect.level(), partialTick);
        this.pool = new ParticlePool(ctx, effect.emitter(),
                GemRenderParticleTypes.BILLBOARD,
                ParticleModels.glowing(TEXTURE));
    }

    @Override
    protected void _delete() {
        pool.delete();
    }
}
```

Atlas sprite (block chips): sprite baked into the mesh, cached per sprite (one model, one draw per sprite):

```java
TextureAtlasSprite sprite = ...;
Model chip = ParticleModels.sprite(
        ParticleQuad.ofUv(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()),
        InventoryMenu.BLOCK_ATLAS);
```

No `beginFrame`: per-frame variation belongs in the style or the closed form.

### Colour and light per particle

`look` = record's last float: `1 + (r5 << 19 | g6 << 13 | b5 << 8 | block << 4 | sky)`, max `2^24`, exact.

| | |
|---|---|
| `ParticleLook.NONE` (0) | style tint, style light |
| `of(rgb, block, sky)` | tint *= rgb (565); light replaced |
| `of(rgb)` | full bright |
| `lit(level, x, y, z, rgb)` | block + sky light of the containing block, read once at spawn |
| `blockColour(level, pos, state)` | vanilla `TerrainParticle` mean: sprite alpha-weighted mean x 0.6 x block tint; grass block untinted |

- Light read at spawn: day/night follows (lightmap); drifting into shadow does not.
- `blockColour`: sprite PNG decoded once per sprite (weak cache); call where the level is owned.
- Stock shaders: `gemrender_particleLight(p, s)`, `gemrender_particleColor`. Custom shader reading
  `s.light` ignores the look.

```java
int look = ParticleLook.lit(level, x, y, z, ParticleLook.blockColour(level, hitPos, state));
emitter.spawn(x, y, z, vx, vy, vz, life, size, spin, 1.0f, look);
```

### Fading out

`.fadeOut(u)`: alpha held at `alphaScale` until unit age `u`, then the `alpha` falloff over `[u, 1]`.
`u = 0` (default): whole life. `BODY` scales by `1 - fade` instead (same window, same default); a
casing wants e.g. `.fadeOut(0.85f)`.

### Decals

```java
emitter.spawnDecal(hit.x, hit.y, hit.z, normal.x, normal.y, normal.z, life, 1.0f, roll,
        ParticleLook.lit(level, hit.x + normal.x * 0.5, hit.y + normal.y * 0.5, hit.z + normal.z * 0.5,
                ParticleLook.WHITE));
```

- Budget = emitter capacity: ring, oldest overwritten. No world-wide budget object.
- Reach: `|spawn - emitter origin| <= DECAL_REACH` (2048) per axis, else `IllegalArgumentException`.
  Offsets are float: half-ulp at 2048 = lift/16; at 16384 = lift/2 => z-fight. World-wide budget =
  one decal emitter per region near the player (re-create when the player leaves it).
- Z-fighting: `decal()` polygon offset (Flywheel `-1, -10`) + lift `1/512` along the normal. No depth
  write, back faces culled.
- Drawn before translucent terrain: a decal under water composites behind it.
- Trap: quad overhangs block edges, unclipped. Keep size under the face or move the centre inward.

### Rigid glTF particles

`ParticleModels.rigid(model)`: loaded `GemRenderGltfModel` frozen at rest pose (skin palette applied,
weights renormalised) into one particle mesh with the model's materials. `rigid(model, bake)`: `bake`
after. Cached per model + bake; reload = new key.

- Author: origin = centre, +Y = long axis (`BODY`, `MESH` orient +Y).
- Pass half the thickness as `radius`: rests on, not in, the floor.
- `GemRenderModels.handle(id).get()` non-null before building the visual.
- Harness casing model and flash / dust / spark / bullet-hole textures live in the harness source set;
  consumers ship their own.

### Contact

Predicted at spawn: arc swept against the world once; two ages + a face ride in the slot; flight still
closed form.

```java
private static final int RUBBLE = ParticleBuffer.getInstance()
        .registerStyle(ParticleStyle.builder()
                .gravity(20.0f)
                .stopsOnContact()
                .build());
```

| | |
|---|---|
| `stopsOnContact()` | rests on the first floor. Rubble, casings, gore. Wall/ceiling => dead drop, settles below |
| `bouncesOnContact(restitution, friction)` | one rebound, settles where it lands |
| `diesOnContact()` | life shortened to the contact. Free |
| *(nothing)* | never swept |

```java
emitter.spawn(level, x, y, z, vx, vy, vz, life, size);
// Mesh: `radius` = half extent, axis-aligned box; centre rests `radius` off the surface.
emitter.spawn(level, x, y, z, vx, vy, vz, life, size, spinPhase, tintScale, 0.35f);
```

- No response on the style => no sweep, level argument free.
- `radius > 0`: centre ray + six offset rays at `±radius` per axis; earliest hit wins => a skimmed wall stops the
  side, not the centre. Offset inside a block at spawn => dropped. Box only: rest the mesh axis-aligned (a tilted
  cube's corners still sink).
- Burst: hoist the probe (remembers blocks; one burst walks mostly the same ground). The level
  overload keeps one probe while the clock stands still.
- Reads blocks: **call on the thread that owns the level** (tick, event), never a visual constructor.

```java
ParticleCollision.Probe probe = LevelContactProbe.of(level);
for (int i = 0; i < 1000; i++) {
    emitter.spawn(probe, x, y, z, vx(i), vy(i), vz(i), life, size, 0.0f, 1.0f, 0.35f);
}

ParticleCollision.Contact contact = emitter.spawn(probe, x, y, z, vx, vy, vz, life, size, 0f, 1f, 0.35f);
Vector3f restingPlace = ParticleCollision.positionAt(style, spawn, velocity, contact, contact.restAge(),
        new Vector3f());
```

- `DIE` records no contact: hit <=> `contact.life() <` requested life; hit time = `contact.life()`.
- Chord per step sized from the local acceleration (pull less drag) against a 0.1-block sagitta.
  Birth-speed sizing FORBIDDEN: stretched chords map the hit fraction to the wrong age => particle
  freezes mid-air. Terminal velocity => one chord for the rest of the flight.
- Limits: geometry changed after spawn (built wall, mined block) ignored; one rebound; accuracy = one
  chord (0.1 block).

### Your own particle shader

```java
private static final InstanceType<ParticleInstance> FLAME = GemRenderParticleTypes.custom(
        ResourceLocation.fromNamespaceAndPath(MODID, "instance/flame.vert"),
        ResourceLocation.fromNamespaceAndPath(MODID, "instance/cull/flame.glsl"));
```

Files in `assets/<namespace>/flywheel/instance/`; Flywheel scans every namespace.

```glsl
#include "gemrender:particle.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderParticle p = gemrender_particle(i.particle);
    GemRenderStyle s = gemrender_style(p.style);
    float age = flw_renderSeconds - p.spawnTime;
    ...
}
```

Contract:
- Cull: `radius = -1e18` when `!gemrender_particleAlive`, or dead ring slots draw.
- `spawnTime`, `life` as named: emitters and `aliveCount` read them.
- Yours: `sizeScale`, `spinPhase`, `tintScale`, `look` (per particle); the style's 24 floats
  (`ParticleStyle.of(float...)`; the builder is the stock shaders' convention). WF-Ballistics reuses
  the two cool fields as a white-out start and span. Float 22 = live cull (`ParticleBuffer.setStyleCull`),
  overwritten at runtime.
- Shape helpers: `gemrender_planeBasis`, `gemrender_decalCorner`, `gemrender_streakCorner`,
  `gemrender_particleMotion`, `gemrender_bodyBasis`, `gemrender_particleFade`, `gemrender_nearFade`. Java
  mirror: `ParticleShapes`, `ParticleCollision.motionAt`, `ParticleMotion.heat`.
- `absorbance` / `glowing` material: `flw_vertexOverlay = gemrender_particleOverlay(heat, center, radius)`
  (heat 0..1, e.g. `gemrender_particleHeat(s, unitAge)`; `radius` = world half-extent of the sprite's thin side).
  `gemrender_particleOverlay(heat)` / `ivec2(0, 10)` => reduced only (see below).
- `_gemrender_particles` bound on every Flywheel program; nothing to bind.

### What a closed form cannot do

No particle-particle forces; no reaction to world changes after spawn (both need last frame's state).
Wind, drag, gravity, buoyancy, growth, fade fine. Contact is predicted, not simulated ([Contact](#contact)).

### Shader packs

Iris: compat layer's instancing backend. Particles work (evaluation in the vertex shader). No GPU cull:
dead particle = zero-size quad, 4 vertex invocations, no fragments.

### Choosing a transparency

Only frame-rate decision on this page.

| `ParticleModels` | passes | depth | use |
|---|---|---|---|
| `absorbance(texture)` | 1 (Fabulous 2) | none | smoke, steam, dust, foam. `tau = -ln(1 - a)` summed: exact order-independent extinction; colour = tau-weighted mean per pixel |
| `glowing(texture)` | as `absorbance`, + RGBA16F target | none | fire, flash, sparks, exhaust. Style `.glow(share)`: share of alpha emitted while hot (fades over `cool` span); rest absorbs. Emission unlit; composited behind the pixel's absorbing media |
| `additive(texture)` | 1 | none | glow not ordered against water; prefer `glowing` |
| `cutout(texture)` | 1, early-Z | writes | bulk where hard edges are acceptable |
| `translucent(texture)` | OIT, 3 raster passes | OIT | layer-ordered colour (glass-like); most expensive |

- Pass count holds while no `translucent` / glTF `BLEND` draw shares the frame (`Absorbance.exclusive`);
  a mixed frame runs all three OIT passes for everything.
- Fabulous: depth-range pass kept; composite into the item-entity target at the nearest absorbance
  depth, sorted against water / clouds per pixel as one layer.
- Exclusive frames: `absorbance` / `glowing` (+ volumes) draw at 1/`Absorbance.resolutionFactor` per axis
  (default 4; `-Dgemrender.absorbanceFactor` overrides). Depth = farthest per block; upsample = bilinear x
  depth match. Mixed frames: full res (gap).
- Band: projected radius <= 4 reduced texels => full res, >= 8 => reduced, smoothstep between
  (`-Dgemrender.absorbanceBand=lo:hi`; `-1:0` = reduced only); tau split, not
  duplicated (`gemrender:absorbance_band.glsl`). Full share = second evaluate (+ front) submit into the full-res
  accumulators, upsample adds them. No radius in overlay (volumes, decals) => reduced only.
- `RopeModels.absorbance`: share per vertex (width varies along the rope); ends + middle agree => collapsed like a
  sprite, else both passes raster it and drop zero-share fragments.
- wflib smoke grenade, 6 m through the cloud, chain GPU us: factor 1 2506, 2 756, 4 329.

In game, wflib blast scenes, 1920x1080, RX 7900 XTX, Fancy:

| scene | OIT `translucent` | `absorbance` / `glowing` |
|---|---:|---:|
| `boom large` over water, GPU us/frame (OIT chain + split) | ~1330 | ~550 |
| same, fps | ~620 | ~1500 |
| `boom large` on land, GPU us/frame | ~580 | ~250 |

#### Only two of the three can be ordered against water

Flywheel draws after entities, **before** vanilla translucent terrain.

- `cutout`: correct (opaque where drawn, honest depth).
- `translucent`, `absorbance`, `glowing`: correct via `WaterSplit` (stack cut at the water surface).
- `additive`: wrong either way. Depth write => puff-shaped hole in the water; so `WriteMask.COLOR`
  => water paints over it (dimmed; bright glow over thick water vanishes).

Blended materials write no depth; occluding => `cutout`.

Cutout tuning: raise the alpha test (`ParticleModels.billboard(texture, Transparency.OPAQUE,
CutoutShaders.HALF)`), let texture alpha carve the silhouette. Low `alphaScale` + high threshold =>
all discarded; low threshold => visible squares.

### Cost

- CPU: one 64-byte write per spawn; per frame nothing (`instanceWrites=0`); one `glBufferSubData` per
  dirty page run, ~1 us.
- Contact: spawn only, only for styles asking for it. 1 000 colliding spawns:

| | 1 000 colliding spawns |
| --- | --- |
| sweep, walking probe | **1.22 ms** |
| sweep, one `BlockGetter.clip` per segment | 1.84 ms |
| blocks asked, walking probe | **2 233** |
| blocks asked, clipping | 46 500 |
| blocks asked at 2 000 particles | 2 507 (**1.12x**, not 2x) |

- Lookups bounded by burst volume, not count. Source: `ParticleSweepScaleTest`.
- In game, 1920x1080: 49-explosion volley, ~1 500 colliding mesh particles (block geometry): **163 fps
  vs 169 empty**, GPU 8-10%. Spawn frame (196 models baked, 1 500 arcs) included.

GPU: RX 7900 XTX, Mesa 26.2, `indirect`, 846x1020, 200 ticks.

| row | frames/s |
| --- | --- |
| empty scene, one particle | 1926 |
| 3 000, `cutout` | 2167 |
| 3 000, `additive` | 2150 |
| 3 000, `translucent` | **713** |
| 30 000, `cutout` | 2067 |
| 30 000 from 2 000 emitters, `cutout` | 2126 |
| 100 000, `cutout` | 900 |
| 300 000, `cutout` | 333 |
| 300 000, `cutout`, `-PparticleSize=0.15` | 1994 |

- Instancer never the bottleneck: last two rows = same 288 000 live instances, differ only in covered
  pixels; 6x.
- Cost = fill. Levers: raster passes (OIT 3x `cutout`) and covered pixels (`-PparticleSize=0.15`:
  3 000 OIT 649 -> 1838 fps; 300 000 cutout 333 -> 1994). Count budget treats the wrong number.

```
./gradlew client -PspikeExit=400 -PquickPlay=spike -Ppitch=0 -Pparticles=3000 \
    -PparticleBlend=cutout   # additive | translucent | cutout
    -PparticleSize=0.15      # multiplies every particle's size
    -PparticleEmitters=200   # splits the count across that many emitters
    -PparticleSpacing=0      # blocks between them, 0 stacks them on one spot
```

### Cost per kind

`-PparticleKinds=<kind> -PparticleKindCount=N` (harness), 1920x1080, RX 7900 XTX, `indirect`, camera 4-5
blocks from the effect, 200 sampled ticks. `N` = target alive; spawn rate `N / life`.

| kind | material | N | frame ms | emit us/tick | spawns/tick |
|---|---|---:|---:|---:|---:|
| none | | 0 | 0.36 / 0.52 | 2-4 | 0 |
| decal | `decal` | 1k / 10k | 0.43 / 0.40 | 8 / 12 | 17 / 167 |
| casing (`BODY`, bounce) | glTF opaque | 1k / 10k | 0.58 / 0.45 | 19 / 24 | 8 / 83 |
| dust (`blockColour` + `lit`) | `translucent` | 1k / 10k | 0.58 / **1.92** | 14 / 61 | 31 / 312 |
| flash | `additive` | 1k / 10k | 0.45 / 0.92 | 12 / 121 | 625 / 6250 |
| smoke | `translucent` | 1k / 10k | **1.82 / 12.1** | 12 / 23 | 17 / 167 |
| spark (`STREAK`, bounce) | `additive` | 1k / 10k | 0.43 / 0.42 | 38 / 236 | 56 / 556 |

- Baseline drifted 0.36 -> 0.52 ms over the sweep; rows under ~0.6 ms are inside it.
- Only OIT (`translucent`) rows cost: fill x 3 passes. Dust/smoke in bulk: `cutout`.
- CPU per colliding spawn ~0.3-0.4 us (sweep, probe per tick); flash spawn ~0.02 us.

### The water split taxes order-independent particles

`WaterSplit` resubmits every `ORDER_INDEPENDENT` draw once more (interleave with translucent terrain and
clouds).

- Skipped (1.20.1: never) when no visible section has a `RenderType.translucent()` layer and no cloud is
  folded. Translucent includes ice, stained glass, slime, honey, portals.
- Per pixel: prepass stencil marks water / cloud in front of the scene; unmarked pixels get split depth 0
  (fixed-function, hierarchical Z exact) => resubmitted fragments depth-rejected; behind composite
  takes the whole stack there.
- Remaining cost legit: smoke covering water or cloud pixels shades twice. Camera inside a gas cloud,
  clouds in view: resubmit ~3.9 of ~11.6 ms GPU.
- Pre-gate measurement, no water: `-PwaterSplit=false` 3 000 `translucent` 655 -> 878 fps, 30 000
  98 -> 152.

- Excluding particles from the resubmission FORBIDDEN: a particle in front of water must composite after
  it; and glTF `BLEND` materials share the path (glass row, `-PwaterColumn=8`, split on vs off: 56% of
  model pixels differ, up to 176/255).
- Skipping when nothing translucent is visible: 0.16% pixels, <= 6/255 (OIT dither) vs split.
- Avoid the tax: Fabulous (`modeActive()` false, no split) or leave `ORDER_INDEPENDENT`. `cutout`
  stays ordered against water; `additive` does not.

### Clouds are in the split too

- Cloud surface folded into the water prepass depth: an OIT model can be in front of or behind a cloud.
- No extra pass: cloud depth rides the opaque-depth seed copy; front half of the composite split by
  cloud coverage; covered pixels wait for `AFTER_WEATHER`. Cost: one more cloud-mesh raster, skipped
  when the view misses the cloud layer.
- `-PcloudSplit=false` removes it; `-Pclouds=off` is the other A/B half. No cloud in level => identical
  to the pixel (measured).
- One split point per pixel: in front of water and behind a cloud in one pixel => composited against
  the nearer.
- Absorbance front half split by cloud coverage too (cloud pixels wait for `AFTER_WEATHER`).

### Camera inside a cloud

`CameraMedium.getInstance()`, per frame (else `outside()`):

| call | effect |
|---|---|
| `inside(style, extinction, box, cull)` | billboards of `style` fade out within `cull` (ramp `ParticleBuffer.CULL_RAMP`); fog `[0, cull + ramp / 2]` |
| `inside(volume, box)` | volume hidden; fog = noise-free mean of its march (`VolumeNoise.densityByShape` over its field, 12 samples/pixel, HG phase on `flw_light0Direction`) |

- Fog: one full-screen pass at `AFTER_LEVEL` (after Fabulous composite, before hand), ending at `box` exit.
  Colour = style tint x style light (lightmap), as the cloud draws it.
- Billboard extinction: `ParticleOptics.extinction(style, sizeScale, livePerCubicBlock, spriteAlpha)`
  (steady state, unit age uniform; matches a random-cloud Monte Carlo within 5%).
- NeoForge 1.21.1 only; 1.20.1 / 26.1 / Fabric: cull applies, no fog (TODO in `CameraMediumEvents`).
- `-Dgemrender.medium=false`: off (A/B).

Camera inside wflib `gas 8`, 1920x1080, RX 7900 XTX, Fancy, GPU ms (OIT chain + split):

| gas | off | on | fog pass |
|---|---:|---:|---:|
| raymarched volume | 7.4-10.6 + 3.7-5.3 (76-105 fps) | 1.1-3.5 + 0.5-1.7 (205-580 fps) | 0.06 |
| billboards | 3.9 + 2.0 (~180 fps) | 1.05 + 0.53 (~650 fps) | 0.01 |

- Region means fog vs no fog: billboards within 2/255; volume within 6/255 (near noise detail lost).
- Spread on the fog side: other clusters around the camera still march.
- Gap: overlapping volumes => only one becomes fog; the rest still march.

### Time resolution

- `age` from `flw_renderSeconds`: 32-bit float of `(ticks + partialTick) / 20`, shared Flywheel
  uniform. Resolution: ~0.25 ms after 1 h uptime, 8 ms after 1 day, 60 ms after 1 week. `spawnTime`
  on the same grid => `age` exact; motion steps visibly past ~1 day in one dimension.
- Layout authority: `particle.glsl` (style 24 floats, record 16). Java mirror `ParticleMotion`,
  held by `ParticleMotionTest`, `ParticleShapesGlTest`, `ParticleInstanceShadersGlTest`.
- `RGBA32F` buffer: 4 fetches per particle, 6 per style. vec4 fetch vs scalar: inside noise up to
  300 000; kept for simplicity.

---

## 8. Ropes, cables and chains

A rope is the same idea as a particle, applied once more: its shape is a **closed form of its two
endpoints**, so the vertex shader can work out where every ring of it goes and the CPU writes only
where the ends are. One rope is one instance of a shared tube mesh. A hundred of them are one draw.

Vanilla does the opposite — `renderLeash` builds a strip of quads into a `VertexConsumer` every frame,
per lead, and none of them batch. That is the cost this replaces.

### What it is for

Leads and leashes, tow cables, power lines, rigging, a winch, a tethered balloon, an anchor chain.
Anything where two things are connected and the connection should be visible. It is **decoration**:
nothing here is simulated, collidable or synced, and there is no rope physics. If you want a rope that
does something, drive its endpoints from whatever already does that thing.

### Drawing one

```java
public final class MooringVisual extends AbstractVisual {
    private final RopeInstance rope;

    public MooringVisual(VisualizationContext ctx, ...) {
        this.rope = ctx.instancerProvider()
                .instancer(GemRenderRopeTypes.ROPE, RopeModels.cutout(CHAIN))
                .createInstance();

        rope.between(x, y, z, x, floor, z)
                .slack(1.01f)
                .radius(0.055f)
                .tiling(y - floor)
                .litUniformly(light)
                .refresh()
                .setChanged();
    }
}
```

Endpoints are relative to the render origin, like every other Flywheel instance. Move them and call
`setChanged()`; that is six floats, and it is the only per-frame cost a rope has.

### The shape

| | |
|---|---|
| `sag(blocks)` | how far it droops below the straight line, at its deepest |
| `slack(ratio)` | the same thing said the other way: 1.0 is taut, 1.2 is a fifth longer than the gap |
| `length(blocks)` | and the same again, as an absolute length of rope |
| `radius(blocks)` | how thick it is |
| `tiling(repeats)` | how many times the texture repeats along it |
| `twist(turns)` | full turns of the cross-section end to end, for a braid or a link |
| `sway(amplitude, frequency, phase, waves)` | a drift across the rope, pinned to zero at both ends |

`slack` and `length` **solve for the sag on the spot**, so call them after setting the endpoints.
The solve is a bisection on arc length and it happens once per change, not once per vertex — the
answer is the same for all several hundred of them.

**They mean nothing on a rope that hangs straight down, and they will not say so.** The sag is along
-Y, so when the two ends share a column it slides points *along* the rope rather than bowing it: the
length barely responds until the curve doubles back, and then jumps. A twenty-block vertical rope asked
for 1% of slack is handed a sag of 5.7 — length-correct, drawn in exactly the same place, and with half
the texture's repeats crammed into the bottom fifth. For anything vertical, including every mooring and
every hanging chain, set `sag` yourself; zero is right for a taut one and the shape is identical anyway.

The curve is a **parabola, not a catenary**, and that is deliberate rather than an approximation taken
for speed. The closed-form catenary is a function of horizontal distance, so it has no value at all
when one endpoint is directly above the other — which is the case a mooring chain is in for its whole
life. The parabola is defined everywhere, is what a uniformly loaded cable actually hangs in, and at
the sag ratios anything in a game uses the two differ by well under a pixel.

Give `sway` a **phase that is a function of something stable** — a block position, an entity id — not a
random. A random looks identical on frame one and jumps every time Flywheel rebuilds the visual, which
it does whenever the render origin moves. Same rule as `AnimationPhase.scattered`.

### Lighting

`light` is the near end and `lightB` the far one, interpolated along the rope. `litUniformly(l)` sets
both. A chain hanging from a lit surface into a dark one is the case this exists for; giving both ends
one value makes a fifty-block rope uniformly bright.

### Tessellation and materials

`RopeModels.solid`, `.cutout` and `.absorbance` take an optional `rings, sides`. Rings is along the
rope — 16 is enough that a rope bent double has no visible flats. Sides is around it, and **4 is what
a vanilla lead looks like**, which is usually what you want; a thick chain seen close wants 6 or 8.

Use `.cutout` for anything whose texture has holes in it, which includes every chain texture. Use
`.absorbance` for a rope hanging in water: it joins the water split's absorbance pass, so it is tinted
and faded by depth instead of drawn flat through a hundred blocks of ocean (§7).

The tube is **capped at both ends**, because Flywheel culls backfaces and an open tube does not read
as an open tube — it reads as a missing face.

### Your own rope shader

Same escape hatch as particles. Declare an instance type over the same layout and include the shipped
curve, so what you draw and what the cull shader tests stay the same thing:

```java
private static final InstanceType<RopeInstance> CABLE = GemRenderRopeTypes.custom(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "instance/cable.vert"),
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "instance/cull/cable.glsl"));
```

```glsl
#include "gemrender:rope.glsl"
```

`gemrender_ropePoint`, `gemrender_ropeTangent`, `gemrender_ropeFrame` and `gemrender_ropeSway` are the
whole of it. `RopeCurve` is the same construction in Java, for tests and for anything that needs to
know where a rope is without asking the GPU.

### What it costs

Per rope, per frame: nothing, unless an endpoint moved, and then 84 bytes. Per rope, ever: one cull
invocation. The draw is shared with every other rope of the same model.

The one number to watch is `rings x sides x 4` vertices per rope, all of which are transformed whether
the rope is 2 blocks long or 60. At the default that is 288 per rope — comparable to a small model, and
the reason to turn the tessellation *down* for something thin and short rather than up.

## 9. Lights

Point and spot lights, forward-shaded: every surface shader adds `albedo x E` before fog. Every target:
1.20.1, 1.21.1 and 26.1.2 on every supported loader. Shadows opt-in per light.

| lit | not lit |
|---|---|
| terrain (vanilla, Sodium 0.5/0.6/0.8, 0.9 on 26.1), entities, block entities, items in the world, vanilla particles, Flywheel instances, direct path in the level, first-person hand (vanilla + direct `HAND`) | GUI, anything after the level (`AFTER_LEVEL` included) |

### Submitting

Immediate mode: a provider is polled on the render thread at the start of every level frame. Nothing
persists between frames; interpolate with `partialTick` yourself. `Lights.shading()` false (shader pack) =>
not polled, nothing shaded: keep a fallback (lightmap boost) for lights that matter.

```java
LightCookie beam = Lights.cookie(ResourceLocation.fromNamespaceAndPath("mymod", "textures/light/torch.png"));

Lights.register((sink, partialTick) -> {
    Vec3 eye = player.getEyePosition(partialTick);
    Vector3f look = player.getViewVector(partialTick).toVector3f();
    sink.spot(eye.x, eye.y, eye.z, look, new Vector3f(0, 1, 0), 32.0f, 10.0f, 30.0f, 0xFFF2E0, 300.0f, beam, true);
    sink.point(x, y, z, 8.0f, 0xFF9040, 40.0f, false);
});
```

| field | |
|---|---|
| `range` | hard edge, blocks. Irradiance `rgb * intensity * (1 - (d/range)^4)^2 / (d^2 + 1)` |
| `intensity` | at ~1 block. `E ~ 1` (lightmap-white) at distance `d` needs `~d^2`: 300 for a 17-block beam |
| `innerDegrees` / `outerDegrees` | half angles; smooth falloff between, squared. `outer < 89` |
| `up` | any vector off the axis; orients the cookie |
| `cookie` | `null` = plain cone |
| `shadow` | occluded by full opaque blocks (`isSolidRender`); entities, slabs, glass, leaves cast none |

`Lights.MAX` = 64 per frame after frustum culling; nearest kept.

### Infrared

`point(..., shadow, ir)` / `spot(..., shadow, ir)`: `ir = true` => shaded per the pass's `Lights.IrView`, from
the one source `Lights.irView(Supplier<IrView>)` polled at every level pass start (offscreen passes included).

| `IrView` | IR lights |
|---|---|
| `OFF` (default) | dropped at collection |
| `SPLIT` | main view only: `albedo x E_ir` -> `IrTarget.texture()` (RGBA16F, COLOR1 from `AFTER_SKY`, Fabric `AFTER_SETUP` below 26.1 and `START_MAIN` on 26.1, through the hand); colour untouched. `IrTarget.filled()` after the level render |
| `MERGED` | added into colour (IR sensor views) |

`Lights.nightSky(ToDoubleFunction<IrView>)`: moon + starlight irradiance on a fully sky-lit surface x (lightmap sky
level)^2 (roofed / caves 0); `SPLIT` => IR channel (16F), `MERGED` => colour (8-bit: < ~1/255 after albedo vanishes =>
merged sensors pre-scale), `OFF` => none.

Not in `IrTarget`: Fabulous translucent/particle/weather targets, Flywheel OIT.

The IR attachment is writable only for programs declaring `_grl_irOut` at location 1. Shader and draw-framebuffer
changes update that routing; programs without the IR output preserve the existing IR image.

Lightmap: albedo recovery floors the lightmap at 1/255 => darkness mods zeroing it still light dynamically.

### Shadows

- Traced per frame against a 1-bit block grid 128^3 around the camera (±64 blocks). Nothing past the grid is lit by a shadowed light.
- Shadowed light whose source is outside the grid, or beyond the nearest 16 shadowed, is **dropped**, not shaded unshadowed: a flashlight must never show through a wall.
- Soft edge ~1 texel (128^2 per light). Contact shadows shrink by up to half a block (bias toward the light).
- Grid refill after teleport / chunk load: up to 0.2 ms CPU per frame, nearest sections first; light leaks through not-yet-filled sections meanwhile.
- Muzzle flashes and other short-lived fill lights: `shadow = false`, they do not compete for the 16.

### Cookies

`Lights.cookie(texture)`: RGB multiplies the beam, **linear** (no sRGB decode). Centre = axis, the
inscribed circle = the outer cone, `+v` = `up`. Resampled to 256^2; reloaded with resources; missing
texture => magenta. Square corners outside the circle are cut by the cone. `gemrender:textures/light/flashlight.png`
ships as a reference (LED hotspot, reflector rings, dark annulus, spill). Spot only; point lights take none.

### Your own shader

Splice `LightShaders.include()` after `#version` (identifiers `_grl_`-prefixed), then:

```glsl
gemrender_lightPrepare(p);                 // top of main, uniform control flow (derivatives)
...
color.rgb += albedo * gemrender_lights(p, gemrender_lightNormal(n));
```

`p` camera-relative world position, `n` world-oriented (zero = no facing, e.g. billboards);
`gemrender_lightFaceNormal(p)` for a format without normals. After linking call
`LightShaders.bindProgram(program)`. Texture units 20-23 are taken. Albedo is colour without the lightmap.
IR: `_grl_irSum` after the call (already inside the result under `MERGED`); under `SPLIT` write
`albedo * (_grl_irSum + gemrender_nightSky(skyLevel))` to `layout(location = 1)`.

### Shader packs

Off while an Iris pack is in use: the pack's programs replace every patched one.

---

## 10. Things that will bite

Failure modes that render something plausible rather than throwing.

### Shader packs disable half the renderer

Under any Iris shader pack, the compatibility layer merges Flywheel's vertex shader into the pack's
and *discards Flywheel's fragment shader entirely*. Consequences:

- **GemRender's own PBR shader does not run.** It cannot; the fragment stage is the pack's.
- **The material data still gets through, if the pack is set up for it.** GemRender registers a
  LabPBR loader with Iris, so a pack whose resource-pack materials are turned on lights the model
  with its own PBR from GemRender's normal and specular maps. Emission is a scalar in LabPBR and a
  colour in glTF, so an emissive strip glows in its albedo's colour.
- **Every pack ships with those materials off.** At stock settings a pack does not read them, and a
  PBR model falls back to base colour – no normal mapping, no roughness, no emissive, a glowing lamp
  as a black plate. Everything baked into the sheet at import survives, so tints and occlusion remain.
- **Parallax occlusion mapping must stay off.** It needs `mc_midTexCoord`, which instanced geometry
  has no attribute for; with it on, models render near-black. This one is not GemRender's to fix and
  happens whether or not GemRender supplies material data.
- **`BLEND` geometry is discarded, not blended.** The layer forces an alpha test of `GREATER 0.5`
  onto every Flywheel draw, so a half-transparent shell disappears rather than compositing badly.

The settings each pack needs, and the line citations behind these four claims, are in the
maintainers' notes.

> When judging a screenshot, check whether a pack was on **and what its material setting is** before
> concluding anything. A flat, unlit, non-emissive render under a stock pack is the expected result,
> not a regression.

### Cost is per distinct pose, not per machine

The only cost in the design that scales with instance count is one palette evaluation per animated
instance per frame. `PoseCache` collapses that: instances of the same model, on the same clip, at the
same quantised instant evaluate once and share a `boneBase`. Sixty-four machines placed at different
times still cost sixty-four evaluations, and that is the correct answer. Sixty-four machines running
in lockstep cost one.

### Do not set changed unnecessarily

The instance struct is 96 bytes and does not dedupe the way palettes do. Compare before assigning, as
in the example above, so a machine holding a shared pose stops re-uploading. On the `indirect` backend
a single instance write costs a whole 32-instance page.

### Constructors run off the render thread

Flywheel builds visuals on its task executor, so a visual constructor can run on any thread and
several can run at once. Do not touch GL there, and do not throw: an exception on a task thread takes
the client down with a stack trace that names none of the responsible code. Return an empty visual
instead, as the example does when the model is null.

---

## 11. 2D canvas

`com.wf.gemrender.ui`, 1.21.1 NeoForge only (`ui_supported`). Immediate-mode 2D recorder for HUDs, GUI
panels and texture surfaces; one quad per primitive in a CPU arena, drawn in as few `glDrawElements` as
texture slots (8 per batch) and clip changes allow. Steady state allocates nothing.

```java
Canvas c = Canvas.shared().begin(graphics);      // or begin(CanvasTarget, w, h, clearArgb) / begin(fbo, px, py, w, h, topRowFirst)
c.push().translate(x, y).rotate(rad).scale(s);
c.fill(...); c.roundRect(...); c.line(x0, y0, x1, y1, width, argb); c.arc(cx, cy, r, thickness, start, sweep, argb);
c.sprite(texture, x0, y0, x1, y1, u0, v0, u1, v1, argb); c.text(buffer, x, y, argb, shadow);
c.pop();
c.end();
```

| rule | |
|---|---|
| units | GUI px of the `GuiGraphics` pose at `begin`; target begins: caller's units over the target |
| colour | ARGB taken literally: alpha 0 = invisible (vanilla `drawString` treats < 4 as opaque) |
| angles | radians, 0 = +x, positive = clockwise on screen |
| AA | `line`, `polyline`, `dashed`, `arc`, `ring`, `disc`, `sector`, `roundRect`/`roundFrame`: edge coverage from screen-space derivatives, 1 px soft under any scale. `fill`, `quad`, `triangle`, `fan`: hard edges |
| clip | `pushClip` = screen-aligned bounds of the rect (rotated => its bounding box), intersected with the parent and any enclosing vanilla scissor |
| text | default font glyphs (vanilla atlases), `CharSequence` or `FormattedCharSequence` (style colour, bold, italic; no underline/strikethrough/obfuscation). `width(..)` = `Font.width`. Glyphs sampled sharp-bilinear (4 `texelFetch`): == nearest at integer px/texel, even columns at fractional scale |
| `TextBuffer` | fixed capacity, never grows, appends past capacity dropped; number/clock/hex formatting without allocation |
| vanilla in between | `suspend()` draws pending quads and restores GL; vanilla draws (items, entities); `resume()`. `applyTo(pose)` puts the canvas transform on a `PoseStack` |
| targets | `CanvasTarget`: owned RGBA8 FBO, premultiplied alpha, rows upright for v0 = top. `begin(fbo, ..., topRowFirst = true)`: draw over a camera-rendered framebuffer, no clear |
| not re-entrant | one `begin`/`end` per instance at a time; `shared()` = render-thread singleton |

Measured (Mesa radeonsi, glthread on): `glGet*` = glthread round trip, 18 per begin cost 31 us => state
is read from `GlStateManager`'s shadow. `glBufferData` per flush 30 us; persistent map + fences worse
(fence = sync); `glBufferSubData` into a fixed ring 3 us. 781 vertices / 4 batches: whole HUD pass 26 us.

---

## Reference

### Where a model can be drawn

Every place vanilla draws a model or an item, and what carries it there. All three versions.

| Target | How | Section |
|---|---|---|
| Block entity in the world | Flywheel `BlockEntityVisualizer` + `GemRenderInstance` | [2](#2-draw-it-on-a-block-entity) |
| Entity in the world | `GemRenderEntityVisual` | [3](#3-draw-it-on-an-entity) |
| Item in an inventory or the HUD | `GemRenderItemRenderer`, GUI pass | [6](#6-items-armour-and-the-hand) |
| Item in the first person | the same, hand pass | 6 |
| Dropped item, item frame, third person, head slot | the same, level pass | 6 |
| Worn armour, on a mob and in a screen | `GemRenderArmorModel` | 6 |
| Entity or item drawn *inside a screen* | rerouted automatically; you do not pick the pass | 6 |
| Particles | `ParticleEmitter` + `ParticlePool` | [7](#7-particles) |
| A connection between two points | `RopeInstance` | [8](#8-ropes-cables-and-chains) |

Two things that are deliberately not on it. **A static block is not a target** -- a GemRender model needs
a block entity, because Flywheel does no static-block instancing and there is no chunk-mesh route.
**A Flywheel visual does not run outside the level render**, so an entity drawn into a screen shows its
vanilla renderer there; its *armour* still comes from GemRender, because armour goes through the item
path and that one is rerouted.

You never choose which pass a copy joins. `GemRenderItemRenderer` picks it from the display context, and
the choice is then overridden by where the copy will actually be flushed -- a level copy submitted while
the level render is not running becomes a GUI copy. That is the whole reason an armour layer, which is
handed the same model in an inventory screen as in the world, needs to know nothing about the difference.

### The types

The types a consumer actually touches.

| Type | Purpose |
|---|---|
| `GemRenderModels` | `handle(id)` to declare an asset, `get(id)` to read it. `partsHandle(id)` for the rigid-part path, `built(id, builder)` for one you assemble yourself |
| `ModelCache.Handle<T>` | Stable reference across reloads. `get()`, `hasFailed()` |
| `GemRenderGltfModel` | The imported asset: `model()`, `layout()`, `bounds()`, `morphs()`, `animations()`, `jointCount()` |
| `GemRenderInstanceTypes.SKINNED` | The instance type to pass to `instancer(...)` |
| `GemRenderInstance` | `pose`, `boneBase`, `morphBase`, `boneSphere`, plus colour and light |
| `PoseCache` | `pose(layout, bounds, morphs, clip, time)`. The only shared way to get a `boneBase`. The `clips[]`/`times[]` overload layers several clips, each at its own instant; the `AnimationBlend` overload blends them |
| `BoneBuffer` | `getInstance()`: per-frame staging for what `PoseCache` does not produce. `addPalette(palette, count)` -> `boneBase`; `addSharedPalette` once per array per frame (rest palettes); `addMorphBlock`/`addFloatBlock` -> `morphBase`/`jointUvBase`. Upload, bind and `direct()` are GemRender's |
| `AnimationBlend`, `BlendMask`, `AdditiveReference`, `Crossfade`, `FadeCurve`, `BlendEvaluator` | weighted, masked, additive and crossfaded layers ([blending](#blending-crossfades-masks-additive-layers)) |
| `AnimationPhase` | `scattered(clip, seed)`, `of(clip)`, `REST`, `timeAt(seconds)` |
| `AnimationDrive` | The same, for a parameter instead of a clock. `cyclic(clip, unitsPerCycle)`, `ranged(clip, min, max)`, `timeAt(parameter)` |
| `GltfAnimation` | A clip. `name()`, `duration()`, `loop(t)` |
| `GemRenderPartsModel` | The rigid-part path (section 3). `animation(name)`, `partCount()`, `parts()`, `newTransforms()`, `drivenBy(clip)`, `withAncestors(driven)` |
| `PartsPose` | `evaluate(model, clips, times, out, only, scratch)`. Several layers at once, each at its own instant; `evaluate(model, blend, out, only, scratch)` blended |
| `RigBuilder` | A skeleton declared in code (section 4). `bone(...)`, `table()`, `attach(...)`, `build(material, clips)` |
| `RigGeometry` | One mesh on its way into a rig: positions, normals, texture coordinates, indices |
| `WavefrontObj` | `load(id)` reads a `.obj` as one `RigGeometry` per named group |
| `NodeSpin`, `NodeOscillate`, `NodeSwing`, `NodeHide` | Procedural drivers: turn, rock, sweep, disappear |
| `NodeRotation` | `compose(...)` and `offsetOf(...)`, for writing a `PoseDriver` of your own |
| `ParticleStyle` | the curves a family of particles shares (section 6). `builder()` (`.glow(share)` for `glowing`), `dragFromPerTickFactor`, `gravityFromPerTickDelta` |
| `ParticleBuffer` | `registerStyle(style)` returns the index every emitter of that family passes; `hideWhen(supplier)`: true at `bind()` => all particles hidden (sensor passes) |
| `ParticleEmitter` | `create(style, capacity, x, y, z)`, `spawn(...)`, `isIdle()`, `close()`. Held by the effect, not the visual |
| `ParticlePool` | `new ParticlePool(ctx, emitter, type, model)` in the visual, `delete()` with it |
| `GemRenderParticleTypes` | `BILLBOARD`, `MESH`, `STREAK`, `DECAL`, `BODY` to pass to `ParticlePool` |
| `Canvas` | 2D recorder (section 11): `shared()`, `begin(..)`/`end()`, shapes, sprites, text, `suspend()`/`resume()` |
| `CanvasTarget` | owned framebuffer a `Canvas` draws into; `texture()` = GL name |
| `TextBuffer` | fixed-capacity per-frame text with allocation-free number formatting |
| `GlPrograms` | raw-GL program from resources: `resource(namespace, path)`, `link(name, vert, frag)` (throws with the info log) |
| `PassState` | GL state around a raw-GL pass: `save()`/`saveDeep()` then `restore()`. Deep adds VAO, array buffer, cull, units 0-2 texture + sampler |
| `ParticleModels` | `additive`, `cutout` and `translucent` billboards; `cutout` is the cheap one, `additive` the one that cannot be ordered against water. `decal(texture)`, `rigid(gltfModel[, bake])` |
| `ParticleLook` | per-particle colour + light: `of`, `lit`, `blockColour` |
| `ParticleMotion` | the closed form in Java, for tests and for CPU-side code that needs a particle's position |
| `ParticleShapes` | decal, streak and body attitude in Java, mirroring `particle.glsl` |
| `GemRenderRopeTypes` | `ROPE`, the instance type to pass to `instancer(...)`, and `custom(vert, cull)` (section 8) |
| `RopeInstance` | one rope: `between`, `sag`/`slack`/`length`, `radius`, `tiling`, `twist`, `sway`, `lightB`, `refresh` |
| `RopeModels` | `solid`, `cutout` and `absorbance` tubes, each taking an optional `rings, sides` |
| `RopeCurve` | the curve in Java: `point`, `tangent`, `frame`, `length`, `sagForLength`, `sagForSlack`, `sphere` |
| `GemRenderItemRenderer` | items, in every context (section 6). `of(model, clip)`, `register(id, renderer)`, `get(id)`, `animates(stack, context)` |
| `ItemAppearance` | what a stack looks like per context: `model`, `clip`, `seconds`, `blend`, `transform`, `variant`, `tint`, `slots` |
| `GemRenderArmorModel` | worn armour. `prepare(entity, stack, slot)`, `DEFAULT_BONES` |
| `ArmorAppearance` | which model a piece draws per slot: `model`, `variant`, `tint` |
| `VariantUv` | one model's variant skins. `NONE`, and `GemRenderGltfModel.variant(i)` |

### The SKINNED instance layout

One hundred and twenty-four bytes. Worth knowing only if you are writing your own instance type against the
same shaders; the writer must match exactly, because it writes raw memory and a mismatch produces wrong
geometry rather than an error.

| Offset | Size | Field | Representation |
|---:|---:|---|---|
| 0 | 4 | `color` | 4 x normalised unsigned byte |
| 4 | 4 | `light` | 2 x unsigned short |
| 8 | 4 | `boneBase` | unsigned int, in matrices |
| 12 | 4 | `morphBase` | unsigned int, in floats |
| 16 | 16 | `boneSphere` | vec4, centre and radius |
| 32 | 64 | `pose` | mat4 |
| 96 | 8 | `uvOffset` | vec2, the variant's tile |
| 104 | 4 | `jointUvBase` | unsigned int, in floats; `0xFFFFFFFF` = none ([per-joint variants](#per-joint-variants)) |
| 108 | 4 | `paint` | unsigned int, layer; `0xFFFFFFFF` = none ([paint](#paint)) |
| 112 | 4 | `paintScale` | float, repeats per block |
| 116 | 4 | `paintReference` | unsigned int, `0xRRGGBB` |
| 120 | 4 | `paintRestBase` | unsigned int, in matrices; the rest palette |

Note what is absent: **overlay**. Stock instance types carry one because their vertex shaders assign
it over the per-vertex value, and GemRender spends the per-vertex overlay on the morph set index, so
there is nothing to overlay onto.

### Version differences

The complete list, read off the three built jars rather than remembered. Everything not named here has
the same public signature on 1.21.1, 1.20.1 and 26.1 (NeoForge/Forge jars), every type in the table above, the whole of
sections 1 to 5 and 7, and the two appearance interfaces in section 6.

| | 1.20.1 and 1.21.1 | 26.1 |
|---|---|---|
| An item claims its renderer | `IClientItemExtensions.getCustomRenderer()`, plus an item model inheriting `builtin/entity` | an item model of `"type": "minecraft:special"` naming `gemrender:model` and a `key`; no client code |
| `GemRenderItemRenderer` is a | `BlockEntityWithoutLevelRenderer` | `SpecialModelRenderer<ItemStack>` |
| Armour hook | `getHumanoidArmorModel(LivingEntity, ItemStack, EquipmentSlot, HumanoidModel)` | `getHumanoidArmorModel(ItemStack, EquipmentClientInfo.LayerType, Model)` |
| `GemRenderArmorModel` is a | `HumanoidModel<LivingEntity>` | `HumanoidModel<HumanoidRenderState>` |
| `ArmorAppearance`'s `entity` | the wearer | `null` -- see section 6 |
| A model that overflows its item cell in the GUI | overflows the slot | is clipped, unless the client item JSON says `"oversized_in_gui": true` |

Capabilities absent on a target rather than different; none is API:

- **Iris/shader packs**: 1.21.1 (both loaders) and 1.20.1 Fabric (through Colorwheel). None on 1.20.1
  Forge or 26.1.
- **Armour hook on 26.1 Fabric**: there is no `IClientItemExtensions`; hand `GemRenderArmorModel` to Fabric
  API's `ArmorRenderer`. The item renderer is claimed by model JSON as on 26.1 NeoForge.
- **Lights** ([section 9](#9-lights)): 1.21.1 NeoForge only.
- **Water split, and with it the cloud half, opt-in on 26.1** (`-Dgemrender.watersplit=force`,
  `-Dgemrender.cloudsplit=force`): on, OIT composites on the wrong side of water. Off, translucent
  models occlude the vanilla translucent surfaces behind them.
