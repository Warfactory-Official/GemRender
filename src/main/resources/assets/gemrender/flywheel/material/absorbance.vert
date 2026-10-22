#include "gemrender:absorbance_band.glsl"

// Share 0 => degenerate: no fragments in the pass that does not own the sprite.
void flw_materialVertex() {
    if (gemrender_bandCollapsible(flw_vertexOverlay.y) && gemrender_bandShare(flw_vertexOverlay.y) <= 0.0) {
        flw_vertexPos = vec4(0.0, 0.0, 0.0, 1.0);
    }
}
