// 26.1's fog.glsl: a linear ramp over the spherical distance, or over the cylindrical one near the
// render distance, whichever is thicker.
float linearFogValue(float distance, float fogStart, float fogEnd) {
    if (distance <= fogStart) {
        return 0.0;
    } else if (distance >= fogEnd) {
        return 1.0;
    }

    return (distance - fogStart) / (fogEnd - fogStart);
}

float totalFogValue() {
    return max(linearFogValue(flw_distance, flw_fogRange.x, flw_fogRange.y),
            linearFogValue(flw_cylindricalDistance, flw_fogRenderDistanceRange.x, flw_fogRenderDistanceRange.y));
}

vec4 flw_fogFilter(vec4 color) {
    return vec4(mix(color.rgb, flw_fogColor.rgb, totalFogValue() * flw_fogColor.a), color.a);
}
