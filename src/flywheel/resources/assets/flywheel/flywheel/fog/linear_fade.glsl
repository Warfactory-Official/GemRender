// 26.1's glint.fsh: the colour fades out with the total fog value, alpha is kept.
float linearFadeValue(float distance, float fogStart, float fogEnd) {
    if (distance <= fogStart) {
        return 0.0;
    } else if (distance >= fogEnd) {
        return 1.0;
    }

    return (distance - fogStart) / (fogEnd - fogStart);
}

float totalFadeValue() {
    return max(linearFadeValue(flw_distance, flw_fogRange.x, flw_fogRange.y),
            linearFadeValue(flw_cylindricalDistance, flw_fogRenderDistanceRange.x, flw_fogRenderDistanceRange.y));
}

vec4 flw_fogFilter(vec4 color) {
    return vec4(color.rgb * (1.0 - totalFadeValue()), color.a);
}
