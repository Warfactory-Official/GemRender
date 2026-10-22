#include "gemrender:particle.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderParticle p = gemrender_particle(i.particle);
    GemRenderStyle s = gemrender_style(p.style);

    float age = flw_renderSeconds - p.spawnTime;
    float unitAge = gemrender_particleUnitAge(p, age);
    float size = gemrender_particleAlive(p, age) ? gemrender_particleSize(p, s, unitAge) : 0.0;

    vec3 center = i.origin + gemrender_particlePosition(p, s, age);
    vec3 velocity = gemrender_particleMotion(p, s, age);

    flw_vertexPos = vec4(gemrender_streakCorner(center, velocity, flw_cameraPos, vec3(flw_viewInverse[0]),
                                                flw_vertexPos.xy, size, s.streak), 1.0);
    flw_vertexNormal = -vec3(flw_viewInverse[2]);
    flw_vertexColor = gemrender_particleColor(p, s, unitAge);
    flw_vertexOverlay = gemrender_particleOverlay(gemrender_particleHeat(s, unitAge), center, 0.5 * size);
    flw_vertexLight = gemrender_particleLight(p, s);
}
