// CrankShaft 26.2 fragment stage has no flw_vertexOverlay / flw_vertexTexCoord / flw_renderSeconds.
flat out ivec2 gemrender_overlay;
out vec2 gemrender_texCoord;
flat out float gemrender_renderSeconds;

void flw_materialVertex() {
    gemrender_overlay = flw_vertexOverlay;
    gemrender_texCoord = flw_vertexTexCoord;
    gemrender_renderSeconds = flw_renderSeconds;
}
