// GemRender: glTF's metallic-roughness material, as far as Minecraft's lighting can carry it.
//
// A Flywheel *material* fragment shader: it runs inside Flywheel's fragment main, after the base colour
// is in flw_fragColor and before the lightmap, fog and OIT accumulation. It reads one texture, the same
// flw_diffuseTex Flywheel already bound, whose sheet is three equal horizontal bands; see
// com.wf.gemrender.texture.SurfaceBake. That is what lets this file need no new binding, no second mixin
// and no per-material uniform, and it is why the band offset cannot be conditional: a material whose
// sheet is not banded is given Flywheel's default shaders instead, by GltfMaterial, at import.
//
// It is not a physically based renderer. Minecraft has no light direction, no HDR environment and no
// radiance to integrate, so the highlight is a Blinn-Phong lobe about the two fixed light directions
// vanilla shades entities by, widened by roughness and tinted by metallic. Rough is dull and metal
// takes its highlight from its own colour, without pretending the result is energy conserving. What is
// worth having is normal mapping, emissive, and a highlight that at least moves the right way.

#include "flywheel:internal/diffuse.glsl"

/// Bands in a GemRender PBR sheet. Must agree with SurfaceBake.BANDS.
const float GEMRENDER_BANDS = 3.0;
const float GEMRENDER_BAND = 1.0 / GEMRENDER_BANDS;

/// Roughness below this would make the highlight a single aliasing pixel.
const float GEMRENDER_MIN_ROUGHNESS = 0.045;

/// Dielectric reflectance at normal incidence, the usual 4% stand-in for everything non-metal.
const vec3 GEMRENDER_DIELECTRIC_F0 = vec3(0.04);

/// Overall weight of the directional highlight. Chosen to look right beside vanilla blocks.
const float GEMRENDER_SPECULAR = 0.35;

/// Ceiling on one lobe, so a near-mirror surface does not clip to a flickering white dot.
const float GEMRENDER_MAX_LOBE = 6.0;

/// A tangent frame from screen-space derivatives, with no per-vertex tangent.
///
/// GemRender has no vertex attribute left to put a tangent in (see
/// gemrender-internal/docs/ARCHITECTURE.md "Blocker 1"),
/// so this is Schueler's cotangent frame, reconstructing T and B from how position and texture
/// coordinate change across the triangle. It is exact for the flat-shaded, axis-aligned UV islands
/// machine models are made of, and disagrees with a MikkTSpace tangent on mirrored islands, where the
/// handedness a real tangent would have carried has to be inferred.
mat3 gemrender_cotangentFrame(vec3 normal, vec3 position, vec2 uv) {
    vec3 dpdx = dFdx(position);
    vec3 dpdy = dFdy(position);
    vec2 duvdx = dFdx(uv);
    vec2 duvdy = dFdy(uv);

    vec3 dpdyPerp = cross(dpdy, normal);
    vec3 dpdxPerp = cross(normal, dpdx);

    vec3 tangent = dpdyPerp * duvdx.x + dpdxPerp * duvdy.x;
    vec3 bitangent = dpdyPerp * duvdx.y + dpdxPerp * duvdy.y;

    // A degenerate UV island gives both vectors zero length, and inversesqrt(0) is infinity. The
    // epsilon is what stops one bad face turning the whole surface into NaNs.
    float scale = inversesqrt(max(1e-8, max(dot(tangent, tangent), dot(bitangent, bitangent))));
    return mat3(tangent * scale, bitangent * scale, normal);
}

/// One Blinn-Phong lobe, normalised so that widening it does not brighten it.
///
/// The usual (n + 8) / 8 normalisation reaches several hundred at the exact reflection angle, which is
/// correct for a physical light and wrong for a constant one: with no exposure control the peak clips
/// to white and aliases as the surface turns. The cap is a rendering decision, not a physical one.
float gemrender_lobe(vec3 normal, vec3 halfway, float shininess) {
    return min(GEMRENDER_MAX_LOBE, pow(max(0.0, dot(normal, halfway)), shininess) * (shininess + 8.0) / 8.0);
}

void flw_materialFragment() {
    vec2 uv = flw_vertexTexCoord;

    vec4 surface = texture(flw_diffuseTex, vec2(uv.x, uv.y + GEMRENDER_BAND));
    vec3 emissive = texture(flw_diffuseTex, vec2(uv.x, uv.y + 2.0 * GEMRENDER_BAND)).rgb;

    // Only x and y are stored: a tangent-space normal points out of the surface, so z is positive and
    // recovering it costs one square root instead of the channel that carries roughness.
    vec2 tangentXy = surface.rg * 2.0 - 1.0;
    vec3 tangentNormal = vec3(tangentXy, sqrt(max(0.0, 1.0 - dot(tangentXy, tangentXy))));

    vec3 geometric = normalize(flw_vertexNormal);
    vec3 normal = normalize(gemrender_cotangentFrame(geometric, flw_vertexPos.xyz, uv) * tangentNormal);

    float roughness = max(GEMRENDER_MIN_ROUGHNESS, surface.b);
    float metallic = surface.a;

    vec3 albedo = flw_fragColor.rgb;

    // A metal has no diffuse albedo at all: its base colour is the tint of its reflection.
    vec3 diffuseColor = albedo * (1.0 - metallic);
    vec3 specularColor = mix(GEMRENDER_DIELECTRIC_F0, albedo, metallic);

    // diffuseFromLightDirections and not diffuse(), and the difference is the whole feature: vanilla's
    // diffuse() is an even function of each axis, so a normal map cannot move it. Flywheel's own
    // cardinal lighting is off for PBR materials (see GltfMaterial), so this replaces it.
    float shade = diffuseFromLightDirections(normal);

    // Roughness to a Blinn exponent: mirror-smooth is ~4096, fully rough is 1.
    float shininess = exp2(12.0 * (1.0 - roughness));

    vec3 view = normalize(flw_cameraPos - flw_vertexPos.xyz);
    float highlight = gemrender_lobe(normal, normalize(flw_light0Direction + view), shininess)
            + gemrender_lobe(normal, normalize(flw_light1Direction + view), shininess);

    // Both terms are needed. A metal's diffuse colour is zero, so with the highlight alone it renders
    // black wherever it is not catching one of the two lights. What is missing there is the
    // environment, and the stand-in is to treat the surroundings as uniform, which under a lightmap
    // is very nearly true. Integrating a uniform environment against a normalised lobe leaves just the
    // reflectance, so metallic moves the tint rather than removing the light.
    flw_fragColor.rgb = (diffuseColor + specularColor) * shade
            + specularColor * highlight * GEMRENDER_SPECULAR * (1.0 - roughness)
            + emissive;

    // Emissive has to survive the lightmap, which Flywheel applies after this function. Raising the
    // block-light coordinate is how vanilla does the same thing, and the lookup was happening anyway.
    flw_fragLight.x = max(flw_fragLight.x, max(emissive.r, max(emissive.g, emissive.b)));
}
