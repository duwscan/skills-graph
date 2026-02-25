package com.sk.skillsgraph.util;

import java.util.Arrays;

public final class PgVectorUtils {

    private PgVectorUtils() {
    }

    public static String toSql(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            builder.append(embedding[i]);
            if (i < embedding.length - 1) {
                builder.append(',');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    public static float[] fromSql(String pgVector) {
        if (pgVector == null || pgVector.isBlank() || "[]".equals(pgVector.trim())) {
            return new float[0];
        }

        String normalized = pgVector.trim();
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            throw new IllegalArgumentException("Invalid pgvector format: " + pgVector);
        }

        String[] parts = normalized.substring(1, normalized.length() - 1).split(",");
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .mapToDouble(Double::parseDouble)
                .collect(() -> new FloatArrayBuilder(parts.length), FloatArrayBuilder::add, FloatArrayBuilder::addAll)
                .toArray();
    }

    private static class FloatArrayBuilder {
        private float[] values;
        private int size;

        FloatArrayBuilder(int capacity) {
            this.values = new float[Math.max(capacity, 4)];
        }

        void add(double value) {
            ensureCapacity(size + 1);
            values[size++] = (float) value;
        }

        void addAll(FloatArrayBuilder other) {
            ensureCapacity(size + other.size);
            System.arraycopy(other.values, 0, values, size, other.size);
            size += other.size;
        }

        float[] toArray() {
            return Arrays.copyOf(values, size);
        }

        private void ensureCapacity(int targetSize) {
            if (targetSize <= values.length) {
                return;
            }
            int newLength = Math.max(values.length * 2, targetSize);
            values = Arrays.copyOf(values, newLength);
        }
    }
}
