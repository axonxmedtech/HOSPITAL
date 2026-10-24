package com.hms.config;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow DDL renderer for the three unmapped legacy columns, never arbitrary metadata SQL. */
final class LegacyColumnDdl {
    private static final Pattern INTEGER_TYPE = Pattern.compile(
            "^(tinyint|smallint|mediumint|int|bigint)(?:\\(([0-9]{1,3})\\))?( unsigned)?$");
    private static final Pattern CHARACTER_TYPE = Pattern.compile("^(varchar|char)\\(([0-9]{1,5})\\)$");

    private static final Map<String, String> CHARSETS = Map.of(
            "utf8mb4", "utf8mb4", "utf8mb3", "utf8mb3", "utf8", "utf8",
            "latin1", "latin1", "ascii", "ascii");
    private static final Map<String, String> COLLATIONS = Map.ofEntries(
            Map.entry("utf8mb4_0900_ai_ci", "utf8mb4_0900_ai_ci"),
            Map.entry("utf8mb4_0900_as_cs", "utf8mb4_0900_as_cs"),
            Map.entry("utf8mb4_0900_bin", "utf8mb4_0900_bin"),
            Map.entry("utf8mb4_general_ci", "utf8mb4_general_ci"),
            Map.entry("utf8mb4_unicode_ci", "utf8mb4_unicode_ci"),
            Map.entry("utf8mb4_unicode_520_ci", "utf8mb4_unicode_520_ci"),
            Map.entry("utf8mb4_bin", "utf8mb4_bin"),
            Map.entry("utf8mb3_general_ci", "utf8mb3_general_ci"),
            Map.entry("utf8mb3_unicode_ci", "utf8mb3_unicode_ci"),
            Map.entry("utf8mb3_bin", "utf8mb3_bin"),
            Map.entry("utf8_general_ci", "utf8_general_ci"),
            Map.entry("utf8_unicode_ci", "utf8_unicode_ci"),
            Map.entry("utf8_bin", "utf8_bin"),
            Map.entry("latin1_swedish_ci", "latin1_swedish_ci"),
            Map.entry("latin1_general_ci", "latin1_general_ci"),
            Map.entry("latin1_general_cs", "latin1_general_cs"),
            Map.entry("latin1_bin", "latin1_bin"),
            Map.entry("ascii_general_ci", "ascii_general_ci"),
            Map.entry("ascii_bin", "ascii_bin"));

    private LegacyColumnDdl() {}

    static String nullableColumn(String table, String column, Map<String, Object> metadata) {
        String target = switch (table + "." + column) {
            case "hospital_settings.shift_mode" -> "ALTER TABLE `hospital_settings` MODIFY COLUMN `shift_mode` ";
            case "users.is_trainer" -> "ALTER TABLE `users` MODIFY COLUMN `is_trainer` ";
            case "doctors.is_anaesthetist" -> "ALTER TABLE `doctors` MODIFY COLUMN `is_anaesthetist` ";
            default -> throw new IllegalArgumentException("Unsupported legacy column");
        };
        if (String.valueOf(metadata.getOrDefault("EXTRA", "")).contains("GENERATED")) {
            throw new IllegalArgumentException("Generated legacy definitions require explicit review");
        }
        StringBuilder ddl = new StringBuilder(target).append(type(metadata.get("COLUMN_TYPE")));
        Object charset = metadata.get("CHARACTER_SET_NAME");
        Object collation = metadata.get("COLLATION_NAME");
        if (charset != null) ddl.append(" CHARACTER SET ").append(catalogToken(CHARSETS, charset));
        if (collation != null) ddl.append(" COLLATE ").append(catalogToken(COLLATIONS, collation));
        // Identifier/type fragments come only from constant catalogs or parsed integers.
        // The default is supplied separately through JDBC, never copied into SQL text.
        ddl.append(" NULL DEFAULT ?");
        return ddl.toString();
    }

    private static String type(Object value) {
        String text = String.valueOf(value).toLowerCase(java.util.Locale.ROOT);
        Matcher integer = INTEGER_TYPE.matcher(text);
        if (integer.matches()) {
            String name = switch (integer.group(1)) {
                case "tinyint" -> "TINYINT";
                case "smallint" -> "SMALLINT";
                case "mediumint" -> "MEDIUMINT";
                case "int" -> "INT";
                case "bigint" -> "BIGINT";
                default -> throw new IllegalArgumentException("Unsupported legacy integer type");
            };
            if (integer.group(2) != null) name += "(" + Integer.parseInt(integer.group(2)) + ")";
            return name + (integer.group(3) == null ? "" : " UNSIGNED");
        }
        Matcher character = CHARACTER_TYPE.matcher(text);
        if (character.matches()) {
            String name = "varchar".equals(character.group(1)) ? "VARCHAR" : "CHAR";
            int length = Integer.parseInt(character.group(2));
            if (length == 0) throw new IllegalArgumentException("Invalid legacy character length");
            return name + "(" + length + ")";
        }
        throw new IllegalArgumentException("Unsupported legacy type; schema review required");
    }

    private static String catalogToken(Map<String, String> catalog, Object value) {
        String token = catalog.get(value.toString());
        if (token == null) {
            throw new IllegalArgumentException("Unsupported legacy charset/collation; schema review required");
        }
        return token;
    }
}
