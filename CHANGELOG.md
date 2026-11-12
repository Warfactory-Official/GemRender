# Changelog

## 0.1.22

- 26.1 runs on upstream Flywheel 1.0.7-mc26.1, nested like on 1.20.1 and 1.21.1, instead of GemRender's own fork.
  NeoForge 26.1.2.126 or newer is required (upstream Flywheel's floor). A Flywheel in `mods/` (Create's, for example)
  no longer clashes with GemRender's.
- Fix (26.1): Flywheel sees Iris shader packs. Its backends stand down under a pack, as on 1.20.1 and 1.21.1, instead of
  drawing outside the pack's buffers where the pack painted over them. With Colorwheel's 26.1 build installed,
  instances draw through the pack, shadows included.
- Under an Iris or Oculus shader pack a toast says GemRender's advanced lighting is disabled (the pack replaces the
  shaders the lights are drawn in). Without Colorwheel (or Iris & Oculus Flywheel Compat) a second toast and a chat line
  say instancing is off and models draw on the CPU. Shown on each world join and each time a pack is switched on.
- Fabric: `fabric-api` is now a declared dependency on every version; GemRender's Fabric code already needed it.
- 26.1: Golem Core before 1.0.6 is refused at launch with a message (it calls the old fork's Flywheel API and would
  crash on upstream Flywheel). Update Golem Core, or stay on GemRender 0.1.21.
- Light shafts (1.21.1): every shaded spot light, nearest eight, draws a visible beam in the air. After the level, one
  scissored fullscreen triangle per light clips each view ray against the cone analytically, stops it at scene depth and
  marches a few equi-angular steps through the light's own traced shadow tile (walls and pillars cut the shaft; no extra
  occlusion tracing). Rain thickens it, daylight under open sky fades it. `-Dgemrender.shafts=false` turns it off;
  `gemrender.shaftMax`, `shaftSteps`, `shaftDensity`, `shaftPhase`, `shaftDust` tune it. Other versions: not ported yet.

## 0.1.21

- Fix: a Voxy fork whose LOD shaders the dynamic-light patch cannot fit (Neo-Voxy 0.5.0) no longer crashes the game when
  joining a world. Its LOD terrain is left unlit by GemRender with one warning in the log.

## 0.1.20

- Fix: a resource pack or mod that replaces a vanilla core shader (for example Stellarity's armor shader, which puts the
  `{` of `main()` on its own line) no longer fails the shader reload and leaves the game stuck on the loading screen.
  The dynamic-light patch accepts any spacing around `main()`, and a replaced shader it still cannot patch is left
  unlit by GemRender with one warning in the log.
- Direct path, level pass: opaque parts draw after entities and block entities, blended parts after weather, so clouds
  stay behind them.
- API: `GemRenderGltfModel.slotFrame(slot)` and `DirectRenderer.submitSlots(...)` draw a glTF model's slot meshes at
  an already posed palette.
