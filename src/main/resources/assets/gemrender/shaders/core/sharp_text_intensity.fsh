#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec4 fetch(ivec2 p, ivec2 size) {
    return texelFetch(Sampler0, clamp(p, ivec2(0), size - 1), 0).rrrr;
}

// Texel edges blend over one screen pixel => == nearest at integer scale, even columns at fractional.
vec4 sharpBilinear(vec2 uv) {
    ivec2 size = textureSize(Sampler0, 0);
    vec2 t = uv * vec2(size) - 0.5;
    vec2 base = floor(t);
    vec2 f = clamp((t - base - 0.5) / max(fwidth(t), vec2(1e-4)) + 0.5, 0.0, 1.0);
    ivec2 p = ivec2(base);
    vec4 top = mix(fetch(p, size), fetch(p + ivec2(1, 0), size), f.x);
    vec4 bottom = mix(fetch(p + ivec2(0, 1), size), fetch(p + 1, size), f.x);
    return mix(top, bottom, f.y);
}

void main() {
    vec4 color = sharpBilinear(texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
