uniform sampler2D _gr_atlas;
uniform sampler2D _gr_lightmap;
uniform sampler2D _gr_overlayTex;

uniform vec3 _gr_light0;
uniform vec3 _gr_light1;

uniform float _gr_alphaCutoff;

in vec2 _gr_texCoord;
in vec2 _gr_lightCoord;
in vec4 _gr_tint;
in vec3 _gr_shadeNormal;
in vec2 _gr_overlayCoord;

out vec4 _gr_fragColor;

void main() {
    vec4 colour = texture(_gr_atlas, _gr_texCoord) * _gr_tint;

    if (colour.a <= _gr_alphaCutoff) {
        discard;
    }

    float light0 = max(0.0, dot(normalize(_gr_light0), _gr_shadeNormal));
    float light1 = max(0.0, dot(normalize(_gr_light1), _gr_shadeNormal));
    float diffuse = min(1.0, (light0 + light1) * 0.6 + 0.4);

    vec3 lit = colour.rgb * diffuse * texture(_gr_lightmap, _gr_lightCoord).rgb;

    vec4 overlay = texture(_gr_overlayTex, _gr_overlayCoord);
    lit = mix(overlay.rgb, lit, overlay.a);

    _gr_fragColor = vec4(lit, colour.a);
}
