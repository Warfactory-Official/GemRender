# Integration

How to draw a glTF asset with GemRender from another mod: registering the asset, rendering it on a
block entity, and the one thing the API cannot do.

This is the consumer-facing guide, and it is self-contained: everything you need to draw a model is
here. Where a rule below looks arbitrary, the reasoning is in the maintainers' notes
(`gemrender-internal/docs/`), which are kept outside this repository.

Requires Minecraft 1.21.1 and NeoForge 21.1.248. Flywheel 1.0.6 rides inside GemRender's jar, so
there is nothing else to install. Everything here is client side.

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

There is a second path for the shape this one is bad at – a handful of vehicles whose parts each
answer to something different, rather than a crowd of machines sharing a clock. Sections 1 and 2 are
the crowd; [section 3](#3-vehicles-and-animation-driven-by-something-other-than-time) is the vehicle.

Both assume the asset arrives with a skeleton in it. [Section 4](#4-rigging-a-model-that-has-none) is
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
is mark the asset as *wanted*, which is what gets it imported at the end of a resource reload, on the
render thread, at a defined moment. Hold it in a `static final` and the import never happens mid-frame
on a Flywheel task thread.

```java
public final class MyModels {
    public static final ResourceLocation DRILL =
            ResourceLocation.fromNamespaceAndPath("mymod", "models/drill/drill.glb");

    // Declaring the handle is the registration. Held in a static final so the asset is
    // imported at reload time rather than by whichever visual happens to want it first.
    public static final ModelCache.Handle<GemRenderGltfModel> DRILL_MODEL =
            GemRenderModels.handle(DRILL);

    private MyModels() {
    }
}
```

Read the model with `handle.get()`, which returns `null` if the asset failed to import.
`GemRenderModels.get(id)` is the same thing keyed by id.

### Reloads and failures

**Hold the handle, not the model.** A `GemRenderGltfModel` owns a stitched atlas texture and a range
of the shared morph buffer, both freed on reload. The handle is stable across reloads and what is
behind it is replaced, so a stored handle can never hand you a disposed model. A stored
`GemRenderGltfModel` can.

An import that throws does not propagate: you get `null`, the failure is cached so a broken file is
not re-parsed every frame, and the reason is logged once. A reload retries everything that failed,
because that is the moment the answer can have changed.

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

    private final GemRenderGltfModel gltf;
    private final AnimationPhase phase;
    private GemRenderInstance instance;

    public DrillVisual(VisualizationContext ctx, DrillBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);

        this.gltf = MyModels.DRILL_MODEL.get();
        if (gltf == null) {
            // The asset did not import and the registry has already logged why. Render
            // nothing rather than throw from a Flywheel task thread, where the stack
            // trace would name none of the responsible code.
            this.phase = AnimationPhase.REST;
            return;
        }

        // Seeded off the block position, so this machine is at the same point in its
        // cycle after a restart, on another client, and after the chunk reloads.
        this.phase = AnimationPhase.scattered(gltf.animationOrAny("run"), pos.asLong());

        instance = instancerProvider()
                .instancer(GemRenderInstanceTypes.SKINNED, gltf.model())
                .createInstance();

        instance.pose.translation(visualPos.getX(), visualPos.getY(), visualPos.getZ());
        instance.colorArgb(0xFFFFFFFF);
        // Per instance, because the per-vertex light attribute carries joint indices.
        relight(instance);
        instance.setChanged();
    }

    @Override
    public void beginFrame(Context ctx) {
        if (instance == null) {
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

**You do not manage the buffers.** Uploading the bone palette, binding it to texture unit 10, binding
the morph deltas to unit 11 and clearing the pose cache all happen once a frame before any Flywheel
draw. You never call `BoneBuffer.uploadAndBind()` or `PoseCache.endFrame()` yourself.

### The four fields

A `GemRenderInstance` has four things a consumer sets. Three of them fail loudly. One fails quietly,
which is why it is worth naming.

| Field | What it is | If you forget it |
|---|---|---|
| `pose` | Model to world transform, applied after skinning. Use `visualPos`, not `pos` | The model renders at the render origin, typically thousands of blocks away |
| `light` | Packed lightmap, per instance. Set it with `relight(instance)` | The model is black. Per-vertex light is unavailable, it carries joint indices |
| `boneBase`, `morphBase` | Offsets into the shared buffers, from `PoseCache.Pose`. Never compute these yourself | Every copy shows another machine's pose, or the rest pose forever |
| `boneSphere` | Bounding sphere of the *posed* model, also from `PoseCache.Pose` | **Geometry vanishes near the edge of the screen.** The default is a deliberately small one-block sphere, so the mistake is visible rather than silently disabling culling forever |

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

---

## 3. Vehicles, and animation driven by something other than time

Everything above assumes the shape section 2 is good at: many copies of one machine, all reading one
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

Same rule as section 1 – declare it in a `static final` so the import happens at reload rather than
on a Flywheel task thread. `GemRenderModels.parts(id)` resolves through the same cache but loads on
the spot, which inside a visual's constructor is an import mid-frame.

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

---

## 4. Rigging a model that has none

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

## 5. Items and BEWLR

GemRender **cannot** render an item, in hand, in a GUI or in an inventory, and this is structural
rather than a missing feature.

### Why it cannot work

A `BlockEntityWithoutLevelRenderer` draws into the item pipeline, which has no level and no Flywheel.
Three separate things in the stack are keyed to the world pass:

- `VisualizationManager.get(...)` takes a `LevelAccessor`. There is no manager, no instancer and no
  visual outside a level.
- The bone palette is bound from Flywheel's `DrawManager.render`, which only runs in the level render
  pass. In an item context texture unit 10 holds whatever was there last.
- Instance transforms are relative to `renderOrigin()`, a world coordinate, and are consumed by
  Flywheel's own view and projection uniforms rather than by the pose stack a BEWLR is handed.

So there is no flag to set. Item rendering means a completely separate CPU path that shares the
imported asset and nothing else.

### The vertex format trap

The obvious idea is to walk `gltf.model().meshes()`, call `Mesh.write(MutableVertexList)` and copy
the result into a `VertexConsumer`. That compiles, runs, and renders nonsense, because in a GemRender
mesh those channels do not mean what they are named.

| Vertex channel | What it actually holds | Naive result |
|---|---|---|
| `r g b a` | The four quantised bone weights | Model tinted by its own skin weighting |
| `light` | Four packed joint indices | Lightmap sampled at a joint index |
| `overlay.x` | Morph set index | Damage overlay from a morph id |
| `x y z` | Bind-pose position, unskinned | The model never moves |

A correct CPU path therefore has to undo all of it: evaluate a palette with
`GltfPose.evaluate(layout, clip, time, palette)`, decode each vertex's joints with
`BoneAttributeCodec.unpackJoint(packed, i)` and weights with `BoneAttributeCodec.decodeWeight(...)`,
blend the four matrices, transform position and normal, and then write real colour and real light. It
is a genuine piece of work, roughly the CPU mirror of `skin_lbs.glsl`, and **none of it exists in
GemRender today**.

### What to do instead

For almost every machine, the pragmatic answer is that the item does not need the animated model. In
rough order of effort:

1. **Ship an ordinary JSON item model.** An item in a hotbar is 16 pixels tall. A static baked model
   is usually indistinguishable and costs nothing.
2. **Render a still of the glTF.** Export one frame of the asset to a normal block model at build
   time and use that for the item, keeping the glTF for the world.
3. **Write the CPU path.** Only worth it if the item genuinely must animate. Budget for palette
   evaluation, skinning and a tessellator upload every frame the item is on screen, which is exactly
   the per-copy cost the whole project exists to avoid.

If you do build it, keep it in your own mod rather than reaching into GemRender internals. The seams
safe to depend on are `GemRenderGltfModel.model().meshes()`, `GltfPose.evaluate`, `GltfPaletteLayout`
and `BoneAttributeCodec`. Everything under `com.wf.gemrender.render` assumes a live GL context and a
Flywheel frame.

---

## 6. Things that will bite

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

## Reference

The types a consumer actually touches.

| Type | Purpose |
|---|---|
| `GemRenderModels` | `handle(id)` to declare an asset, `get(id)` to read it. `partsHandle(id)` for the rigid-part path, `built(id, builder)` for one you assemble yourself |
| `ModelCache.Handle<T>` | Stable reference across reloads. `get()`, `hasFailed()` |
| `GemRenderGltfModel` | The imported asset: `model()`, `layout()`, `bounds()`, `morphs()`, `animations()`, `jointCount()` |
| `GemRenderInstanceTypes.SKINNED` | The instance type to pass to `instancer(...)` |
| `GemRenderInstance` | `pose`, `boneBase`, `morphBase`, `boneSphere`, plus colour and light |
| `PoseCache` | `pose(layout, bounds, morphs, clip, time)`. The only supported way to get a `boneBase`. The `clips[]`/`times[]` overload layers several clips, each at its own instant |
| `AnimationPhase` | `scattered(clip, seed)`, `of(clip)`, `REST`, `timeAt(seconds)` |
| `AnimationDrive` | The same, for a parameter instead of a clock. `cyclic(clip, unitsPerCycle)`, `ranged(clip, min, max)`, `timeAt(parameter)` |
| `GltfAnimation` | A clip. `name()`, `duration()`, `loop(t)` |
| `GemRenderPartsModel` | The rigid-part path (section 3). `animation(name)`, `partCount()`, `parts()`, `newTransforms()`, `drivenBy(clip)`, `withAncestors(driven)` |
| `PartsPose` | `evaluate(model, clips, times, out, only, scratch)`. Several layers at once, each at its own instant |
| `RigBuilder` | A skeleton declared in code (section 4). `bone(...)`, `table()`, `attach(...)`, `build(material, clips)` |
| `RigGeometry` | One mesh on its way into a rig: positions, normals, texture coordinates, indices |
| `WavefrontObj` | `load(id)` reads a `.obj` as one `RigGeometry` per named group |
| `NodeSpin`, `NodeOscillate`, `NodeSwing`, `NodeHide` | Procedural drivers: turn, rock, sweep, disappear |
| `NodeRotation` | `compose(...)` and `offsetOf(...)`, for writing a `PoseDriver` of your own |

### The SKINNED instance layout

Ninety-six bytes. Worth knowing only if you are writing your own instance type against the same
shaders; the writer must match exactly, because it writes raw memory and a mismatch produces wrong
geometry rather than an error.

| Offset | Size | Field | Representation |
|---:|---:|---|---|
| 0 | 4 | `color` | 4 x normalised unsigned byte |
| 4 | 4 | `light` | 2 x unsigned short |
| 8 | 4 | `boneBase` | unsigned int, in matrices |
| 12 | 4 | `morphBase` | unsigned int, in floats |
| 16 | 16 | `boneSphere` | vec4, centre and radius |
| 32 | 64 | `pose` | mat4 |

Note what is absent: **overlay**. Stock instance types carry one because their vertex shaders assign
it over the per-vertex value, and GemRender spends the per-vertex overlay on the morph set index, so
there is nothing to overlay onto.
