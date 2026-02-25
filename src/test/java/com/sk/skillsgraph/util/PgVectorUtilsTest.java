package com.sk.skillsgraph.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgVectorUtilsTest {

    @Test
    void toSqlSerializesVector() {
        assertThat(PgVectorUtils.toSql(new float[]{0.1f, 0.2f, 0.3f}))
                .isEqualTo("[0.1,0.2,0.3]");
    }

    @Test
    void fromSqlParsesVector() {
        float[] parsed = PgVectorUtils.fromSql("[0.1,0.2,0.3]");
        assertThat(parsed).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void roundTripSupportsLargeVectors() {
        float[] input = new float[1024];
        for (int i = 0; i < input.length; i++) {
            input[i] = i / 1000.0f;
        }

        String sql = PgVectorUtils.toSql(input);
        float[] output = PgVectorUtils.fromSql(sql);

        assertThat(output).hasSize(1024);
        assertThat(output[0]).isEqualTo(0.0f);
        assertThat(output[1023]).isEqualTo(1.023f);
    }

    @Test
    void emptyVectorIsHandled() {
        assertThat(PgVectorUtils.toSql(new float[0])).isEqualTo("[]");
        assertThat(PgVectorUtils.fromSql("[]")).isEmpty();
    }
}
