#include "gemrender:particle.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderParticle p = gemrender_particle(i.particle);
    GemRenderStyle s = gemrender_style(p.style);

    float age = flw_renderSeconds - p.spawnTime;
    float unitAge = gemrender_particleUnitAge(p, age);

    vec2 corner = flw_vertexPos.xy;

    vec3 center = i.origin + gemrender_particlePosition(p, s, age);
    float size = gemrender_particleAlive(p, age) ? gemrender_particleSize(p, s, unitAge) : 0.0;

    float angle = p.spinPhase + s.spinRate * age;
    float cosine = cos(angle);
    float sine = sin(angle);

    vec3 right = vec3(flw_viewInverse[0]);
    vec3 up = vec3(flw_viewInverse[1]);
    vec3 spunRight = right * cosine + up * sine;
    vec3 spunUp = up * cosine - right * sine;

    flw_vertexPos = vec4(center + (corner.x * spunRight + corner.y * spunUp) * size, 1.0);
    flw_vertexNormal = -vec3(flw_viewInverse[2]);
    flw_vertexColor = gemrender_particleColor(p, s, unitAge);
    flw_vertexOverlay = ivec2(0, 10);
    flw_vertexLight = s.light;
}
