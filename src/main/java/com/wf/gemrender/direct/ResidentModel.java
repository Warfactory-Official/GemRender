package com.wf.gemrender.direct;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfMesh;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL33C.glGetInteger;

final class ResidentModel {
    private final List<Part> parts;

    private ResidentModel(List<Part> parts) {
        this.parts = parts;
    }

    static ResidentModel upload(GemRenderGltfModel model) {
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousArray = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int previousIndex = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);

        try {
            List<Part> parts = new ArrayList<>();
            int skipped = 0;

            for (Model.ConfiguredMesh configured : model.model()
                    .meshes()) {
                DirectMaterial material = DirectMaterial.of(configured.material());
                if (material == null) {

                    continue;
                }

                Mesh mesh = configured.mesh();
                if (!(mesh instanceof GltfMesh gltfMesh)) {
                    skipped++;
                    continue;
                }

                parts.add(new Part(ResidentMesh.upload(gltfMesh.geometry()), material));
            }

            if (skipped > 0) {
                GemRender.LOGGER.warn("{} mesh(es) of a model are not GltfMesh and were skipped by the "
                        + "direct path; it reads GemRender's own vertex encoding and cannot draw a mesh "
                        + "built some other way", skipped);
            }

            return new ResidentModel(parts);
        } finally {
            glBindVertexArray(previousVao);
            glBindBuffer(GL_ARRAY_BUFFER, previousArray);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, previousIndex);
        }
    }

    List<Part> parts() {
        return parts;
    }

    void delete() {
        for (Part part : parts) {
            part.mesh()
                    .delete();
        }
        parts.clear();
    }

    record Part(ResidentMesh mesh, DirectMaterial material) {
    }
}
