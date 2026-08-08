package com.hms.service.import_;

import com.hms.entity.ImportRowError;

import java.util.List;
import java.util.Map;

/**
 * Renders failed rows back into a CSV shaped for re-upload: the original headers, the original
 * values, plus a trailing _error column. Fixing those rows and uploading just this file leaves
 * everything that already imported untouched.
 */
public final class ImportCsvWriter {

    private ImportCsvWriter() { }

    public static String write(List<ImportRowError> errors, List<String> headers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(cell(headers.get(i)));
        }
        sb.append(",_error\n");

        for (ImportRowError e : errors) {
            Map<String, String> row = parseFlatJson(e.getRawRowJson());
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(cell(row.getOrDefault(headers.get(i), "")));
            }
            String msg = (e.getColumnName() == null ? "" : e.getColumnName() + ": ") + e.getMessage();
            sb.append(",").append(cell(msg)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Escapes for CSV and defuses spreadsheet formula injection. A legacy field beginning =, +, -
     * or @ is executable when opened in Excel, so it is prefixed with an apostrophe.
     */
    private static String cell(String raw) {
        String v = raw == null ? "" : raw;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Reads the flat {"k":"v"} objects written by ImportEngine.toJson. */
    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (json == null || json.length() < 2) return out;
        String body = json.substring(1, json.length() - 1);
        boolean inQuotes = false, escaped = false, readingKey = true;
        StringBuilder key = new StringBuilder(), val = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (escaped) {
                (readingKey ? key : val).append(unescape(c));
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && c == ':') {
                readingKey = false;
            } else if (!inQuotes && c == ',') {
                out.put(key.toString(), val.toString());
                key.setLength(0);
                val.setLength(0);
                readingKey = true;
            } else {
                (readingKey ? key : val).append(c);
            }
        }
        if (key.length() > 0) out.put(key.toString(), val.toString());
        return out;
    }

    private static char unescape(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> c;
        };
    }
}
