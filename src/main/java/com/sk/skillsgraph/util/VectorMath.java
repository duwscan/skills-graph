package com.sk.skillsgraph.util;

public final class VectorMath {

    private VectorMath() {
    }

    public static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0.0D;
        }

        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0D || rightNorm == 0.0D) {
            return 0.0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
