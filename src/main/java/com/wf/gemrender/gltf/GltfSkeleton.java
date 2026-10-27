package com.wf.gemrender.gltf;

import org.joml.Matrix4f;

import java.util.Map;

/** Node hierarchy + animations of a glTF, no meshes or textures: poses on a dedicated server. */
public record GltfSkeleton(GltfPaletteLayout layout, Map<String, GltfAnimation> animations) {
    public int jointCount() {
        return layout.size();
    }

    public Matrix4f[] newPalette() {
        Matrix4f[] palette = new Matrix4f[jointCount()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = new Matrix4f();
        }
        return palette;
    }
}
