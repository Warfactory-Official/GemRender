package com.wf.gemrender.gltf;

import com.wf.gemrender.vendor.jgltf.model.AccessorByteData;
import com.wf.gemrender.vendor.jgltf.model.AccessorData;
import com.wf.gemrender.vendor.jgltf.model.AccessorDatas;
import com.wf.gemrender.vendor.jgltf.model.AccessorFloatData;
import com.wf.gemrender.vendor.jgltf.model.AccessorIntData;
import com.wf.gemrender.vendor.jgltf.model.AccessorModel;
import com.wf.gemrender.vendor.jgltf.model.AccessorShortData;
import com.wf.gemrender.vendor.jgltf.model.ElementType;
import com.wf.gemrender.vendor.jgltf.model.GltfConstants;
import com.wf.gemrender.vendor.jgltf.model.MeshModel;
import com.wf.gemrender.vendor.jgltf.model.MeshPrimitiveModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.jgltf.model.impl.DefaultAccessorModel;
import com.wf.gemrender.vendor.jgltf.model.impl.DefaultBufferModel;
import com.wf.gemrender.vendor.jgltf.model.impl.DefaultBufferViewModel;
import com.wf.gemrender.vendor.jgltf.model.impl.DefaultMeshPrimitiveModel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link RigPatch.Rig#slice}: triangles crossing the plane split there; new vertices lerp every attribute. */
final class RigSlicer {

    /** |d| below => on the plane, never split. */
    static final float EPS = 1e-6F;

    private RigSlicer() {
    }

    static void slice(NodeModel owner, RigPatch.Plane plane) {
        for (MeshModel mesh : owner.getMeshModels()) {
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                if (primitive.getMode() != GltfConstants.GL_TRIANGLES) {
                    continue;
                }
                if (!primitive.getTargets().isEmpty()) {
                    throw new IllegalArgumentException("rig slice: node '" + owner.getName() + "' has morph targets");
                }
                slice((DefaultMeshPrimitiveModel) primitive, plane);
            }
        }
    }

    private static void slice(DefaultMeshPrimitiveModel primitive, RigPatch.Plane plane) {
        Map<String, float[]> attrs = new LinkedHashMap<>();
        Map<String, Integer> widths = new HashMap<>();
        int count = primitive.getAttributes().get("POSITION").getCount();
        for (Map.Entry<String, AccessorModel> e : primitive.getAttributes().entrySet()) {
            AccessorModel a = e.getValue();
            if (!(a.getAccessorData() instanceof AccessorFloatData data) || a.isNormalized()) {
                throw new IllegalArgumentException("rig slice: attribute " + e.getKey() + " is not float");
            }
            int w = a.getElementType().getNumComponents();
            float[] out = new float[count * w];
            for (int v = 0; v < count; v++) {
                for (int c = 0; c < w; c++) {
                    out[v * w + c] = data.get(v, c);
                }
            }
            attrs.put(e.getKey(), out);
            widths.put(e.getKey(), w);
        }
        float[] pos = attrs.get("POSITION");
        float[] d = new float[count];
        for (int v = 0; v < count; v++) {
            d[v] = plane.distance(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]);
        }
        int[] tris = indices(primitive, count);
        boolean crossing = false;
        for (int t = 0; t < tris.length && !crossing; t += 3) {
            crossing = crosses(d, tris[t], tris[t + 1]) || crosses(d, tris[t + 1], tris[t + 2])
                    || crosses(d, tris[t + 2], tris[t]);
        }
        if (!crossing) {
            return;
        }

        Map<String, FloatList> grown = new LinkedHashMap<>();
        attrs.forEach((k, v) -> grown.put(k, new FloatList(v)));
        Map<Long, Integer> edgeVertex = new HashMap<>();
        List<Integer> out = new ArrayList<>(tris.length * 2);
        for (int t = 0; t < tris.length; t += 3) {
            int n = 0, m = 0;
            int[] above = new int[4], below = new int[4];
            for (int k = 0; k < 3; k++) {
                int a = tris[t + k], b = tris[t + (k + 1) % 3];
                if (d[a] <= EPS) {
                    above[n++] = a;
                }
                if (d[a] >= -EPS) {
                    below[m++] = a;
                }
                if (d[a] < -EPS && d[b] > EPS || d[a] > EPS && d[b] < -EPS) {
                    int cut = edgeVertex.computeIfAbsent(edge(a, b),
                            key -> lerp(grown, widths, a, b, d[a] / (d[a] - d[b])));
                    above[n++] = cut;
                    below[m++] = cut;
                }
            }
            fan(out, above, n);
            fan(out, below, m);
        }
        int newCount = grown.get("POSITION").size / 3;
        for (Map.Entry<String, FloatList> e : grown.entrySet()) {
            FloatList list = e.getValue();
            if (e.getKey().equals("NORMAL")) {
                normalize(list);
            }
            int w = widths.get(e.getKey());
            primitive.putAttribute(e.getKey(), floats(list, newCount, w == 1 ? ElementType.SCALAR : vec(w)));
        }
        primitive.setIndices(ints(out));
    }

    /** Polygon {@code (p0..pn-1)} -> triangle fan; fewer than 3 = sliver on the plane, dropped. */
    private static void fan(List<Integer> out, int[] p, int n) {
        for (int k = 1; k + 1 < n; k++) {
            out.add(p[0]);
            out.add(p[k]);
            out.add(p[k + 1]);
        }
    }

    private static boolean crosses(float[] d, int a, int b) {
        return d[a] < -EPS && d[b] > EPS || d[a] > EPS && d[b] < -EPS;
    }

    private static long edge(int a, int b) {
        return a < b ? (long) a << 32 | b : (long) b << 32 | a;
    }

    private static int lerp(Map<String, FloatList> grown, Map<String, Integer> widths, int a, int b, float t) {
        int index = -1;
        for (Map.Entry<String, FloatList> e : grown.entrySet()) {
            int w = widths.get(e.getKey());
            FloatList list = e.getValue();
            index = list.size / w;
            for (int c = 0; c < w; c++) {
                float x = list.get(a * w + c);
                list.add(x + (list.get(b * w + c) - x) * t);
            }
        }
        return index;
    }

    private static void normalize(FloatList n) {
        for (int i = 0; i < n.size; i += 3) {
            float x = n.get(i), y = n.get(i + 1), z = n.get(i + 2);
            float l = (float) Math.sqrt(x * x + y * y + z * z);
            if (l > 0) {
                n.data[i] = x / l;
                n.data[i + 1] = y / l;
                n.data[i + 2] = z / l;
            }
        }
    }

    private static ElementType vec(int width) {
        return switch (width) {
            case 2 -> ElementType.VEC2;
            case 3 -> ElementType.VEC3;
            case 4 -> ElementType.VEC4;
            default -> throw new IllegalArgumentException("width " + width);
        };
    }

    private static int[] indices(MeshPrimitiveModel primitive, int count) {
        AccessorModel accessor = primitive.getIndices();
        int[] out;
        if (accessor == null) {
            out = new int[count];
            for (int i = 0; i < count; i++) {
                out[i] = i;
            }
            return out;
        }
        AccessorData data = accessor.getAccessorData();
        out = new int[accessor.getCount()];
        for (int i = 0; i < out.length; i++) {
            if (data instanceof AccessorIntData x) {
                out[i] = x.get(i, 0);
            } else if (data instanceof AccessorShortData x) {
                out[i] = x.get(i, 0) & 0xFFFF;
            } else if (data instanceof AccessorByteData x) {
                out[i] = x.get(i, 0) & 0xFF;
            } else {
                throw new IllegalArgumentException("index type " + data.getClass());
            }
        }
        return out;
    }

    private static AccessorModel floats(FloatList list, int count, ElementType type) {
        ByteBuffer buf = ByteBuffer.allocateDirect(list.size * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < list.size; i++) {
            buf.putFloat(list.get(i));
        }
        return accessor(buf.flip(), GltfConstants.GL_FLOAT, count, type);
    }

    private static AccessorModel ints(List<Integer> list) {
        ByteBuffer buf = ByteBuffer.allocateDirect(list.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i : list) {
            buf.putInt(i);
        }
        return accessor(buf.flip(), GltfConstants.GL_UNSIGNED_INT, list.size(), ElementType.SCALAR);
    }

    private static AccessorModel accessor(ByteBuffer data, int componentType, int count, ElementType type) {
        DefaultBufferModel buffer = new DefaultBufferModel();
        buffer.setBufferData(data);
        DefaultBufferViewModel view = new DefaultBufferViewModel(null);
        view.setBufferModel(buffer);
        view.setByteOffset(0);
        view.setByteLength(data.capacity());
        DefaultAccessorModel accessor = new DefaultAccessorModel(componentType, count, type);
        accessor.setBufferViewModel(view);
        accessor.setByteOffset(0);
        accessor.setAccessorData(AccessorDatas.create(accessor));
        return accessor;
    }

    private static final class FloatList {
        float[] data;
        int size;

        FloatList(float[] init) {
            data = Arrays.copyOf(init, init.length * 2 + 16);
            size = init.length;
        }

        float get(int i) {
            return data[i];
        }

        void add(float v) {
            if (size == data.length) {
                data = Arrays.copyOf(data, size * 2);
            }
            data[size++] = v;
        }
    }
}
