#include "gemrender:absorbance_band.glsl"

uniform samplerBuffer _gemrender_particles;

const int GEMRENDER_STYLE_FLOATS = 24;
const int GEMRENDER_MAX_STYLES = 256;
const int GEMRENDER_PARTICLE_FLOATS = 16;
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
// Where this particle's flight runs into the world, worked out on the CPU at spawn; see
// ParticleCollision. contactAge is GemRender's NEVER (1e30) for a particle that hits nothing, which is
// what lets the arithmetic below be unconditional rather than branchy.
    float contactAge;
    float contactNormal;
    float restAge;
// 0 = none: style tint + style light. Else 1 + (rgb565 << 8 | block << 4 | sky); see ParticleLook.
    float look;
};

struct GemRenderStyle {
    float drag;
    float dragY;
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
    float fadeIn;
    float restitution;
    float friction;
    float fadeOut;
    float streak;
    float glow;
    float cull;
};

GemRenderParticle gemrender_particle(uint slot) {
    int base = GEMRENDER_PARTICLE_BASE + int(slot) * GEMRENDER_PARTICLE_TEXELS;

    vec4 a = texelFetch(_gemrender_particles, base);
    vec4 b = texelFetch(_gemrender_particles, base + 1);
    vec4 c = texelFetch(_gemrender_particles, base + 2);
    vec4 d = texelFetch(_gemrender_particles, base + 3);

    GemRenderParticle p;
    p.spawnPos = a.xyz;
    p.spawnTime = a.w;
    p.spawnVelocity = b.xyz;
    p.life = b.w;
    p.style = c.x;
    p.sizeScale = c.y;
    p.spinPhase = c.z;
    p.tintScale = c.w;
    p.contactAge = d.x;
    p.contactNormal = d.y;
    p.restAge = d.z;
    p.look = d.w;
    return p;
}

GemRenderStyle gemrender_style(float index) {
    int base = int(index) * GEMRENDER_STYLE_TEXELS;

    vec4 a = texelFetch(_gemrender_particles, base);
    vec4 b = texelFetch(_gemrender_particles, base + 1);
    vec4 c = texelFetch(_gemrender_particles, base + 2);
    vec4 d = texelFetch(_gemrender_particles, base + 3);
    vec4 e = texelFetch(_gemrender_particles, base + 4);
    vec4 f = texelFetch(_gemrender_particles, base + 5);

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
    s.dragY = d.z;
    s.fadeIn = d.w;
    s.restitution = e.x;
    s.friction = e.y;
    s.fadeOut = e.w;
    s.streak = f.x;
    s.glow = f.y;
    s.cull = f.z;
    // e.z is the style's ContactResponse, which only the CPU reads: what it decides is already baked into
    // the two ages and the life the particle carries.
    return s;
}

vec3 _gemrender_drag(in GemRenderStyle s) {
    return vec3(s.drag, s.dragY, s.drag);
}

// One leg of a flight: where something that started at pos0 with vel0 is t seconds later. A particle is one
// of these before it touches anything and a second one after it rebounds, which is the whole of what contact
// does to the closed form.
vec3 _gemrender_flightPos(vec3 pos0, vec3 vel0, in GemRenderStyle s, float t) {
    vec3 d = _gemrender_drag(s);
    vec3 g = vec3(0.0, -s.gravity, 0.0);

    vec3 safe = max(d, vec3(1e-4));
    vec3 terminal = g / safe;
    vec3 travel = (1.0 - exp(-safe * t)) / safe;

    vec3 dragged = pos0 + (vel0 - terminal) * travel + terminal * t;
    vec3 free = pos0 + vel0 * t + 0.5 * g * t * t;

    return mix(free, dragged, greaterThan(d, vec3(1e-4)));
}

vec3 _gemrender_flightVel(vec3 vel0, in GemRenderStyle s, float t) {
    vec3 d = _gemrender_drag(s);
    vec3 g = vec3(0.0, -s.gravity, 0.0);

    vec3 safe = max(d, vec3(1e-4));
    vec3 terminal = g / safe;

    vec3 dragged = (vel0 - terminal) * exp(-safe * t) + terminal;
    vec3 free = vel0 + g * t;

    return mix(free, dragged, greaterThan(d, vec3(1e-4)));
}

// The six block faces, packed as one float: the low bit is the sign, the rest is the axis.
vec3 _gemrender_normal(float encoded) {
    int face = int(encoded);
    int axis = face >> 1;
    float facing = (face & 1) == 1 ? 1.0 : -1.0;
    return vec3(axis == 0 ? facing : 0.0, axis == 1 ? facing : 0.0, axis == 2 ? facing : 0.0);
}

// The velocity coming out of a surface: what went into it returns scaled by restitution, what ran along it
// keeps friction. A velocity already leaving is only slowed: flipping it would drive the particle back
// into the block it just left.
vec3 _gemrender_reflect(vec3 velocity, float encoded, in GemRenderStyle s) {
    vec3 n = _gemrender_normal(encoded);
    float along = dot(velocity, n);
    float bounced = along < 0.0 ? -s.restitution * along : along;
    return (velocity - along * n) * s.friction + bounced * n;
}

bool gemrender_particleAlive(in GemRenderParticle p, float age) {
    return p.life > 0.0 && age >= 0.0 && age < p.life;
}

float gemrender_particleUnitAge(in GemRenderParticle p, float age) {
    return clamp(age / max(p.life, 1e-6), 0.0, 1.0);
}

// A particle that has settled keeps the velocity it arrived with rather than reporting zero: a mesh particle
// is oriented along this vector, and rubble that landed should lie the way it hit rather than snap upright
// the instant it stops.
vec3 gemrender_particleVelocity(in GemRenderParticle p, in GemRenderStyle s, float age) {
    float flight = min(age, p.contactAge);
    vec3 v = _gemrender_flightVel(p.spawnVelocity, s, flight);

    if (age <= p.contactAge || p.restAge <= p.contactAge) {
        return v;
    }

    float settled = max(0.0, min(age, p.restAge) - p.contactAge);
    return _gemrender_flightVel(_gemrender_reflect(v, p.contactNormal, s), s, settled);
}

// Zero once settled (age >= restAge); gemrender_particleVelocity keeps the landing vector for attitude.
vec3 gemrender_particleMotion(in GemRenderParticle p, in GemRenderStyle s, float age) {
    return age >= p.restAge ? vec3(0.0) : gemrender_particleVelocity(p, s, age);
}

vec3 gemrender_particlePosition(in GemRenderParticle p, in GemRenderStyle s, float age) {
    float flight = min(age, p.contactAge);
    vec3 at = _gemrender_flightPos(p.spawnPos, p.spawnVelocity, s, flight);

    if (age <= p.contactAge) {
        return at;
    }

    // Past the contact the particle is on its second leg, and past restAge it is on neither: the clamp
    // freezes it where that leg ended, which is a particle lying on the floor.
    vec3 rebound = _gemrender_reflect(_gemrender_flightVel(p.spawnVelocity, s, p.contactAge),
                                      p.contactNormal, s);
    float settled = max(0.0, min(age, p.restAge) - p.contactAge);
    return _gemrender_flightPos(at, rebound, s, settled);
}

float gemrender_particleSize(in GemRenderParticle p, in GemRenderStyle s, float unitAge) {
    return p.sizeScale * (s.size0 + s.sizeRate * unitAge);
}

bool gemrender_particleHasLook(in GemRenderParticle p) {
    return p.look > 0.5;
}

int _gemrender_look(in GemRenderParticle p) {
    return int(p.look + 0.5) - 1;
}

vec3 gemrender_particleLookColor(in GemRenderParticle p) {
    if (!gemrender_particleHasLook(p)) {
        return vec3(1.0);
    }
    int v = _gemrender_look(p);
    return vec3(float((v >> 19) & 31) / 31.0, float((v >> 13) & 63) / 63.0, float((v >> 8) & 31) / 31.0);
}

vec2 gemrender_particleLight(in GemRenderParticle p, in GemRenderStyle s) {
    if (!gemrender_particleHasLook(p)) {
        return s.light;
    }
    int v = _gemrender_look(p);
    return vec2(float((v >> 4) & 15), float(v & 15)) / 16.0;
}

// 0 until fadeOut, then 0 -> 1 by the end of life. fadeOut == 0: the whole life.
float gemrender_particleFade(in GemRenderStyle s, float unitAge) {
    return clamp((unitAge - s.fadeOut) / max(1.0 - s.fadeOut, 1e-6), 0.0, 1.0);
}

vec4 gemrender_particleColor(in GemRenderParticle p, in GemRenderStyle s, float unitAge) {
    float cool = s.coolFloor
    + (1.0 - s.coolFloor) * (1.0 - min(unitAge / max(s.coolSpan, 1e-6), 1.0));
    float alpha = s.alphaScale * pow(1.0 - gemrender_particleFade(s, unitAge), s.alphaFalloff);
    float ramp = s.fadeIn > 1e-4 ? min(unitAge / s.fadeIn, 1.0) : 1.0;

    return vec4(clamp(s.tint * gemrender_particleLookColor(p) * cool * p.tintScale, 0.0, 1.0),
                clamp(alpha * ramp, 0.0, 1.0));
}

// Emission share of alpha (ParticleStyle.glow): fades over the cool span when one is set.
float gemrender_particleHeat(in GemRenderStyle s, float unitAge) {
    float hot = s.coolFloor < 1.0 ? 1.0 - min(unitAge / max(s.coolSpan, 1e-6), 1.0) : 1.0;
    return s.glow * hot;
}

// Absorbance materials read heat from overlay.x (gemrender:absorbance.glsl); heat 0 => vanilla (0, 10).
ivec2 gemrender_particleOverlay(float heat) {
    return ivec2(int(clamp(heat, 0.0, 1.0) * 1023.0 + 0.5), 10);
}

// + absorbance band (gemrender:absorbance_band.glsl) from the sprite's world radius; without => reduced only.
ivec2 gemrender_particleOverlay(float heat, vec3 center, float radius) {
    return ivec2(int(clamp(heat, 0.0, 1.0) * 1023.0 + 0.5), gemrender_bandOverlay(center, radius));
}

// Camera inside a billboard => full-screen layer. Alpha 0 at d <= NEAR_IN x size, 1 at d >= NEAR_OUT x size.
const float GEMRENDER_NEAR_IN = 0.5;
const float GEMRENDER_NEAR_OUT = 1.5;

float gemrender_nearFade(vec3 center, vec3 eye, float size) {
    float d = distance(center, eye);
    return clamp((d - GEMRENDER_NEAR_IN * size) / max((GEMRENDER_NEAR_OUT - GEMRENDER_NEAR_IN) * size, 1e-6),
                 0.0, 1.0);
}

// Toward the eye by ~2 depth steps (24-bit, near 0.05: step = d^2 / (0.05 * 2^24)), <= size: far smoke on LOD
// ground (step ~1.2 blocks at 1 km) is otherwise cut by it.
vec3 gemrender_depthPull(vec3 center, vec3 eye, float size) {
    vec3 v = eye - center;
    float d2 = dot(v, v);
    return d2 > 1e-6 ? center + v * (min(d2 * 2.4e-6, size) * inversesqrt(d2)) : center;
}

// ParticleBuffer.setStyleCull: 0 at d <= cull, 1 at d >= cull + RAMP (= ParticleBuffer.CULL_RAMP); cull 0 = off.
const float GEMRENDER_CULL_RAMP = 2.0;

float gemrender_cullFade(vec3 center, vec3 eye, float cull) {
    return cull > 0.0 ? clamp((distance(center, eye) - cull) / GEMRENDER_CULL_RAMP, 0.0, 1.0) : 1.0;
}

// Right-handed (t, b, n), t x b = n; rolled about n.
mat3 gemrender_planeBasis(vec3 n, float roll) {
    vec3 reference = abs(n.y) > 0.999 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 t = normalize(cross(reference, n));
    vec3 b = cross(n, t);
    float c = cos(roll);
    float si = sin(roll);
    return mat3(t * c + b * si, b * c - t * si, n);
}

// DECAL: spawnVelocity carries the surface normal; the particle never moves.
vec3 gemrender_decalNormal(in GemRenderParticle p) {
    float len = length(p.spawnVelocity);
    return len > 1e-6 ? p.spawnVelocity / len : vec3(0.0, 1.0, 0.0);
}

const float GEMRENDER_DECAL_LIFT = 1.0 / 512.0;

vec3 gemrender_decalCorner(in GemRenderParticle p, vec2 corner, float size) {
    mat3 basis = gemrender_planeBasis(gemrender_decalNormal(p), p.spinPhase);
    return p.spawnPos + basis * vec3(corner * size, GEMRENDER_DECAL_LIFT);
}

// STREAK: y along motion, head at the particle (corner.y = 0.5), tail streak * |v| behind; x across, facing eye.
// Feed gemrender_particleMotion: settled => width x width dot.
vec3 gemrender_streakCorner(vec3 center, vec3 velocity, vec3 eye, vec3 fallbackRight, vec2 corner, float width,
                            float streak) {
    float speed = length(velocity);
    vec3 dir = speed > 1e-6 ? velocity / speed : vec3(0.0, 1.0, 0.0);
    float len = max(streak * speed, width);
    vec3 across = cross(dir, eye - center);
    float acrossLen = length(across);
    vec3 side = acrossLen > 1e-6 ? across / acrossLen : fallbackRight;
    return center + side * (corner.x * width) + dir * ((corner.y - 0.5) * len);
}

float gemrender_streakLength(vec3 velocity, float width, float streak) {
    return max(streak * length(velocity), width);
}

// BODY: tumbles about a horizontal axis (yawed by spinPhase) at spinRate; comes to rest lying down (+Y
// horizontal) and eases into that attitude over the rebound leg.
const float GEMRENDER_PI = 3.14159265;

float _gemrender_bodyAngle(in GemRenderParticle p, in GemRenderStyle s, float age) {
    float free = 2.0 * p.spinPhase + s.spinRate * min(age, p.restAge);
    if (age <= p.contactAge) {
        return free;
    }
    float rest = GEMRENDER_PI * (floor((2.0 * p.spinPhase + s.spinRate * p.restAge) / GEMRENDER_PI) + 0.5);
    float span = p.restAge - p.contactAge;
    float w = span > 1e-6 ? clamp((age - p.contactAge) / span, 0.0, 1.0) : 1.0;
    return mix(free, rest, w * w);
}

mat3 gemrender_bodyBasis(in GemRenderParticle p, in GemRenderStyle s, float age) {
    vec2 ground = p.spawnVelocity.xz;
    float groundLen = length(ground);
    vec2 heading = groundLen > 1e-6 ? ground / groundLen : vec2(0.0, 1.0);
    float cy = cos(p.spinPhase);
    float sy = sin(p.spinPhase);
    vec3 forward = vec3(heading.x * cy - heading.y * sy, 0.0, heading.x * sy + heading.y * cy);
    vec3 up = vec3(0.0, 1.0, 0.0);
    vec3 axis = cross(up, forward);

    float angle = _gemrender_bodyAngle(p, s, age);
    vec3 y = up * cos(angle) + forward * sin(angle);
    return mat3(axis, y, cross(axis, y));
}

// BODY shrinks through the fade window instead of blending: its meshes are usually opaque.
float gemrender_bodyScale(in GemRenderStyle s, float unitAge) {
    return 1.0 - gemrender_particleFade(s, unitAge);
}
