package com.hms.service.import_;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects explicit exclusions out of evaluation and stored review rows, without changing upload bytes. */
final class ImportColumnExclusions {
    static final ImportColumnExclusions NONE = new ImportColumnExclusions(Set.of());
    private final Set<Integer> indexes;

    private ImportColumnExclusions(Set<Integer> indexes) {
        this.indexes = Set.copyOf(indexes);
    }

    static ImportColumnExclusions validate(SheetHeader header, Map<String, String> mapping, List<String> excluded) {
        if (excluded.size() > ParserLimits.MAX_COLUMNS) throw new InvalidImportMappingException("Too many excluded columns.");
        Set<String> mapped = new HashSet<>();
        mapping.keySet().forEach(key -> mapped.add(SheetHeader.normalize(key)));
        Set<Integer> indexes = new HashSet<>();
        for (String name : excluded) {
            if (name == null || name.isBlank()) throw new InvalidImportMappingException("An excluded column name is blank.");
            if (name.length() > ParserLimits.MAX_CELL_CHARS) throw new InvalidImportMappingException("An excluded column name is too long.");
            int index = header.indexOf(name);
            if (index < 0) throw new InvalidImportMappingException("An excluded column is not in the source headers.");
            if (!indexes.add(index)) throw new InvalidImportMappingException("A source column is excluded more than once.");
            if (mapped.contains(SheetHeader.normalize(name))) throw new InvalidImportMappingException("A source column cannot be both mapped and excluded.");
        }
        return new ImportColumnExclusions(indexes);
    }

    SheetHeader header(SheetHeader source) {
        return indexes.isEmpty() ? source : new SheetHeader(source.sheetName(), project(source.display()));
    }

    ParsedRow row(ParsedRow source) {
        // Preserve parser validation problems and physical row numbers; exclusion never bypasses limits.
        return indexes.isEmpty() ? source : new ParsedRow(source.rowNum(), project(source.values()), source.problems());
    }

    private List<String> project(List<String> source) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            if (!indexes.contains(i)) result.add(source.get(i));
        }
        return result;
    }
}
