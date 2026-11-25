package com.hms.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * List endpoints never hand a client-chosen page size straight to the database: {@code size=100000}
 * would pull a whole table into memory in one request.
 */
class PagesCapTest {

    @Test
    void capsTheSizeButLeavesOrdinaryPagesAlone() {
        assertThat(Pages.cap(10)).isEqualTo(10);
        assertThat(Pages.cap(1000)).isEqualTo(1000);
        assertThat(Pages.cap(100_000)).isEqualTo(Pages.MAX_SIZE);
    }

    /** A new list endpoint that builds PageRequest.of(page, size) directly fails here. */
    @Test
    void noControllerBuildsAPageFromTheRawRequestSize() throws IOException {
        Pattern raw = Pattern.compile("PageRequest\\.of\\(\\s*\\w+\\s*,\\s*size\\b");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/hms/controller"))) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return raw.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(p -> p.getFileName().toString())
                    .toList();
        }
        assertThat(offenders).as("wrap the size in Pages.cap(size)").isEmpty();
    }
}
