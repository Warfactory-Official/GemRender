# Changelog

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
