uniform samplerBuffer _gemrender_volumes;

const int GEMRENDER_VOLUME_FLOATS = 24;
const int GEMRENDER_VOLUME_TEXELS = GEMRENDER_VOLUME_FLOATS / 4;

struct GemRenderVolume {
    vec3 extent;
    float density;
    vec3 tint;
    float fade;
    float detail;
    float edge;
    float rise;
    float seed;
    vec2 light;
    float phase;
    float ambient;
    float steps;
    float sunSteps;
    float sunDensity;
    float sunStrength;
    vec3 fieldOrigin;
    float fieldScale;
};

GemRenderVolume gemrender_volume(uint slot) {
    int base = int(slot) * GEMRENDER_VOLUME_TEXELS;

    vec4 a = texelFetch(_gemrender_volumes, base);
    vec4 b = texelFetch(_gemrender_volumes, base + 1);
    vec4 c = texelFetch(_gemrender_volumes, base + 2);
    vec4 d = texelFetch(_gemrender_volumes, base + 3);
    vec4 e = texelFetch(_gemrender_volumes, base + 4);
    vec4 f = texelFetch(_gemrender_volumes, base + 5);

    GemRenderVolume v;
    v.extent = a.xyz;
    v.density = a.w;
    v.tint = b.xyz;
    v.fade = b.w;
    v.detail = c.x;
    v.edge = c.y;
    v.rise = c.z;
    v.seed = c.w;
    v.light = d.xy;
    v.phase = d.z;
    v.ambient = d.w;
    v.steps = e.x;
    v.sunSteps = e.y;
    v.sunDensity = e.z;
    v.sunStrength = e.w;
    v.fieldOrigin = f.xyz;
    v.fieldScale = f.w;
    return v;
}

bool gemrender_volumeAlive(in GemRenderVolume v) {
    return v.fade > 1e-3 && v.density > 1e-5 && v.extent.x > 0.0 && v.steps >= 1.0;
}

float gemrender_volumeRadius(in GemRenderVolume v) {
    return length(v.extent);
}
