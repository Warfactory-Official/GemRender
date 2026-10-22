#ifndef GEMRENDER_ABSORBANCE_BAND
#define GEMRENDER_ABSORBANCE_BAND

// Absorbance band texel: (pass, factor, lo, hi). pass 0 = whole sprite, 1 = reduced share, 2 = full-res share.
#ifndef GEMRENDER_CHUNK_OIT
uniform sampler2D _gemrender_absorbanceBand;
#endif

// overlay.y = BASE + q (+ VARYING): full-res share q / 1023. Below BASE (0, vanilla 10) => reduced only.
// VARYING: share differs across the primitive => never collapsed, zero-share fragments discarded.
const int GEMRENDER_BAND_BASE = 1024;
const int GEMRENDER_BAND_VARYING = 1024;

vec4 _gemrender_band() {
    #ifdef GEMRENDER_CHUNK_OIT
    return vec4(0.0);
    #else
    return texelFetch(_gemrender_absorbanceBand, ivec2(0), 0);
    #endif
}

// Reduced or full-res pass of a split frame.
bool gemrender_bandSplit() {
    return _gemrender_band().x > 0.5;
}

// Quantized full-res share: 1023 at projected radius <= lo reduced texels, 0 at >= hi; 0 outside a split frame.
int gemrender_bandShareQ(vec3 center, float radius) {
    vec4 band = _gemrender_band();
    if (band.x < 0.5) {
        return 0;
    }
    float z = max(-(flw_view * vec4(center, 1.0)).z, 1e-3);
    float texels = radius * flw_projection[1][1] * 0.5 * flw_viewportSize.y / (z * band.y);
    return int((1.0 - smoothstep(band.z, band.w, texels)) * 1023.0 + 0.5);
}

// Share constant over the primitive.
int gemrender_bandOverlay(vec3 center, float radius) {
    return GEMRENDER_BAND_BASE + gemrender_bandShareQ(center, radius);
}

bool gemrender_bandCollapsible(int overlayY) {
    return overlayY < GEMRENDER_BAND_BASE + GEMRENDER_BAND_VARYING;
}

// This pass's share of tau / emission.
float gemrender_bandShare(int overlayY) {
    float pass = _gemrender_band().x;
    if (pass < 0.5) {
        return 1.0;
    }
    float fine = overlayY >= GEMRENDER_BAND_BASE
            ? float((overlayY - GEMRENDER_BAND_BASE) % GEMRENDER_BAND_VARYING) / 1023.0 : 0.0;
    return pass < 1.5 ? 1.0 - fine : fine;
}

#endif
