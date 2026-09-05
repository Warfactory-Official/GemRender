void flw_materialFragment() {
#if defined(_FLW_DEPTH_RANGE) || defined(_FLW_COLLECT_COEFFS)
    discard;
#else
    flw_fragColor.a = -log(max(1.0 - flw_fragColor.a, 1e-4));
#endif
}
