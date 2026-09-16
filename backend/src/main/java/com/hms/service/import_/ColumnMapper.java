package com.hms.service.import_;

import com.hms.dto.import_.ImportFieldDef;
import com.hms.entity.import_.ImportEntityType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Suggests header → field mappings from the {@link ImportFieldRegistry} synonyms. Suggestions
 * only; and where a header could mean two things, or two headers want the same field, nothing is
 * guessed — both are reported as ambiguous and left for the administrator.
 */
@Component
public class ColumnMapper {

    private final ImportFieldRegistry registry;

    public ColumnMapper(ImportFieldRegistry registry) {
        this.registry = registry;
    }

    public MappingSuggestion suggest(SheetHeader header, ImportEntityType type) {
        return suggest(header.display(), type);
    }

    public MappingSuggestion suggest(List<String> headers, ImportEntityType type) {
        List<ImportFieldDef> fields = registry.fieldsFor(type);

        // Pass 1: every field each header could be.
        Map<String, List<String>> candidates = new LinkedHashMap<>();
        for (String h : headers) {
            String norm = normalise(h);
            List<String> hits = new ArrayList<>();
            for (ImportFieldDef f : fields) {
                if (f.synonyms().contains(norm)) hits.add(f.key());
            }
            candidates.put(h, hits);
        }
        // Pass 2: how many headers want each field.
        Map<String, Integer> claims = new LinkedHashMap<>();
        for (List<String> hits : candidates.values()) {
            if (hits.size() == 1) claims.merge(hits.get(0), 1, Integer::sum);
        }

        Map<String, String> mapping = new LinkedHashMap<>();
        Map<String, String> ambiguous = new LinkedHashMap<>();
        List<String> unmapped = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : candidates.entrySet()) {
            String h = e.getKey();
            List<String> hits = e.getValue();
            if (hits.isEmpty()) {
                unmapped.add(h);
            } else if (hits.size() > 1) {
                ambiguous.put(h, "Could be any of: " + String.join(", ", hits));
            } else if (claims.get(hits.get(0)) > 1) {
                ambiguous.put(h, "More than one column looks like \"" + hits.get(0) + "\"");
            } else {
                mapping.put(h, hits.get(0));
            }
        }
        return new MappingSuggestion(mapping, ambiguous, unmapped);
    }

    /** Headers a confirmed mapping does not cover, in file order. */
    public List<String> unmapped(List<String> headers, Map<String, String> mapping) {
        List<String> out = new ArrayList<>();
        for (String h : headers) {
            if (!mapping.containsKey(h)) out.add(h);
        }
        return out;
    }

    /**
     * Lowercases, replaces punctuation with a space (never deletes it, so "Mob.No" becomes
     * "mob no" rather than "mobno"), then collapses whitespace. Registry synonyms are written in
     * this already-normalised form so they compare equal.
     */
    static String normalise(String header) {
        if (header == null) return "";
        return header.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[.\\-_/#():,;]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
