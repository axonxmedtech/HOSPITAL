package com.hms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyColumnDdlTest {
    private Map<String, Object> metadata() {
        Map<String, Object> m = new HashMap<>();
        m.put("COLUMN_TYPE", "varchar(100)");
        m.put("CHARACTER_SET_NAME", "utf8mb4");
        m.put("COLLATION_NAME", "utf8mb4_bin");
        m.put("EXTRA", "");
        return m;
    }

    @Test
    void quotesAndBackslashesInDefaultAreDataNotSql() {
        Map<String, Object> m = metadata();
        String hostile = "café'\\; DROP TABLE billing; --";
        m.put("COLUMN_DEFAULT", hostile);
        String ddl = LegacyColumnDdl.nullableColumn("hospital_settings", "shift_mode", m);
        assertThat(ddl).doesNotContain(hostile, "DROP TABLE");
        assertThat(ddl).contains("CONVERT(X'" + HexFormat.of().formatHex(hostile.getBytes(StandardCharsets.UTF_8)));
        assertThat(ddl).contains("VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL");
    }

    @Test
    void rejectsSqlFragmentsInType() {
        Map<String, Object> m = metadata();
        m.put("COLUMN_TYPE", "varchar(100); DROP TABLE billing");
        assertThatThrownBy(() -> LegacyColumnDdl.nullableColumn("hospital_settings", "shift_mode", m))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSqlFragmentsInCharsetAndCollation() {
        for (String field : new String[]{"CHARACTER_SET_NAME", "COLLATION_NAME"}) {
            Map<String, Object> m = metadata();m.put(field, "utf8mb4; DROP TABLE billing");
            assertThatThrownBy(() -> LegacyColumnDdl.nullableColumn("hospital_settings", "shift_mode", m))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void unknownColumnAndGeneratedDefaultFailClosed() {
        Map<String, Object> m = metadata();
        assertThatThrownBy(() -> LegacyColumnDdl.nullableColumn("patients", "name", m))
                .isInstanceOf(IllegalArgumentException.class);
        m.put("EXTRA", "DEFAULT_GENERATED");
        assertThatThrownBy(() -> LegacyColumnDdl.nullableColumn("hospital_settings", "shift_mode", m))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAndLiteralNullDefaultsRemainDistinct() {
        Map<String, Object> m = metadata();
        assertThat(LegacyColumnDdl.nullableColumn("hospital_settings", "shift_mode", m)).endsWith("DEFAULT NULL");
        m.put("COLUMN_DEFAULT", "null");
        assertThat(LegacyColumnDdl.nullableColumn("hospital_settings", "shift_mode", m))
                .endsWith("DEFAULT (CONVERT(X'6e756c6c' USING utf8mb4))");
    }
}
