// CrankShaft's ordered additive producer supplies attenuation by the OIT layers in front.
void flw_materialFragment() {
    float heat = clamp(float(flw_vertexOverlay.x) / 1023.0, 0.0, 1.0);
    flw_fragColor.a *= heat;
    if (flw_fragColor.a < 1e-3) {
        discard;
    }
}
