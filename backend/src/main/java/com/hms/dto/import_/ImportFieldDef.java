package com.hms.dto.import_;

import java.util.List;

/**
 * One writable target field of a patient import. {@code synonyms} are header spellings seen in
 * legacy exports, stored already normalised (see {@code ColumnMapper}); they drive mapping
 * <i>suggestions</i> only — the administrator confirms or overrides every one, and nothing in the
 * registry ever decides patient identity or writes a row.
 */
public record ImportFieldDef(String key, String label, boolean required, List<String> synonyms) {}
