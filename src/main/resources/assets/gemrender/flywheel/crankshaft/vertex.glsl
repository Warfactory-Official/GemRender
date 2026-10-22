// Flywheel 1.0 vertex-stage API on CrankShaft 26.2; CrankShaftShaders prepends it to instance/material .vert.
#define GEMRENDER_CHUNK_OIT 1
// Unconditional (Compilation hoists it); enable, not require: the selector path may lack it.
#extension GL_ARB_shader_draw_parameters : enable

// Every backend draws with a base vertex; instancing without draw parameters passes it in _flw_instanceSelector.z.
#ifdef _FLW_INSTANCING_SELECTOR
int gemrender_baseVertex();
#elif __VERSION__ >= 460
#define gemrender_baseVertex() gl_BaseVertex
#else
#define gemrender_baseVertex() gl_BaseVertexARB
#endif
#define flw_vertexId (gl_VertexID - gemrender_baseVertex())

// ModelViewMat = view x translate(renderOrigin - camera): flw_vertexPos space, as Flywheel 1.0's flw_view.
#define flw_viewInverse inverse(ModelViewMat)
#define flw_cameraPos (flw_viewInverse[3].xyz)
#define flw_viewProjection (ProjMat * ModelViewMat)
#define flw_viewProjectionInverse inverse(flw_viewProjection)
