uniform samplerBuffer _gemrender_particles;

const int GEMRENDER_STYLE_FLOATS = 16;
const int GEMRENDER_MAX_STYLES = 64;
const int GEMRENDER_PARTICLE_FLOATS = 12;
const int GEMRENDER_STYLE_TEXELS = GEMRENDER_STYLE_FLOATS / 4;
const int GEMRENDER_PARTICLE_TEXELS = GEMRENDER_PARTICLE_FLOATS / 4;
const int GEMRENDER_PARTICLE_BASE = GEMRENDER_STYLE_TEXELS * GEMRENDER_MAX_STYLES;

struct GemRenderParticle {
    vec3 spawnPos;
    float spawnTime;
    vec3 spawnVelocity;
    float life;
    float style;
    float sizeScale;
    float spinPhase;
    float tintScale;
};

struct GemRenderStyle {
    float drag;
    float gravity;
    float size0;
    float sizeRate;
    vec3 tint;
    float alphaScale;
    float alphaFalloff;
    float coolFloor;
    float coolSpan;
    float spinRate;
    vec2 light;
};

GemRenderParticle gemrender_particle(uint slot) {
    int base = GEMRENDER_PARTICLE_BASE + int(slot) * GEMRENDER_PARTICLE_TEXELS;

    vec4 a = texelFetch(_gemrender_particles, base);
    vec4 b = texelFetch(_gemrender_particles, base + 1);
    vec4 c = texelFetch(_gemrender_particles, base + 2);

    GemRenderParticle p;
    p.spawnPos = a.xyz;
    p.spawnTime = a.w;
    p.spawnVelocity = b.xyz;
    p.life = b.w;
    p.style = c.x;
    p.sizeScale = c.y;
    p.spinPhase = c.z;
    p.tintScale = c.w;
    return p;
}

GemRenderStyle gemrender_style(float index) {
    int base = int(index) * GEMRENDER_STYLE_TEXELS;

    vec4 a = texelFetch(_gemrender_particles, base);
    vec4 b = texelFetch(_gemrender_particles, base + 1);
    vec4 c = texelFetch(_gemrender_particles, base + 2);
    vec4 d = texelFetch(_gemrender_particles, base + 3);

    GemRenderStyle s;
    s.drag = a.x;
    s.gravity = a.y;
    s.size0 = a.z;
    s.sizeRate = a.w;
    s.tint = b.xyz;
    s.alphaScale = b.w;
    s.alphaFalloff = c.x;
    s.coolFloor = c.y;
    s.coolSpan = c.z;
    s.spinRate = c.w;
    s.light = d.xy;
    return s;
}

bool gemrender_particleAlive(in GemRenderParticle p, float age) {
    return p.life > 0.0 && age >= 0.0 && age < p.life;
}

float gemrender_particleUnitAge(in GemRenderParticle p, float age) {
    return clamp(age / max(p.life, 1e-6), 0.0, 1.0);
}

vec3 gemrender_particleVelocity(in GemRenderParticle p, in GemRenderStyle s, float age) {
    vec3 g = vec3(0.0, -s.gravity, 0.0);

    if (s.drag > 1e-4) {
        vec3 terminal = g / s.drag;
        return (p.spawnVelocity - terminal) * exp(-s.drag * age) + terminal;
    }

    return p.spawnVelocity + g * age;
}

vec3 gemrender_particlePosition(in GemRenderParticle p, in GemRenderStyle s, float age) {
    vec3 g = vec3(0.0, -s.gravity, 0.0);

    if (s.drag > 1e-4) {
        vec3 terminal = g / s.drag;
        return p.spawnPos
                + (p.spawnVelocity - terminal) * ((1.0 - exp(-s.drag * age)) / s.drag)
                + terminal * age;
    }

    return p.spawnPos + p.spawnVelocity * age + 0.5 * g * age * age;
}

float gemrender_particleSize(in GemRenderParticle p, in GemRenderStyle s, float unitAge) {
    return p.sizeScale * (s.size0 + s.sizeRate * unitAge);
}

vec4 gemrender_particleColor(in GemRenderParticle p, in GemRenderStyle s, float unitAge) {
    float cool = s.coolFloor
            + (1.0 - s.coolFloor) * (1.0 - min(unitAge / max(s.coolSpan, 1e-6), 1.0));
    float alpha = s.alphaScale * pow(1.0 - unitAge, s.alphaFalloff);

    return vec4(clamp(s.tint * cool * p.tintScale, 0.0, 1.0), clamp(alpha, 0.0, 1.0));
}
