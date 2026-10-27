// Lenses (Lenses.java), after X-Ray's distortion (combine_2 + particle_distort): the scene is read at a screen-UV
// offset taken from a distortion field, and hazed a little toward a blur. X-Ray's field is billboards textured with a
// distortion map, rotated and resized as they live; here it is two octaves of gradient noise anchored to the lens (the
// ray's closest approach, in radii), turned and drifting with time, masked soft toward the rim as a billboard's alpha.
// Fine, as X-Ray's maps are and its billboards come in several sizes: a lens filling the screen shows ripples, not
// one smooth (so invisible) shift of the whole image. Offset in screen UV like def_distort, but scaled down while the
// lens is small on screen. Foreground is never pulled in (X-Ray's depth_x check).
#define MAX_LENSES 16

uniform sampler2D _gr_scene;
uniform sampler2D _gr_depth;
uniform mat4 _gr_clipToRelative;
uniform mat4 _gr_relativeToClip;
uniform int _gr_count;
uniform float _gr_time;
// xyz camera-relative centre, w radius
uniform vec4 _gr_lens[MAX_LENSES];
// x strength (peak screen-UV offset, lens near), y turn (radians the field is turned about the vertical)
uniform vec2 _gr_shape[MAX_LENSES];

out vec4 frag;

vec3 relativeAt(vec2 uv) {
    vec4 h = _gr_clipToRelative * vec4(uv * 2.0 - 1.0, texture(_gr_depth, uv).r * 2.0 - 1.0, 1.0);
    return h.xyz / h.w;
}

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

// Gradient noise, about -0.7..0.7.
float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(dot(hash3(i), f),
                       dot(hash3(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), u.x),
                   mix(dot(hash3(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)),
                       dot(hash3(i + vec3(1, 1, 0)), f - vec3(1, 1, 0)), u.x), u.y),
               mix(mix(dot(hash3(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)),
                       dot(hash3(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), u.x),
                   mix(dot(hash3(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)),
                       dot(hash3(i + vec3(1, 1, 1)), f - vec3(1, 1, 1)), u.x), u.y), u.z);
}

void main() {
    vec2 size = vec2(textureSize(_gr_scene, 0));
    vec2 uv = gl_FragCoord.xy / size;
    vec3 rel = relativeAt(uv);
    float dist = length(rel);
    vec3 dir = rel / max(dist, 1e-6);

    vec2 offset = vec2(0.0);
    float haze = 0.0;
    float nearest = 1e9;
    for (int i = 0; i < _gr_count; i++) {
        vec3 c = _gr_lens[i].xyz;
        float radius = _gr_lens[i].w;
        float t = dot(c, dir);
        float b = length(c - dir * t);
        if (b >= radius || t < -radius) {
            continue;
        }
        // Full strength once the scene is at or past the centre; nothing for scene in front of the sphere.
        float behind = smoothstep(t - radius, t, dist);
        float mask = (1.0 - smoothstep(0.5, 1.0, b / radius)) * behind;
        if (mask <= 0.0) {
            continue;
        }
        vec3 q = (dir * t - c) / radius;
        float turn = _gr_shape[i].y;
        q.xz = mat2(cos(turn), -sin(turn), sin(turn), cos(turn)) * q.xz;
        vec3 p = q * 5.0 + vec3(0.0, _gr_time * 0.5, 0.0);
        vec3 fine = p * 2.7 + vec3(0.0, _gr_time * 0.6, 0.0);
        vec2 n = 0.6 * vec2(noise(p), noise(p + vec3(31.4, 17.7, 9.2)))
                + 0.4 * vec2(noise(fine + vec3(5.1, 2.3, 7.7)), noise(fine + vec3(19.9, 11.3, 3.1)));
        // Angular radius over 0.35 rad (~20 degrees): full strength from there in, less while it is small.
        float near = min(1.0, radius / max(t, radius) / 0.35);
        offset += n * _gr_shape[i].x * near * mask;
        haze = max(haze, mask * near);
        nearest = min(nearest, t - radius);
    }
    if (haze <= 0.0) {
        discard;
    }

    vec2 at = clamp(uv + offset, vec2(0.0), vec2(1.0));
    if (length(relativeAt(at)) < nearest) {
        at = uv;
    }
    vec3 sharp = texture(_gr_scene, at).rgb;
    vec2 tap = 1.5 / size;
    vec3 blurred = 0.25 * (texture(_gr_scene, at + vec2(tap.x, 0.0)).rgb + texture(_gr_scene, at - vec2(tap.x, 0.0)).rgb
            + texture(_gr_scene, at + vec2(0.0, tap.y)).rgb + texture(_gr_scene, at - vec2(0.0, tap.y)).rgb);
    frag = vec4(mix(sharp, blurred, 0.5 * haze), 1.0);
}
