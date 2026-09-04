#include "gemrender:particle.glsl"

void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    GemRenderParticle p = gemrender_particle(i.particle);
    GemRenderStyle s = gemrender_style(p.style);

    float age = flw_renderSeconds - p.spawnTime;

    if (!gemrender_particleAlive(p, age)) {
        radius = -1e18;
        return;
    }

    float size = gemrender_particleSize(p, s, gemrender_particleUnitAge(p, age));

    radius = (length(center) + radius) * size;
    center = i.origin + gemrender_particlePosition(p, s, age);
}
