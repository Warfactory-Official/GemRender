# GemRender

A GPU-driven model and animation renderer for Minecraft, built from one source tree for
**1.21.1 (NeoForge)**, **1.20.1 (Forge)** and **26.1 (NeoForge)**.

GemRender ties together three things that already exist separately and have never been made to work as
one: a **glTF 2.0 asset pipeline**, **Flywheel's retained instancing and GPU culling**, and **skinning
evaluated on the GPU**. Animated models stay instanced – one draw call per model per batch, however
many copies are on screen.

![four glTF radars, each at its own animation phase](docs/images/radar-gltf.png)

## What it does

- **Imports glTF 2.0** – node animation, skinning and morph targets, all three of glTF's animation
  mechanisms, plus metallic-roughness materials with normal, emissive, metallic-roughness and
  occlusion maps. A model's textures are stitched at import, so a multi-part machine is a single draw.
- **Imports Bedrock `.geo.json`** through the same pipeline, for assets authored as Minecraft models.
- **Rigs a model that has none** – declare a skeleton in code with `RigBuilder` and bind Wavefront OBJ
  geometry to it.
- **Draws on block entities and on entities** – a machine, a mob, a vehicle, a projectile. The entity
  path handles the two things that differ: a model that is still importing when the entity comes into
  view, and culling against the model's own bound rather than the entity's hitbox.
- **Wears several skins out of one sheet.** A livery, a camo, a team colour: declared together, stitched
  into one texture at import, and picked per instance, so copies in different skins still draw in one
  batch.
- **Skins in the vertex shader Flywheel already runs**, not in a pre-pass. Cost is O(visible vertices)
  rather than O(instances × vertices), the mesh stays shared, and Flywheel's GPU frustum and occlusion
  culling keep applying.
- **Shares work between copies that agree.** A copy's animation phase is a pure function of the world
  clock and its own position, so it needs no stored state and survives a chunk reload identically;
  copies genuinely at the same instant share one evaluated bone palette, and copies that are not, do
  not.
- **Animates without a clip.** An animation is a list of drivers, and a glTF channel is only one kind:
  a shaft can be given a rate instead of a keyframe track. Drivers compose rather than replace.

```java
NodeTable nodes = model.layout().nodeTable();
GltfAnimation turning = model.animation("running_loop")
        .with(NodeSpin.aboutY(nodes, nodes.slotOf(mastNode), rpm / 60.0f));
```

Transparent materials go through order-independent transparency, distant copies are animated on a
coarser time grid at constant on-screen error, and textures may be KTX2. Anything that has to be
attached to a bone – a muzzle flash, a held item, a light – can ask a pose where that bone ended up.

## Using it

**[docs/INTEGRATION.md](docs/INTEGRATION.md)** is the API guide: registering an asset, rendering it on
a block entity, rigging a model in code, and why items need a separate path.

```gradle
dependencies {
    compileOnly files('libs/gemrender-1.21.1-0.1.0.jar')
}
```

GemRender is a **required client-side dependency** of a mod that draws through it – declare it in
`neoforge.mods.toml`.

## Building

```bash
./gradlew build       # compile, unit tests, shader validation
./gradlew test        # unit tests: pure JVM, no GPU needed
./gradlew glTest      # GPU tests against a real OpenGL 4.6 driver
./gradlew client   # dev client
```

## Requirements

- Java 21 on 1.21.1, Java 17 on 1.20.1, Java 25 on 26.1 (auto-provisioned by the foojay toolchain
  resolver)
- Minecraft 1.21.1 / NeoForge 21.1.248, Minecraft 1.20.1 / Forge 47.4.23, or Minecraft 26.1.2 /
  NeoForge 26.1.2.109

  The same API on all three: one source tree, one set of types, and no per-version entry point. What
  differs is what the platform can carry.

  On **1.20.1**: no `.ktx2` model textures or BC7 atlas compression (`org.lwjgl:lwjgl-ktx` has no build
  for the LWJGL 3.3.1 that 1.20.1 ships), and no Iris LabPBR bridge. Both degrade rather than fail -- a
  `.ktx2` texture falls back to PNG, and an uncompressed atlas renders identically for more video
  memory.

  On **26.1**: no Iris, which has no 26.1 build to bind to. `.ktx2` decoding is available -- 26.1 ships
  LWJGL 3.4.1, which has an `lwjgl-ktx` binding where 1.20.1's 3.3.1 does not -- but **BC7 atlas
  compression has no upload path there yet**, so a model whose sheet is large enough to be compressed
  renders as the missing texture. Keep 26.1 assets under the compression threshold until that lands.
  Everything else is here, including the retained item/armour/hand renderer. The water split is the
  other exception: it is ported and it runs, but it is **switched off on 26.1**, because with it on
  every OIT fragment is composited into the wrong half -- a model inside a translucent shell draws in
  front of the shell. The two GL-state leaks that used to be blamed for this are fixed and were not it;
  see `WaterDepthPrepass.SUPPORTED` for the current lead. So 26.1 draws water and clouds correctly and
  loses only the interleave with them. Two differences a consuming mod will notice, and neither
  can be shimmed away because vanilla moved the decision:

  - An item claims a GemRender renderer in its **model JSON** (`"type": "minecraft:special"` naming a
    `gemrender:model`), not from `IClientItemExtensions.getCustomRenderer()`. The Java half --
    `GemRenderItemRenderer.register(id, renderer)` -- is the same call on every version.
  - `ArmorAppearance` is handed a `null` `LivingEntity`. Armour is chosen during 26.1's submit phase,
    which sees the wearer's render state rather than the wearer. The stack is still there.

  **26.1 runs, and models draw on it**: glTF import, the atlas, GPU skinning, palette sharing, LOD, the
  direct path's level drive and its GUI item grid are all measured on a 26.1 client. Flywheel has no
  26.1 release, so the engine is vendored into this jar and ported (`src/flywheel`, MIT, Jozufozu). Not
  everything has been seen: Flywheel's crumbling path and the BC7 atlas upload above have not been
  exercised. See `gemrender-internal/docs/MULTIVERSION.md` for the current list.
- **OpenGL 4.2**, and only for BC7 atlas compression – nothing in the shaders needs more than
  Flywheel's own 3.3 floor
- Flywheel 1.0.6, **required** and jar-in-jar'd on 1.20.1 and 1.21.1, so there is nothing to install
  alongside. Create nests it under the same coordinate, so a pack carrying both resolves them to one
  copy, and a Flywheel in `mods/` displaces both. On 26.1 there is no published Flywheel: this jar
  *provides* the engine rather than bundling it, so a Flywheel in `mods/` there is a duplicate mod id
  and will not load
- Iris is `compileOnly` and optional. `com.wf.gemrender.iris` names Iris types to register a LabPBR
  loader, and that package is entered only after a `ModList` check
- `org.lwjgl:lwjgl-ktx` for `.ktx2` model textures and for encoding stitched sheets – jar-in-jar'd,
  pinned to the LWJGL version Minecraft ships

`glTest` needs a GL 4.6 driver and skips cleanly without one.

## Source set guide

| |                                                          |
|---|----------------------------------------------------------|
| `src/main` | the mod                                                  |
| `src/test` | unit tests, and GPU tests tagged `gl`                    |
| `src/harness`, `src/bench` | the development spike and the cross-framework benchmark  |
| `docs/INTEGRATION.md` | the API guide                                            |

## Licence

GPL-3. Vendored third-party source retains its own licences.
