package com.hms.dto.import_;

import java.util.List;

/**
 * One mappable target field. `synonyms` are lowercase header spellings seen in real legacy exports
 * and drive auto-mapping; the admin can always override the suggestion.
 */
public record ImportFieldDef(
        String key,
        String label,
        boolean required,
        List<String> synonyms
) { }
