#include "gemrender:absorbance_band.glsl"

// Takes part in depth range: nearest depth for the Fabulous composite (skipped when Absorbance#skips).
void flw_materialFragment() {
    #ifdef _FLW_COLLECT_COEFFS
    discard;
    #else
    if (flw_fragColor.a < 1e-3) {
        discard;
    }
    float share = gemrender_bandShare(flw_fragOverlay.y);
    if (share <= 0.0) {
        discard;
    }
    flw_fragColor.a = -log(max(1.0 - flw_fragColor.a, 1e-4)) * share;
    #endif
}
