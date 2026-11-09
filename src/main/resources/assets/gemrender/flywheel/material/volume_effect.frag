#include "gemrender:volume_effect.glsl"
#include "gemrender:absorbance.glsl"
#include "gemrender:absorbance_band.glsl"

void flw_materialFragment() {
    #if defined(_FLW_COLLECT_COEFFS) && !defined(GEMRENDER_CHUNK_OIT)
    discard;
    #else
    GemRenderVolume v = gemrender_volume(uint(flw_vertexOverlay.x));
    if (!gemrender_volumeAlive(v)) discard;

    vec3 center = flw_vertexColor.xyz;
    vec3 origin = flw_cameraPos;
    vec3 direction = normalize(flw_vertexPos.xyz - origin);
    vec2 span = gemrender_volumeSpan(v, center, origin, direction);
    float far = min(span.y, gemrender_sceneDistance(direction));
    if (far <= span.x) discard;

    vec3 emission;
    float firstHit;
    vec4 marched = gemrender_effectMarch(v, center, origin, direction, span.x, far,
            gemrender_volumeJitter(), emission, firstHit);
    if (marched.a < 0.004) discard;

    // The coverage quad is at the near plane; Fabulous and depth tests need the actual effect depth.
    vec4 clip = flw_viewProjection * vec4(origin + direction * firstHit, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    gemrender_effectViewDepth = -(flw_view * vec4(origin + direction * firstHit, 1.0)).z;

    #if defined(_FLW_EVALUATE) && !defined(GEMRENDER_CHUNK_OIT)
    float share = gemrender_bandShare(flw_fragOverlay.y);
    if (share <= 0.0) discard;
    // The composite applies total extinction; the march has already applied this volume's share.
    vec3 sourceEmission = min(emission / max(1.0 - marched.a, 1e-4), vec3(60000.0));
    gemrender_emission = vec4(sourceEmission * share, 0.0);
    flw_fragColor = vec4(marched.rgb, -log(max(1.0 - marched.a, 1e-4)) * share);
    #else
    flw_fragColor = vec4(marched.rgb + emission / max(marched.a, 1e-4), marched.a);
    #endif
    #endif
}
