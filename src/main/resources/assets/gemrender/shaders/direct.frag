uniform sampler2D _gr_atlas;
uniform sampler2D _gr_lightmap;
uniform sampler2D _gr_overlayTex;

uniform vec3 _gr_light0;
uniform vec3 _gr_light1;

uniform float _gr_alphaCutoff;
// Sheet bands: base, surface, emissive (rgb = emission, a = paint coverage).
uniform bool _gr_banded;

in vec2 _gr_texCoord;
in vec2 _gr_lightCoord;
in vec4 _gr_tint;
in vec3 _gr_shadeNormal;
in vec2 _gr_overlayCoord;
in vec3 _gr_paintCoord;
flat in int _gr_paintLayer;
flat in int _gr_paintReference;
#ifdef GEMRENDER_LIGHTS
in vec3 _gr_lightPos;
#endif

// Coverage lives in the emissive band's alpha; paintable models are banded (3 bands).
const float GR_EMISSIVE_BAND = 2.0 / 3.0;

layout(location = 0) out vec4 _gr_fragColor;
#ifdef GEMRENDER_LIGHTS
layout(location = 1) out vec4 _grl_irOut;
#endif

void main() {
#ifdef GEMRENDER_LIGHTS
    gemrender_lightPrepare(_gr_lightPos);
    _grl_irOut = vec4(0.0);
#endif
    vec4 colour = texture(_gr_atlas, _gr_texCoord) * _gr_tint;
    if (_gr_paintLayer >= 0) {
        vec4 paint = gemrender_paint(_gr_paintLayer, _gr_paintCoord,
                texture(_gr_atlas, _gr_texCoord + vec2(0.0, GR_EMISSIVE_BAND)).a);
        vec3 reference = gemrender_unpackPaint(_gr_paintReference).rgb;
        vec3 repainted = min(vec3(1.0), colour.rgb * paint.rgb / max(reference, vec3(1.0 / 255.0)));
        colour.rgb = mix(colour.rgb, repainted, paint.a);
    }

    if (colour.a <= _gr_alphaCutoff) {
        discard;
    }

    vec3 lit = colour.rgb;
    vec3 emission = _gr_banded ? texture(_gr_atlas, _gr_texCoord + vec2(0.0, GR_EMISSIVE_BAND)).rgb : vec3(0.0);
    // Emissive: light coord < 0.
    if (_gr_lightCoord.x >= 0.0) {
        float light0 = max(0.0, dot(normalize(_gr_light0), _gr_shadeNormal));
        float light1 = max(0.0, dot(normalize(_gr_light1), _gr_shadeNormal));
        float diffuse = min(1.0, (light0 + light1) * 0.6 + 0.4);

        lit = colour.rgb * diffuse * texture(_gr_lightmap, _gr_lightCoord).rgb;
#ifdef GEMRENDER_LIGHTS
        float skyLevel = clamp((_gr_lightCoord.y * 16.0 - 0.5) / 15.0, 0.0, 1.0);
        lit += colour.rgb * (gemrender_lights(_gr_lightPos, _gr_shadeNormal) + gemrender_mergedNight(skyLevel));
        _grl_irOut = vec4(colour.rgb * (_grl_irSum + gemrender_nightSky(skyLevel)), colour.a);
#endif
        lit += emission;
    }

    vec4 overlay = texture(_gr_overlayTex, _gr_overlayCoord);
    lit = mix(overlay.rgb, lit, overlay.a);

    _gr_fragColor = vec4(lit, colour.a);
}
