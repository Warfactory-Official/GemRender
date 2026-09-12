package com.wf.gemrender.texture;

/**
 * Where one variant's tiles sit in a multi-variant sheet: the offset added to a vertex's texture
 * coordinate, per instance.
 *
 * <p>A variant is the same model wearing a different set of textures -- a mob's colour, a vehicle's
 * livery, a team's paint. All of them are stitched into one sheet at import, so the geometry, the
 * palette layout and the mesh are shared and every variant of a model draws in <b>one batch</b>. What
 * separates them at draw time is this, two floats on the instance.
 *
 * <p>Tiles run across in {@code u} and down in {@code v} <em>inside</em> a band, never across bands:
 * a PBR sheet's three bands stay the outer division of the sheet, so the offset from a base texel to
 * its surface texel is still the compile-time {@code 1/3} that {@code material/pbr.frag} adds. That
 * ordering is the whole reason variants cost no shader work beyond one addition.
 */
public record VariantUv(float u, float v) {
    /**
     * The only variant of a model that declares none, and the tile every mesh's uvs are baked into.
     */
    public static final VariantUv NONE = new VariantUv(0.0f, 0.0f);

    public boolean isNone() {
        return u == 0.0f && v == 0.0f;
    }
}
