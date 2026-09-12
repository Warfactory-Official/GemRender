#include "flywheel:internal/diffuse.glsl"

const float GEMRENDER_BANDS = 3.0;
const float GEMRENDER_BAND = 1.0 / GEMRENDER_BANDS;

const float GEMRENDER_MIN_ROUGHNESS = 0.045;

const vec3 GEMRENDER_DIELECTRIC_F0 = vec3(0.04);

const float GEMRENDER_SPECULAR = 0.35;

const float GEMRENDER_MAX_LOBE = 6.0;

mat3 gemrender_cotangentFrame(vec3 normal, vec3 position, vec2 uv) {
    vec3 dpdx = dFdx(position);
    vec3 dpdy = dFdy(position);
    vec2 duvdx = dFdx(uv);
    vec2 duvdy = dFdy(uv);

    vec3 dpdyPerp = cross(dpdy, normal);
    vec3 dpdxPerp = cross(normal, dpdx);

    vec3 tangent = dpdyPerp * duvdx.x + dpdxPerp * duvdy.x;
    vec3 bitangent = dpdyPerp * duvdx.y + dpdxPerp * duvdy.y;

    float scale = inversesqrt(max(1e-8, max(dot(tangent, tangent), dot(bitangent, bitangent))));
    return mat3(tangent * scale, bitangent * scale, normal);
}

float gemrender_lobe(vec3 normal, vec3 halfway, float shininess) {
    return min(GEMRENDER_MAX_LOBE, pow(max(0.0, dot(normal, halfway)), shininess) * (shininess + 8.0) / 8.0);
}

void flw_materialFragment() {
    vec2 uv = flw_vertexTexCoord;

    vec4 surface = texture(flw_diffuseTex, vec2(uv.x, uv.y + GEMRENDER_BAND));
    vec3 emissive = texture(flw_diffuseTex, vec2(uv.x, uv.y + 2.0 * GEMRENDER_BAND)).rgb;

    vec2 tangentXy = surface.rg * 2.0 - 1.0;
    vec3 tangentNormal = vec3(tangentXy, sqrt(max(0.0, 1.0 - dot(tangentXy, tangentXy))));

    vec3 geometric = normalize(flw_vertexNormal);
    vec3 normal = normalize(gemrender_cotangentFrame(geometric, flw_vertexPos.xyz, uv) * tangentNormal);

    float roughness = max(GEMRENDER_MIN_ROUGHNESS, surface.b);
    float metallic = surface.a;

    vec3 albedo = flw_fragColor.rgb;

    vec3 diffuseColor = albedo * (1.0 - metallic);
    vec3 specularColor = mix(GEMRENDER_DIELECTRIC_F0, albedo, metallic);

    float shade = diffuseFromLightDirections(normal);

    float shininess = exp2(12.0 * (1.0 - roughness));

    vec3 view = normalize(flw_cameraPos - flw_vertexPos.xyz);
    float highlight = gemrender_lobe(normal, normalize(flw_light0Direction + view), shininess)
    + gemrender_lobe(normal, normalize(flw_light1Direction + view), shininess);

    flw_fragColor.rgb = (diffuseColor + specularColor) * shade
    + specularColor * highlight * GEMRENDER_SPECULAR * (1.0 - roughness)
    + emissive;

    flw_fragLight.x = max(flw_fragLight.x, max(emissive.r, max(emissive.g, emissive.b)));
}
