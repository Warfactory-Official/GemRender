// The absorbing part of a cooling glow uses the same ordinary OIT stack as smoke and terrain.
void flw_materialFragment() {
    float heat = clamp(float(flw_vertexOverlay.x) / 1023.0, 0.0, 1.0);
    flw_fragColor.a *= 1.0 - heat;
    if (flw_fragColor.a < 1e-3) {
        discard;
    }
}
