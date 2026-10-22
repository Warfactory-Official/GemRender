#include "gemrender:rope.glsl"
#include "gemrender:absorbance_band.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderRope r;
    r.a = i.a;
    r.b = i.b;
    r.sag = i.shape.x;
    r.radius = i.shape.y;
    r.uvScale = i.shape.z;
    r.twist = i.shape.w;
    r.sway = i.sway;

    float t = flw_vertexPos.x;
    vec2 ring = flw_vertexPos.yz;
    float capSign = flw_vertexNormal.y;

    vec3 tangent = gemrender_ropeTangent(r, t);
    vec3 forward;
    vec3 n1;
    vec3 n2;
    gemrender_ropeFrame(tangent, forward, n1, n2);

    float angle = r.twist * GEMRENDER_TAU * t;
    float c = cos(angle);
    float s = sin(angle);
    vec2 spun = vec2(ring.x * c - ring.y * s, ring.x * s + ring.y * c);

    vec3 radial = n1 * spun.x + n2 * spun.y;
    vec3 center = gemrender_ropePoint(r, t) + gemrender_ropeSway(r, t, flw_renderSeconds, n1, n2);

    flw_vertexPos = vec4(center + radial * r.radius, 1.0);

    // A cap faces along the rope; every other vertex faces out of it. The rim of a cap carries a
    // radial direction too, so the two cannot be told apart from the geometry alone.
    flw_vertexNormal = abs(capSign) > 0.5 ? forward * sign(capSign) : normalize(radial);

    flw_vertexColor = i.color;
    // Split frame only: other passes draw opaque ropes, whose overlay is vanilla's.
    // Ends + middle agree => instance-constant share, collapsible like a sprite; else per vertex.
    if (gemrender_bandSplit()) {
        int ends = gemrender_bandShareQ(r.a, r.radius);
        bool even = ends == gemrender_bandShareQ(r.b, r.radius)
                && ends == gemrender_bandShareQ(gemrender_ropePoint(r, 0.5), r.radius);
        flw_vertexOverlay = ivec2(0, GEMRENDER_BAND_BASE + (even ? ends
                : gemrender_bandShareQ(center, r.radius) + GEMRENDER_BAND_VARYING));
    } else {
        flw_vertexOverlay = ivec2(0, 10);
    }

    // v runs along the rope, so a texture tiles by length rather than stretching with it.
    flw_vertexTexCoord = vec2(flw_vertexTexCoord.x, flw_vertexTexCoord.y * r.uvScale);

    flw_vertexLight = mix(vec2(i.lightA), vec2(i.lightB), t) / 256.0;
}
