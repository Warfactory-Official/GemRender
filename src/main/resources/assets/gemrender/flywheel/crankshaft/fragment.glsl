// Flywheel 1.0 fragment-stage API on CrankShaft 26.2; CrankShaftShaders prepends it to instance/material .frag.
#define GEMRENDER_CHUNK_OIT 1
// Inputs written by material/varyings26.vert; unused (no static use) under any other material vertex shader.
flat in ivec2 gemrender_overlay;
in vec2 gemrender_texCoord;
flat in float gemrender_renderSeconds;
#define flw_vertexOverlay gemrender_overlay
#define flw_vertexTexCoord gemrender_texCoord
#define flw_renderSeconds gemrender_renderSeconds

#define flw_viewInverse inverse(ModelViewMat)
#define flw_cameraPos (flw_viewInverse[3].xyz)

// flw_diffuseTex: Sampler0 is declared after the material source; defined at the end of the fragment main.
vec4 gemrender_diffuse(vec2 uv);
