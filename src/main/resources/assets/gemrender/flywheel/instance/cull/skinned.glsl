#include "flywheel:util/matrix.glsl"

void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    center = i.boneSphere.xyz;
    radius = i.boneSphere.w;

    transformBoundingSphere(i.pose, center, radius);
}
