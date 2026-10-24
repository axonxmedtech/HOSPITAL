package com.hms.service.import_;

import com.hms.entity.import_.ImportEntityType;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The mapping the client sends to preview and commit is authoritative user input, not the
 * mapper's suggestion, and is validated as such: every target must be a writable field of the
 * {@link ImportFieldRegistry} (so {@code hospital_id}, {@code publicId}, the acknowledgement
 * columns and anything unknown are refused), no target may be claimed twice, no source header
 * may be mapped twice or be blank, and the whole thing is bounded by the parser's column limit.
 */
@Component
public class ImportMappingValidator {

    public static final int MAX_ENTRIES = ParserLimits.MAX_COLUMNS;
    private static final int MAX_HEADER_LENGTH = ParserLimits.MAX_CELL_CHARS;

    private final ImportFieldRegistry registry;

    public ImportMappingValidator(ImportFieldRegistry registry) {
        this.registry = registry;
    }

    /** @return a defensive, insertion-ordered copy of the validated mapping (source header → field key) */
    public Map<String, String> validate(Map<String, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            throw new InvalidImportMappingException("At least one column must be mapped to a patient field.");
        }
        if (mapping.size() > MAX_ENTRIES) {
            throw new InvalidImportMappingException("At most " + MAX_ENTRIES + " columns can be mapped.");
        }
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        boolean hasName = false;
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            String source = e.getKey();
            String target = e.getValue();
            if (source == null || source.isBlank()) {
                throw new InvalidImportMappingException("A mapped column has a blank header.");
            }
            if (source.length() > MAX_HEADER_LENGTH) {
                throw new InvalidImportMappingException("A mapped column header is too long.");
            }
            if (target == null || target.isBlank()) {
                throw new InvalidImportMappingException("Column \"" + source + "\" is mapped to a blank field.");
            }
            if (!registry.isWritable(ImportEntityType.PATIENT, target)) {
                throw new InvalidImportMappingException("Column \"" + source + "\" is mapped to \"" + target + "\", which is not an importable patient field.");
            }
            if (!sources.add(SheetHeader.normalize(source))) {
                throw new InvalidImportMappingException("Column \"" + source + "\" is mapped more than once.");
            }
            if (!targets.add(target)) {
                throw new InvalidImportMappingException("More than one column is mapped to \"" + target + "\".");
            }
            if ("name".equals(target)) hasName = true;
            out.put(source.strip(), target);
        }
        if (!hasName) {
            throw new InvalidImportMappingException("A column must be mapped to \"name\": every patient needs a full name.");
        }
        return out;
    }
}
