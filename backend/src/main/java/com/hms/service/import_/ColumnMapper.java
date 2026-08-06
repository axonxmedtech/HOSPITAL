package com.hms.service.import_;

import com.hms.dto.import_.ImportFieldDef;
import com.hms.entity.ImportEntityType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Suggests header -> field mappings. Suggestions only: the admin confirms or overrides every one
 * before a dry-run happens, so a wrong guess is never destructive.
 */
@Component
public class ColumnMapper {

    private final ImportFieldRegistry registry;

    public ColumnMapper(ImportFieldRegistry registry) {
        this.registry = registry;
    }

    public Map<String, String> suggest(List<String> headers, ImportEntityType type) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> claimed = new HashSet<>();
        List<ImportFieldDef> fields = registry.fieldsFor(type);

        for (String header : headers) {
            String norm = normalise(header);
            if (norm.isEmpty()) continue;
            for (ImportFieldDef field : fields) {
                if (claimed.contains(field.key())) continue;
                if (field.synonyms().contains(norm)) {
                    result.put(header, field.key());
                    claimed.add(field.key());
                    break;
                }
            }
        }
        return result;
    }

    /** Headers with no target field. Their values are preserved verbatim in Patient.customFields. */
    public List<String> unmapped(List<String> headers, Map<String, String> mapping) {
        List<String> out = new ArrayList<>();
        for (String h : headers) {
            if (!mapping.containsKey(h) && !normalise(h).isEmpty()) {
                out.add(h);
            }
        }
        return out;
    }

    /**
     * Lowercases, replaces punctuation with a space (never deletes it, so "Mob.No" becomes
     * "mob no" rather than "mobno"), then collapses whitespace. Registry synonyms are written
     * in this already-normalised form so they compare equal.
     */
    private String normalise(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase()
                .replaceAll("[.\\-_/#():]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
