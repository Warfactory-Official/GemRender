package com.wf.gemrender.direct;

import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
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

    static final int INSTANCE_STRIDE = 100;
    private static final int VERTEX_STRIDE = 48;
    private final int vao;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int instanceBuffer;

    private final int indexCount;

    private int instanceCapacity;

    private ResidentMesh(int vao, int vertexBuffer, int indexBuffer, int instanceBuffer, int indexCount) {
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

            int vao = glGenVertexArrays();
            glBindVertexArray(vao);

            int vertexBuffer = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

            attribute(0, 3, GL_FLOAT, false, VERTEX_STRIDE, 0);
            attribute(1, 3, GL_FLOAT, false, VERTEX_STRIDE, 12);
            attribute(2, 2, GL_FLOAT, false, VERTEX_STRIDE, 24);
            attribute(3, 4, GL_UNSIGNED_BYTE, true, VERTEX_STRIDE, 32);
            attribute(4, 2, GL_FLOAT, false, VERTEX_STRIDE, 36);
            attribute(5, 1, GL_FLOAT, false, VERTEX_STRIDE, 44);

            int indexBuffer = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

            int instanceBuffer = glGenBuffers();
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
            attribute(14, 2, GL_FLOAT, false, INSTANCE_STRIDE, 84);
            glVertexAttribDivisor(14, 1);
            attribute(15, 2, GL_FLOAT, false, INSTANCE_STRIDE, 92);
            glVertexAttribDivisor(15, 1);

            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);

            return new ResidentMesh(vao, vertexBuffer, indexBuffer, instanceBuffer, indexCount);
        } finally {
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
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
        glBindVertexArray(vao);
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

    void delete() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vertexBuffer);
        glDeleteBuffers(indexBuffer);
        glDeleteBuffers(instanceBuffer);
    }
}
