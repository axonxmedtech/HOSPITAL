package com.hms.config;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow DDL renderer for the three unmapped legacy columns, never arbitrary metadata SQL. */
final class LegacyColumnDdl {
    private static final Pattern INTEGER_TYPE = Pattern.compile(
            "^(tinyint|smallint|mediumint|int|bigint)(?:\\(([0-9]{1,3})\\))?( unsigned)?$");
    private static final Pattern CHARACTER_TYPE = Pattern.compile("^(varchar|char)\\(([0-9]{1,5})\\)$");

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
        if (charset != null) ddl.append(" CHARACTER SET ").append(identifier(charset));
        if (collation != null) ddl.append(" COLLATE ").append(identifier(collation));
        ddl.append(" NULL DEFAULT ");
        Object defaultValue = metadata.get("COLUMN_DEFAULT");
        if (defaultValue == null) {
            ddl.append("NULL");
        } else {
            // MySQL does not support a parameter marker in ALTER ... DEFAULT. Encode the
            // value as bytes, not SQL text: quotes/backslashes/expressions cannot become code.
            // Conversion from UTF-8 preserves the value even for a latin1 target column.
            String hex = HexFormat.of().formatHex(defaultValue.toString().getBytes(StandardCharsets.UTF_8));
            ddl.append("(CONVERT(X'").append(hex).append("' USING utf8mb4))");
        }
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

    private static String identifier(Object value) {
        String text = value.toString();
        if (!text.matches("[a-zA-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Invalid legacy charset/collation identifier");
        }
        return text;
    }
}
