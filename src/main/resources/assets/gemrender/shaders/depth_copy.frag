// Copies a depth texture into the bound framebuffer's depth attachment through gl_FragDepth.
// A fullscreen draw rather than glBlitFramebuffer, because vanilla's depth attachment is an unsized
// GL_DEPTH_COMPONENT (or DEPTH32F_STENCIL8 with stencil on) and a depth blit demands identical
// formats; a shader copy has no format to disagree about.

uniform sampler2D _gr_depth;

void main() {
    gl_FragDepth = texelFetch(_gr_depth, ivec2(gl_FragCoord.xy), 0).r;
}
