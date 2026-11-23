#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec4 fetch(ivec2 p, ivec2 size) {
    return texelFetch(Sampler0, clamp(p, ivec2(0), size - 1), 0);
}

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
    vec4 color = sharpBilinear(texCoord0).rrrr * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
