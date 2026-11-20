package com.wf.gemrender.gltf;

import com.wf.gemrender.vendor.jgltf.model.AccessorData;
import com.wf.gemrender.vendor.jgltf.model.AccessorFloatData;
import com.wf.gemrender.vendor.jgltf.model.AccessorIntData;
import com.wf.gemrender.vendor.jgltf.model.AccessorShortData;
import com.wf.gemrender.vendor.jgltf.model.GltfModel;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.jgltf.model.io.GltfModelReader;
import java.io.IOException;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigSlicerTest {

    /** Box sliced through: area + oriented area per axis kept, no triangle straddles the plane. */
    @Test
    void sliceKeepsSurfaceAndSeparatesSides() throws Exception {
        for (String name : new String[]{"right_arm", "body"}) {
            GltfModel gltf = read();
            NodeModel node = gltf.getNodeModels().stream().filter(n -> name.equals(n.getName())).findFirst().orElseThrow();
            MeshPrimitiveModel p = node.getMeshModels().get(0).getMeshPrimitiveModels().get(0);
            float[] before = area(p);
            float c = -0.21F;
            RigSlicer.slice(node, (x, y, z) -> y - c);
            float[] after = area(p);
            for (int k = 0; k < 4; k++) {
                assertEquals(before[k], after[k], 1e-5, name + " area component " + k);
            }
            AccessorFloatData pos = (AccessorFloatData) p.getAttributes().get("POSITION").getAccessorData();
            int[] idx = indices(p);
            for (int t = 0; t < idx.length; t += 3) {
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (int k = 0; k < 3; k++) {
                    float d = pos.get(idx[t + k], 1) - c;
                    lo = Math.min(lo, d);
                    hi = Math.max(hi, d);
                }
                assertTrue(lo >= -1e-5 || hi <= 1e-5, name + " triangle " + t / 3 + " straddles");
            }
        }
    }

    private static GltfModel read() throws URISyntaxException, IOException {
        return new GltfModelReader().read(Path.of(RigSlicerTest.class.getResource("/slice/box.gltf").toURI()).toUri());
    }

    /** {total area, oriented area x, y, z}. */
    private static float[] area(MeshPrimitiveModel p) {
        AccessorFloatData pos = (AccessorFloatData) p.getAttributes().get("POSITION").getAccessorData();
        int[] idx = indices(p);
        float[] out = new float[4];
        Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
        for (int t = 0; t < idx.length; t += 3) {
            a.set(pos.get(idx[t], 0), pos.get(idx[t], 1), pos.get(idx[t], 2));
            b.set(pos.get(idx[t + 1], 0), pos.get(idx[t + 1], 1), pos.get(idx[t + 1], 2)).sub(a);
            c.set(pos.get(idx[t + 2], 0), pos.get(idx[t + 2], 1), pos.get(idx[t + 2], 2)).sub(a);
            Vector3f n = b.cross(c, new Vector3f()).mul(0.5F);
            out[0] += n.length();
            out[1] += n.x;
            out[2] += n.y;
            out[3] += n.z;
        }
        return out;
    }

    private static int[] indices(MeshPrimitiveModel p) {
        AccessorData data = p.getIndices().getAccessorData();
        int[] out = new int[p.getIndices().getCount()];
        for (int i = 0; i < out.length; i++) {
            out[i] = data instanceof AccessorIntData x ? x.get(i, 0) : ((AccessorShortData) data).get(i, 0) & 0xFFFF;
        }
        return out;
    }
}
