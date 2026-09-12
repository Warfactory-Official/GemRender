package com.wf.gemrender.render;

import org.joml.FrustumIntersection;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;

public final class PosedBound {
    private PosedBound() {
    }

    public static boolean test(FrustumIntersection frustum, Matrix4fc pose, Vector4fc sphere) {
        float x = sphere.x();
        float y = sphere.y();
        float z = sphere.z();

        float wx = Math.fma(pose.m00(), x, Math.fma(pose.m10(), y, Math.fma(pose.m20(), z, pose.m30())));
        float wy = Math.fma(pose.m01(), x, Math.fma(pose.m11(), y, Math.fma(pose.m21(), z, pose.m31())));
        float wz = Math.fma(pose.m02(), x, Math.fma(pose.m12(), y, Math.fma(pose.m22(), z, pose.m32())));

        return frustum.testSphere(wx, wy, wz, sphere.w() * longestAxis(pose));
    }

    public static float longestAxis(Matrix4fc pose) {
        float x = Math.fma(pose.m00(), pose.m00(), Math.fma(pose.m01(), pose.m01(), pose.m02() * pose.m02()));
        float y = Math.fma(pose.m10(), pose.m10(), Math.fma(pose.m11(), pose.m11(), pose.m12() * pose.m12()));
        float z = Math.fma(pose.m20(), pose.m20(), Math.fma(pose.m21(), pose.m21(), pose.m22() * pose.m22()));

        return (float) Math.sqrt(Math.max(x, Math.max(y, z)));
    }
}
