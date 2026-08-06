package com.hms.service.import_;

import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportEntityType;

import java.util.List;
import java.util.Map;

/**
 * One implementation per importable entity. Implementations are pure: evaluate() decides what
 * would happen to a row and never writes. Persisting is ImportEngine's job, which is what lets
 * dry-run and commit share exactly the same logic.
 */
public interface EntityImporter {

    ImportEntityType entityType();

    /**
     * @param row             header -> trimmed cell value, blanks present as ""
     * @param mapping         header -> field key
     * @param unmappedHeaders headers with no field, preserved as custom fields
     * @param hospitalId      always from the JWT, never from the file
     * @param rowNumber       1-based row number in the source file, for error reporting
     */
    RowOutcome evaluate(Map<String, String> row, Map<String, String> mapping,
                        List<String> unmappedHeaders, Long hospitalId, int rowNumber);
}
