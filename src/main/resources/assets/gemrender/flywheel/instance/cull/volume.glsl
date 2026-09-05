#include "gemrender:volume.glsl"

void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    GemRenderVolume v = gemrender_volume(i.volume);

    if (!gemrender_volumeAlive(v)) {
        radius = -1e18;
        return;
    }

    center = i.center;
    radius = gemrender_volumeRadius(v);
}
