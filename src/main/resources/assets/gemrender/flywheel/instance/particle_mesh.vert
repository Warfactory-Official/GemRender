#include "gemrender:particle.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderParticle p = gemrender_particle(i.particle);
    GemRenderStyle s = gemrender_style(p.style);

    float age = flw_renderSeconds - p.spawnTime;
    float unitAge = gemrender_particleUnitAge(p, age);

    vec3 local = flw_vertexPos.xyz;

    vec3 center = i.origin + gemrender_particlePosition(p, s, age);
    float size = gemrender_particleAlive(p, age) ? gemrender_particleSize(p, s, unitAge) : 0.0;

    vec3 velocity = gemrender_particleVelocity(p, s, age);
    vec3 forward = dot(velocity, velocity) > 1e-8 ? normalize(velocity) : vec3(0.0, 1.0, 0.0);
    vec3 reference = abs(forward.y) > 0.999 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 side = normalize(cross(reference, forward));
    vec3 up = cross(forward, side);

    float angle = p.spinPhase + s.spinRate * age;
    float cosine = cos(angle);
    float sine = sin(angle);

    mat3 basis = mat3(side * cosine + up * sine, forward, up * cosine - side * sine);

    flw_vertexPos = vec4(center + basis * (local * size), 1.0);
    flw_vertexNormal = basis * flw_vertexNormal;
    flw_vertexColor = gemrender_particleColor(p, s, unitAge);
    flw_vertexOverlay = ivec2(0, 10);
    flw_vertexLight = s.light;
}
