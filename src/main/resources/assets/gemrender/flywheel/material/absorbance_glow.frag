#include "gemrender:absorbance.glsl"
#include "gemrender:absorbance_band.glsl"

// heat h: emission rgb a h; absorbing share a (1 - h) -> tau. Depth range: as absorbance.frag.
void flw_materialFragment() {
    #ifdef _FLW_COLLECT_COEFFS
    discard;
    #else
    vec4 c = flw_fragColor;
    if (c.a < 1e-3) {
        discard;
    }
    float heat = gemrender_fragHeat();
    float share = gemrender_bandShare(flw_fragOverlay.y);
    if (share <= 0.0) {
        discard;
    }
    #ifdef _FLW_EVALUATE
    gemrender_emission = vec4(c.rgb * (c.a * heat * share), 0.0);
    #endif
    flw_fragColor.a = -log(max(1.0 - c.a * (1.0 - heat), 1e-4)) * share;
    #endif
}
