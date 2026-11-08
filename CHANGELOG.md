# Changelog

## Unreleased

- Light shafts (1.21.1): every shaded spot light, nearest eight, draws a visible beam in the air. After the level, one
  scissored fullscreen triangle per light clips each view ray against the cone analytically, stops it at scene depth and
  marches a few equi-angular steps through the light's own traced shadow tile (walls and pillars cut the shaft; no extra
  occlusion tracing). Rain thickens it, daylight under open sky fades it. `-Dgemrender.shafts=false` turns it off;
  `gemrender.shaftMax`, `shaftSteps`, `shaftDensity`, `shaftPhase`, `shaftDust` tune it. Other versions: not ported yet.

## 0.1.20

- Fix: a resource pack or mod that replaces a vanilla core shader (for example Stellarity's armor shader, which puts the
  `{` of `main()` on its own line) no longer fails the shader reload and leaves the game stuck on the loading screen.
  The dynamic-light patch accepts any spacing around `main()`, and a replaced shader it still cannot patch is left
  unlit by GemRender with one warning in the log.
- Direct path, level pass: opaque parts draw after entities and block entities, blended parts after weather, so clouds
  stay behind them.
- API: `GemRenderGltfModel.slotFrame(slot)` and `DirectRenderer.submitSlots(...)` draw a glTF model's slot meshes at
  an already posed palette.
