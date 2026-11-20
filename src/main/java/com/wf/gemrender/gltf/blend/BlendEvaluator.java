package com.wf.gemrender.gltf.blend;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import java.util.Arrays;

import java.util.List;

import static com.wf.gemrender.gltf.NodeTable.*;

/**
 * {@link AnimationBlend} -> node state ({@link NodeTable} layout), ready for
 * {@link com.wf.gemrender.gltf.GltfPose#evaluate(com.wf.gemrender.gltf.GltfPaletteLayout, float[],
 * org.joml.Matrix4f[], com.wf.gemrender.gltf.morph.GltfMorphLayout, float[],
 * com.wf.gemrender.gltf.GltfPose.Scratch)}.
 *
 * <p>Override: per node {@code e_i = weight_i * mask_i(node)}; translation, scale and morph weights
 * {@code sum(e_i x_i) / max(1, sum e_i)}, rest filling {@code 1 - sum e_i}; rotation the same sum with
 * each quaternion flipped into the running sum's hemisphere, then normalized (nlerp).
 */
public final class BlendEvaluator {
    private static final float MIN_REFERENCE_SCALE = 1.0e-6f;

    private BlendEvaluator() {
    }

    public static void evaluate(NodeTable table, AnimationBlend blend, float[] out, Scratch scratch) {
        int floats = table.scratchFloats();
        blend.resolve();
        table.resetToRest(out);

        int layers = blend.layerCount();
        if (blend.isSingleClip()) {
            GltfAnimation clip = blend.clip(0);
            if (clip != null) {
                clip.apply(blend.resolvedTime(0), out);
            }
            return;
        }

        float[] sample = scratch.sample(floats);
        boolean anyOverride = false;
        for (int i = 0; i < layers; i++) {
            if (blend.mode(i) == BlendMode.OVERRIDE && blend.weight(i) > 0.0f) {
                anyOverride = true;
                break;
            }
        }

        if (anyOverride) {
            float[] acc = scratch.acc(floats);
            float[] total = scratch.total(table.nodeCount());
            Arrays.fill(acc, 0, floats, 0.0f);
            Arrays.fill(total, 0, table.nodeCount(), 0.0f);
            for (int i = 0; i < layers; i++) {
                if (blend.mode(i) != BlendMode.OVERRIDE || blend.weight(i) <= 0.0f) {
                    continue;
                }
                sample(table, blend, i, sample);
                accumulate(table, sample, blend.weight(i), blend.mask(i), acc, total);
            }
            finish(table, acc, total, out);
        }

        for (int i = 0; i < layers; i++) {
            if (blend.mode(i) != BlendMode.ADDITIVE || blend.weight(i) <= 0.0f) {
                continue;
            }
            AdditiveReference reference = blend.reference(i);
            reference.checkFits(table);
            sample(table, blend, i, sample);
            add(table, sample, reference.state(), blend.weight(i), blend.mask(i), out);
        }

        Inertialization inertial = blend.inertial();
        if (inertial != null) {
            inertial.checkFits(table);
            blend.inertialMask()
                    .checkFits(table);
            inertial.apply(out, blend.inertialElapsed(), blend.inertialWeight(), blend.inertialMask());
        }
    }

    private static void sample(NodeTable table, AnimationBlend blend, int layer, float[] sample) {
        blend.mask(layer)
                .checkFits(table);
        table.resetToRest(sample);
        GltfAnimation clip = blend.clip(layer);
        if (clip == null) {
            return;
        }
        float time = blend.resolvedTime(layer);
        BlendMask mask = blend.mask(layer);
        if (mask.isAll()) {
            clip.apply(time, sample);
            return;
        }
        List<PoseDriver> drivers = clip.drivers();
        int trsFloats = table.nodeCount() * TRS_STRIDE;
        for (int i = 0, n = drivers.size(); i < n; i++) {
            PoseDriver driver = drivers.get(i);
            int offset = driver.offset();
            int slot = offset < 0 ? -1 : offset < trsFloats ? offset / TRS_STRIDE : table.slotOfOffset(offset);
            if (slot < 0 || mask.weight(slot) > 0.0f) {
                driver.apply(time, sample);
            }
        }
    }

    private static void accumulate(NodeTable table, float[] sample, float weight, BlendMask mask, float[] acc,
                                   float[] total) {
        int count = table.nodeCount();
        boolean all = mask.isAll();
        for (int n = 0; n < count; n++) {
            float e = all ? weight : weight * mask.weight(n);
            if (e <= 0.0f) {
                continue;
            }
            total[n] += e;
            int s = n * TRS_STRIDE;
            acc[s] += e * sample[s];
            acc[s + 1] += e * sample[s + 1];
            acc[s + 2] += e * sample[s + 2];
            int r = s + ROTATION;
            float sign = acc[r] * sample[r] + acc[r + 1] * sample[r + 1] + acc[r + 2] * sample[r + 2]
                    + acc[r + 3] * sample[r + 3] < 0.0f ? -e : e;
            acc[r] += sign * sample[r];
            acc[r + 1] += sign * sample[r + 1];
            acc[r + 2] += sign * sample[r + 2];
            acc[r + 3] += sign * sample[r + 3];
            int c = s + SCALE;
            acc[c] += e * sample[c];
            acc[c + 1] += e * sample[c + 1];
            acc[c + 2] += e * sample[c + 2];
            int base = table.weightBase(n);
            for (int k = 0, m = table.weightCount(n); k < m; k++) {
                acc[base + k] += e * sample[base + k];
            }
        }
    }

    /**
     * {@code out} holds rest on entry.
     */
    private static void finish(NodeTable table, float[] acc, float[] total, float[] out) {
        int count = table.nodeCount();
        for (int n = 0; n < count; n++) {
            float w = total[n];
            if (w <= 0.0f) {
                continue;
            }
            int s = n * TRS_STRIDE;
            int base = table.weightBase(n);
            int morphs = table.weightCount(n);
            if (w < 1.0f) {
                float rest = 1.0f - w;
                for (int k = 0; k < 3; k++) {
                    acc[s + k] += rest * out[s + k];
                    acc[s + SCALE + k] += rest * out[s + SCALE + k];
                }
                int r = s + ROTATION;
                float sign = acc[r] * out[r] + acc[r + 1] * out[r + 1] + acc[r + 2] * out[r + 2]
                        + acc[r + 3] * out[r + 3] < 0.0f ? -rest : rest;
                for (int k = 0; k < 4; k++) {
                    acc[r + k] += sign * out[r + k];
                }
                for (int k = 0; k < morphs; k++) {
                    acc[base + k] += rest * out[base + k];
                }
                w = 1.0f;
            }
            float inverse = 1.0f / w;
            for (int k = 0; k < 3; k++) {
                out[s + k] = acc[s + k] * inverse;
                out[s + SCALE + k] = acc[s + SCALE + k] * inverse;
            }
            for (int k = 0; k < morphs; k++) {
                out[base + k] = acc[base + k] * inverse;
            }
            int r = s + ROTATION;
            float qx = acc[r], qy = acc[r + 1], qz = acc[r + 2], qw = acc[r + 3];
            float length = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            if (length > 0.0f) {
                out[r] = qx / length;
                out[r + 1] = qy / length;
                out[r + 2] = qz / length;
                out[r + 3] = qw / length;
            }
        }
    }

    private static void add(NodeTable table, float[] sample, float[] reference, float weight, BlendMask mask,
                            float[] out) {
        int count = table.nodeCount();
        boolean all = mask.isAll();
        for (int n = 0; n < count; n++) {
            float e = all ? weight : weight * mask.weight(n);
            if (e <= 0.0f) {
                continue;
            }
            int s = n * TRS_STRIDE;
            for (int k = 0; k < 3; k++) {
                out[s + k] += e * (sample[s + k] - reference[s + k]);
                int c = s + SCALE + k;
                float ref = reference[c];
                if (Math.abs(ref) > MIN_REFERENCE_SCALE) {
                    out[c] *= 1.0f + e * (sample[c] / ref - 1.0f);
                }
            }

            int r = s + ROTATION;
            float rx = -reference[r], ry = -reference[r + 1], rz = -reference[r + 2], rw = reference[r + 3];
            float sx = sample[r], sy = sample[r + 1], sz = sample[r + 2], sw = sample[r + 3];
            float dx = rw * sx + rx * sw + ry * sz - rz * sy;
            float dy = rw * sy - rx * sz + ry * sw + rz * sx;
            float dz = rw * sz + rx * sy - ry * sx + rz * sw;
            float dw = rw * sw - rx * sx - ry * sy - rz * sz;
            if (dw < 0.0f) {
                dx = -dx;
                dy = -dy;
                dz = -dz;
                dw = -dw;
            }
            if (e != 1.0f) {
                dx *= e;
                dy *= e;
                dz *= e;
                dw = 1.0f - e + e * dw;
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz + dw * dw);
                dx /= length;
                dy /= length;
                dz /= length;
                dw /= length;
            }
            float bx = out[r], by = out[r + 1], bz = out[r + 2], bw = out[r + 3];
            out[r] = bw * dx + bx * dw + by * dz - bz * dy;
            out[r + 1] = bw * dy - bx * dz + by * dw + bz * dx;
            out[r + 2] = bw * dz + bx * dy - by * dx + bz * dw;
            out[r + 3] = bw * dw - bx * dx - by * dy - bz * dz;

            int base = table.weightBase(n);
            for (int k = 0, m = table.weightCount(n); k < m; k++) {
                out[base + k] += e * (sample[base + k] - reference[base + k]);
            }
        }
    }

    /**
     * Working buffers; one per thread, grown once.
     */
    public static final class Scratch {
        private float[] sample = new float[0];
        private float[] acc = new float[0];
        private float[] total = new float[0];

        float[] sample(int floats) {
            if (sample.length < floats) {
                sample = new float[floats];
            }
            return sample;
        }

        float[] acc(int floats) {
            if (acc.length < floats) {
                acc = new float[floats];
            }
            return acc;
        }

        float[] total(int nodes) {
            if (total.length < nodes) {
                total = new float[nodes];
            }
            return total;
        }
    }
}
