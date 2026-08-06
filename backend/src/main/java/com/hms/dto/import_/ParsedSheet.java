package com.hms.dto.import_;

import java.util.List;
import java.util.Map;

/**
 * A sheet reduced to a header list and one map per data row, keyed by header text.
 * Values are always present as strings; a blank cell is "" and never a missing key, so that
 * "blank stays blank" is representable rather than indistinguishable from "column absent".
 */
public record ParsedSheet(
        String sheetName,
        List<String> headers,
        List<Map<String, String>> rows
) { }
