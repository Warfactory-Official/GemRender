#include "gemrender:volume_march.glsl"

void flw_materialFragment() {
#if defined(_FLW_DEPTH_RANGE) || defined(_FLW_COLLECT_COEFFS)
    discard;
#else
    GemRenderVolume v = gemrender_volume(uint(flw_vertexOverlay.x));

    if (!gemrender_volumeAlive(v)) {
        discard;
    }

    vec3 center = flw_vertexColor.xyz;
    vec3 origin = flw_cameraPos;
    vec3 direction = normalize(flw_vertexPos.xyz - origin);

    vec2 span = gemrender_volumeSpan(v, center, origin, direction);
    float far = min(span.y, gemrender_sceneDistance(direction));

    if (far <= span.x) {
        discard;
    }

    vec4 marched = gemrender_volumeMarch(v, center, origin, direction, span.x, far,
            gemrender_volumeJitter());

    if (marched.a < 0.004) {
        discard;
    }

#ifdef _FLW_EVALUATE
    flw_fragColor = vec4(marched.rgb, -log(max(1.0 - marched.a, 1e-4)));
#else
    flw_fragColor = marched;
#endif
#endif
}
