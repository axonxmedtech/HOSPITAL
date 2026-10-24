package com.hms.service.import_;

import java.io.IOException;
import java.util.List;

/** Public access to the package-private fixture builder for tests outside this package. */
public final class ImportTestFilesBridge {
    private ImportTestFilesBridge() {}

    public static byte[] xlsx(List<List<String>> rows) throws IOException {
        return ImportTestFiles.xlsx(rows);
    }
}
