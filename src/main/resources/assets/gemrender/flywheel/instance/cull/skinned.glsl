#include "flywheel:util/matrix.glsl"

// Transforms a GemRender instance's bounding sphere into world space for GPU culling.
void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    center = i.boneSphere.xyz;
    radius = i.boneSphere.w;

    transformBoundingSphere(i.pose, center, radius);
}
