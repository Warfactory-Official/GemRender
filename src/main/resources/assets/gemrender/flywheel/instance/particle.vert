#include "gemrender:particle.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderParticle p = gemrender_particle(i.particle);
    GemRenderStyle s = gemrender_style(p.style);

    float age = flw_renderSeconds - p.spawnTime;
    float unitAge = gemrender_particleUnitAge(p, age);

    vec2 corner = flw_vertexPos.xy;

    vec3 center = i.origin + gemrender_particlePosition(p, s, age);
    float size = gemrender_particleAlive(p, age) ? gemrender_particleSize(p, s, unitAge) : 0.0;
    float near = gemrender_nearFade(center, flw_cameraPos, size) * gemrender_cullFade(center, flw_cameraPos, s.cull);
    size *= step(1e-4, near);

    float angle = p.spinPhase + s.spinRate * age;
    float cosine = cos(angle);
    float sine = sin(angle);

    vec3 right = vec3(flw_viewInverse[0]);
    vec3 up = vec3(flw_viewInverse[1]);
    vec3 spunRight = right * cosine + up * sine;
    vec3 spunUp = up * cosine - right * sine;

    flw_vertexPos = vec4(gemrender_depthPull(center, flw_cameraPos, size) + (corner.x * spunRight + corner.y * spunUp) * size, 1.0);
    // Zero = unfaced lighting: a billboard is a puff of volume, so every light reaches it from any side. A facing
    // term made a lamp light smoke only while it sat on one side of the view direction.
    flw_vertexNormal = vec3(0.0);
    vec4 color = gemrender_particleColor(p, s, unitAge);
    flw_vertexColor = vec4(color.rgb, color.a * near);
    flw_vertexOverlay = gemrender_particleOverlay(gemrender_particleHeat(s, unitAge), center, 0.5 * size);
    flw_vertexLight = gemrender_particleLight(p, s);
}
