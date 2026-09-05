#include "gemrender:volume.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderVolume v = gemrender_volume(i.volume);

    vec2 low = vec2(-2.0);
    vec2 high = vec2(-2.0);

    if (gemrender_volumeAlive(v)) {
        low = vec2(1e9);
        high = vec2(-1e9);

        for (int c = 0; c < 8; c++) {
            vec3 octant = vec3((c & 1) != 0 ? 1.0 : -1.0,
                    (c & 2) != 0 ? 1.0 : -1.0,
                    (c & 4) != 0 ? 1.0 : -1.0);

            vec4 clip = flw_viewProjection * vec4(i.center + v.extent * octant, 1.0);

            if (clip.w <= 1e-3) {
                low = vec2(-1.0);
                high = vec2(1.0);
                break;
            }

            vec2 ndc = clip.xy / clip.w;
            low = min(low, ndc);
            high = max(high, ndc);
        }

        low = max(low, vec2(-1.0));
        high = min(high, vec2(1.0));

        if (any(greaterThanEqual(low, high))) {
            low = vec2(-2.0);
            high = vec2(-2.0);
        }
    }

    vec2 ndc = mix(low, high, flw_vertexPos.xy + 0.5);
    vec4 near = flw_viewProjectionInverse * vec4(ndc, -1.0 + 1e-4, 1.0);

    flw_vertexPos = vec4(near.xyz / near.w, 1.0);
    flw_vertexNormal = -vec3(flw_viewInverse[2]);
    flw_vertexColor = vec4(i.center, 1.0);
    flw_vertexOverlay = ivec2(int(i.volume), 0);
    flw_vertexLight = v.light;
}
