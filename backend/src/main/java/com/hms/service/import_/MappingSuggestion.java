package com.hms.service.import_;

import java.util.List;
import java.util.Map;

/**
 * What {@link ColumnMapper} proposes for a header row. UI assistance only: nothing here decides
 * patient identity or writes anything, and the administrator confirms or overrides every entry.
 *
 * @param mapping   display header → field key, for headers that matched exactly one field and
 *                  were the only header to do so
 * @param ambiguous display header → why it was left for a human (matched several fields, or
 *                  another header claims the same field)
 * @param unmapped  display headers with no suggestion at all, in file order; later phases
 *                  preserve their values verbatim
 */
public record MappingSuggestion(Map<String, String> mapping, Map<String, String> ambiguous, List<String> unmapped) {

    public MappingSuggestion {
        mapping = Map.copyOf(mapping);
        ambiguous = Map.copyOf(ambiguous);
        unmapped = List.copyOf(unmapped);
    }
}
