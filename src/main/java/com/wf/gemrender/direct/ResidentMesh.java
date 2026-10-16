package com.wf.gemrender.direct;

import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31C.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.opengl.GL33C.GL_FLOAT;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL33C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL33C.GL_UNSIGNED_INT;

final class ResidentMesh {

    static final int INSTANCE_STRIDE = 116;
    private static final int VERTEX_STRIDE = 48;
    private static final int EXTRAS_LOCATION = 15;
    private final MeshGeometry geometry;
    private final int vao;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int instanceBuffer;

    private final int indexCount;

    private int instanceCapacity;

    private int guestVao;
    private int extrasBuffer;

    private ResidentMesh(MeshGeometry geometry, int vao, int vertexBuffer, int indexBuffer, int instanceBuffer,
                         int indexCount) {
        this.geometry = geometry;
        this.vao = vao;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.instanceBuffer = instanceBuffer;
        this.indexCount = indexCount;
    }

    static ResidentMesh upload(MeshGeometry geometry) {
        int vertexCount = geometry.vertexCount();
        int indexCount = geometry.indexCount();

        ByteBuffer vertices = MemoryUtil.memAlloc(vertexCount * VERTEX_STRIDE);
        IntBuffer indices = MemoryUtil.memAllocInt(indexCount);
        try {
            for (int v = 0; v < vertexCount; v++) {
                int base = v * VERTEX_STRIDE;

                vertices.putFloat(base, geometry.position(v, 0));
                vertices.putFloat(base + 4, geometry.position(v, 1));
                vertices.putFloat(base + 8, geometry.position(v, 2));

                vertices.putFloat(base + 12, geometry.normal(v, 0));
                vertices.putFloat(base + 16, geometry.normal(v, 1));
                vertices.putFloat(base + 20, geometry.normal(v, 2));

                vertices.putFloat(base + 24, geometry.texCoord(v, 0));
                vertices.putFloat(base + 28, geometry.texCoord(v, 1));

                for (int influence = 0; influence < BoneAttributeCodec.INFLUENCES; influence++) {
                    int quantised = (int) (geometry.weightChannel(v, influence) * 255.0f);
                    vertices.put(base + 32 + influence, (byte) quantised);
                }

                int packed = geometry.packedJoints(v);
                vertices.putFloat(base + 36, (packed & 0xFFFF) / 256.0f);
                vertices.putFloat(base + 40, ((packed >>> 16) & 0xFFFF) / 256.0f);

                vertices.putFloat(base + 44, geometry.morphSet(v));
            }

            for (int i = 0; i < indexCount; i++) {
                indices.put(i, geometry.index(i));
            }

            int vertexBuffer = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

            int indexBuffer = glGenBuffers();
            int instanceBuffer = glGenBuffers();

            int vao = glGenVertexArrays();
            glBindVertexArray(vao);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
            layout(vertexBuffer, instanceBuffer, true);

            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);

            return new ResidentMesh(geometry, vao, vertexBuffer, indexBuffer, instanceBuffer, indexCount);
        } finally {
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
        }
    }

    /** VAO bound, its element buffer attached. {@code paint} false: location 15 left to the caller. */
    private static void layout(int vertexBuffer, int instanceBuffer, boolean paint) {
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        attribute(0, 3, GL_FLOAT, false, VERTEX_STRIDE, 0);
        attribute(1, 3, GL_FLOAT, false, VERTEX_STRIDE, 12);
        attribute(2, 2, GL_FLOAT, false, VERTEX_STRIDE, 24);
        attribute(3, 4, GL_UNSIGNED_BYTE, true, VERTEX_STRIDE, 32);
        attribute(4, 2, GL_FLOAT, false, VERTEX_STRIDE, 36);
        attribute(5, 1, GL_FLOAT, false, VERTEX_STRIDE, 44);

        glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer);

        for (int column = 0; column < 4; column++) {
            attribute(6 + column, 4, GL_FLOAT, false, INSTANCE_STRIDE, column * 16);
            glVertexAttribDivisor(6 + column, 1);
        }
        integerAttribute(10, 1, INSTANCE_STRIDE, 64);
        glVertexAttribDivisor(10, 1);
        integerAttribute(11, 1, INSTANCE_STRIDE, 68);
        glVertexAttribDivisor(11, 1);
        attribute(12, 2, GL_FLOAT, false, INSTANCE_STRIDE, 72);
        glVertexAttribDivisor(12, 1);
        attribute(13, 4, GL_UNSIGNED_BYTE, true, INSTANCE_STRIDE, 80);
        glVertexAttribDivisor(13, 1);
        // 14 = (overlay, uvOffset), 15 = paint: 16 attributes is the GL minimum.
        attribute(14, 4, GL_FLOAT, false, INSTANCE_STRIDE, 84);
        glVertexAttribDivisor(14, 1);
        if (paint) {
            attribute(15, 4, GL_FLOAT, false, INSTANCE_STRIDE, 100);
            glVertexAttribDivisor(15, 1);
        }
    }

    private static void attribute(int index, int size, int type, boolean normalized, int stride, int offset) {
        glEnableVertexAttribArray(index);
        glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    private static void integerAttribute(int index, int size, int stride, int offset) {
        glEnableVertexAttribArray(index);
        glVertexAttribIPointer(index, size, GL_UNSIGNED_INT, stride, offset);
    }

    void draw(ByteBuffer instanceData, int count) {
        drawWith(vao, instanceData, count);
    }

    /** Location 15 = {@link #extras} instead of paint. Built on first use. */
    void drawGuest(ByteBuffer instanceData, int count) {
        if (guestVao == 0) {
            createGuest();
        }
        drawWith(guestVao, instanceData, count);
    }

    private void drawWith(int array, ByteBuffer instanceData, int count) {
        glBindVertexArray(array);
        glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer);

        int needed = count * INSTANCE_STRIDE;
        if (needed > instanceCapacity) {
            instanceCapacity = Integer.highestOneBit(Math.max(needed - 1, 1)) * 2;
        }

        glBufferData(GL_ARRAY_BUFFER, instanceCapacity, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceData);

        glDrawElementsInstanced(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L, count);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void createGuest() {
        FloatBuffer extras = extras(geometry);
        try {
            extrasBuffer = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, extrasBuffer);
            glBufferData(GL_ARRAY_BUFFER, extras, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(extras);
        }

        guestVao = glGenVertexArrays();
        glBindVertexArray(guestVao);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        layout(vertexBuffer, instanceBuffer, false);
        glBindBuffer(GL_ARRAY_BUFFER, extrasBuffer);
        attribute(EXTRAS_LOCATION, 4, GL_FLOAT, false, 16, 0);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Per vertex {@code (octTangent.x + (w < 0 ? 4 : 0), octTangent.y, midUv)}: tangent from incident triangles' UV
     * gradients (Gram-Schmidt against the normal), mid-UV = mean of incident triangles' UV centroids.
     */
    static FloatBuffer extras(MeshGeometry geometry) {
        int vertexCount = geometry.vertexCount();
        float[] tangent = new float[vertexCount * 3];
        float[] bitangent = new float[vertexCount * 3];
        float[] mid = new float[vertexCount * 3];

        for (int i = 0; i + 2 < geometry.indexCount(); i += 3) {
            int a = geometry.index(i);
            int b = geometry.index(i + 1);
            int c = geometry.index(i + 2);

            float e1x = geometry.position(b, 0) - geometry.position(a, 0);
            float e1y = geometry.position(b, 1) - geometry.position(a, 1);
            float e1z = geometry.position(b, 2) - geometry.position(a, 2);
            float e2x = geometry.position(c, 0) - geometry.position(a, 0);
            float e2y = geometry.position(c, 1) - geometry.position(a, 1);
            float e2z = geometry.position(c, 2) - geometry.position(a, 2);
            float du1 = geometry.texCoord(b, 0) - geometry.texCoord(a, 0);
            float dv1 = geometry.texCoord(b, 1) - geometry.texCoord(a, 1);
            float du2 = geometry.texCoord(c, 0) - geometry.texCoord(a, 0);
            float dv2 = geometry.texCoord(c, 1) - geometry.texCoord(a, 1);

            float det = du1 * dv2 - du2 * dv1;
            float r = Math.abs(det) < 1e-12f ? 0.0f : 1.0f / det;
            float tx = (e1x * dv2 - e2x * dv1) * r;
            float ty = (e1y * dv2 - e2y * dv1) * r;
            float tz = (e1z * dv2 - e2z * dv1) * r;
            float bx = (e2x * du1 - e1x * du2) * r;
            float by = (e2y * du1 - e1y * du2) * r;
            float bz = (e2z * du1 - e1z * du2) * r;

            float midU = (geometry.texCoord(a, 0) + geometry.texCoord(b, 0) + geometry.texCoord(c, 0)) / 3.0f;
            float midV = (geometry.texCoord(a, 1) + geometry.texCoord(b, 1) + geometry.texCoord(c, 1)) / 3.0f;

            for (int vertex : new int[] {a, b, c}) {
                tangent[vertex * 3] += tx;
                tangent[vertex * 3 + 1] += ty;
                tangent[vertex * 3 + 2] += tz;
                bitangent[vertex * 3] += bx;
                bitangent[vertex * 3 + 1] += by;
                bitangent[vertex * 3 + 2] += bz;
                mid[vertex * 3] += midU;
                mid[vertex * 3 + 1] += midV;
                mid[vertex * 3 + 2] += 1.0f;
            }
        }

        FloatBuffer out = MemoryUtil.memAllocFloat(vertexCount * 4);
        for (int v = 0; v < vertexCount; v++) {
            float nx = geometry.normal(v, 0);
            float ny = geometry.normal(v, 1);
            float nz = geometry.normal(v, 2);
            float tx = tangent[v * 3];
            float ty = tangent[v * 3 + 1];
            float tz = tangent[v * 3 + 2];
            float along = nx * tx + ny * ty + nz * tz;
            tx -= nx * along;
            ty -= ny * along;
            tz -= nz * along;
            float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (length < 1e-8f) {
                // Any unit vector orthogonal to the normal keeps a pack's TBN finite.
                boolean upright = Math.abs(ny) < 0.999f;
                tx = upright ? nz : 0.0f;
                ty = upright ? 0.0f : -nz;
                tz = upright ? -nx : ny;
                length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            }
            tx /= length;
            ty /= length;
            tz /= length;

            float handed = (ny * tz - nz * ty) * bitangent[v * 3] + (nz * tx - nx * tz) * bitangent[v * 3 + 1]
                    + (nx * ty - ny * tx) * bitangent[v * 3 + 2];

            float l1 = Math.abs(tx) + Math.abs(ty) + Math.abs(tz);
            float ox = tx / l1;
            float oy = ty / l1;
            if (tz < 0.0f) {
                float fx = (1.0f - Math.abs(oy)) * Math.signum(ox == 0.0f ? 1.0f : ox);
                oy = (1.0f - Math.abs(ox)) * Math.signum(oy == 0.0f ? 1.0f : oy);
                ox = fx;
            }

            float weight = mid[v * 3 + 2];
            out.put(v * 4, handed < 0.0f ? ox + 4.0f : ox);
            out.put(v * 4 + 1, oy);
            out.put(v * 4 + 2, weight == 0.0f ? geometry.texCoord(v, 0) : mid[v * 3] / weight);
            out.put(v * 4 + 3, weight == 0.0f ? geometry.texCoord(v, 1) : mid[v * 3 + 1] / weight);
        }
        return out;
    }

    void delete() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vertexBuffer);
        glDeleteBuffers(indexBuffer);
        glDeleteBuffers(instanceBuffer);
        if (guestVao != 0) {
            glDeleteVertexArrays(guestVao);
            glDeleteBuffers(extrasBuffer);
        }
    }
}
