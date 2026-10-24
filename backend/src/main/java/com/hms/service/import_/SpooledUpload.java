package com.hms.service.import_;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;

/**
 * An upload written once to an owner-only temp file, so it can be read more than once — by the
 * .xlsx package reader (which must seek within the zip, and would otherwise buffer the whole
 * workbook on the heap), by the CSV reader, and later by the SHA-256 fingerprint — without ever
 * holding the bytes in memory.
 *
 * <p><b>Lifecycle, and who owns it.</b> Whoever calls {@link #spool} owns the file and must
 * {@link #close()} it, normally with try-with-resources; the parser never creates or deletes
 * temp files of its own, it only opens streams on this one. Closing deletes the file; if the
 * platform refuses the delete it is scheduled for JVM exit. The file holds patient data, so it
 * lives under the application's temp directory with an unpredictable name and, where the
 * filesystem supports POSIX permissions, owner read/write only.
 *
 * <p>No content is ever logged: not the name of the source file, not a byte of it.
 */
public final class SpooledUpload implements AutoCloseable {

    private static final String PREFIX = "hms-import-";
    private static final String SUFFIX = ".upload";

    private final Path file;
    private final long size;
    private boolean closed;

    private SpooledUpload(Path file, long size) {
        this.file = file;
        this.size = size;
    }

    /** Copies the stream to a fresh temp file under {@code java.io.tmpdir}. The stream is fully consumed, not closed. */
    public static SpooledUpload spool(InputStream in) throws IOException {
        return spool(in, Path.of(System.getProperty("java.io.tmpdir")));
    }

    public static SpooledUpload spool(InputStream in, Path directory) throws IOException {
        Path path = Files.createTempFile(directory, PREFIX, SUFFIX, ownerOnly(directory));
        try {
            // Write INTO the file createTempFile made (it carries the owner-only permissions);
            // Files.copy(REPLACE_EXISTING) would delete and recreate it with the default umask.
            long copied;
            try (java.io.OutputStream out = Files.newOutputStream(path)) {
                copied = in.transferTo(out);
            }
            return new SpooledUpload(path, copied);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    private static FileAttribute<?>[] ownerOnly(Path directory) {
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))};
        }
        return new FileAttribute<?>[0];
    }

    /** A fresh stream over the whole upload. The caller closes it. */
    public InputStream open() throws IOException {
        ensureOpen();
        return Files.newInputStream(file);
    }

    public long size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** The exact path, for the .xlsx package reader only. Callers must not move or delete it. */
    Path path() {
        ensureOpen();
        return file;
    }

    /** Lower-case hex SHA-256 of the bytes, streamed. Available to later phases for the retry guard. */
    public String sha256() throws IOException {
        ensureOpen();
        try (InputStream in = new DigestInputStream(open(), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every Java runtime", e);
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("The spooled upload has been closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            file.toFile().deleteOnExit();
        }
    }
}
