package com.hms.service.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * Clinical documents on a private directory of the server's own filesystem.
 *
 * <p>The directory is deliberately not anywhere a web server publishes. There is no URL for a
 * patient's blood report -- the only way to read one is through the authenticated endpoint above
 * this class, which resolves the document tenant-scoped first. That makes the privacy property
 * structural rather than a setting somebody could switch off.
 *
 * <p>Keys are generated here from random UUIDs. Nothing in a stored path comes from a patient's
 * name, the original filename, or anything else a person could guess or that would leak who a
 * document belongs to if the directory listing were ever seen.
 */
@Component
public class LocalVpsClinicalDocumentStorage implements ClinicalDocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalVpsClinicalDocumentStorage.class);

    /** Only these may appear in a key we generate, so a key can never be a path expression. */
    private static final String KEY_CHARS = "[A-Za-z0-9_-]+";

    private final String configuredRoot;
    private final Environment environment;
    private Path storageRoot;

    public LocalVpsClinicalDocumentStorage(
            @Value("${hms.document-storage.root:}") String configuredRoot,
            Environment environment) {
        this.configuredRoot = configuredRoot;
        this.environment = environment;
    }

    @PostConstruct
    void initialise() {
        String root = configuredRoot == null ? "" : configuredRoot.trim();

        if (root.isEmpty()) {
            // Production must not quietly invent somewhere to put clinical records. A default
            // under the working directory survives exactly until the next deploy, and the
            // documents would be gone with no error ever having been raised.
            if (isProduction()) {
                throw new IllegalStateException(
                        "HMS_DOCUMENT_STORAGE_PATH is not set. Production requires an explicit "
                                + "private directory for patient documents, on persistent storage "
                                + "and outside anything the web server publishes.");
            }
            root = Paths.get(System.getProperty("java.io.tmpdir"), "hms-patient-documents")
                    .toAbsolutePath().toString();
            log.warn("hms.document-storage.root is not set; using a temporary directory for development only.");
        }

        Path configured = Paths.get(root).toAbsolutePath().normalize();
        try {
            // A symlinked root is refused outright rather than resolved: if the directory an
            // operator configured is itself a link, every containment check below would be
            // measuring against wherever that link happens to point today.
            if (Files.isSymbolicLink(configured)) {
                throw new IllegalStateException(
                        "The configured patient document directory is a symbolic link. Point "
                                + "HMS_DOCUMENT_STORAGE_PATH at a real directory.");
            }
            Files.createDirectories(configured);
            // Canonical from here on, so containment is compared against real locations rather
            // than the spelling of a path.
            this.storageRoot = configured.toRealPath();
            restrictPermissions(storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Patient document storage directory could not be prepared. Check that the "
                            + "service account owns it and that the volume is mounted.", e);
        }
        log.info("Patient document storage initialised.");
    }

    @Override
    public StoredDocument store(String hospitalRef, String patientRef, String extension,
                                InputStream content, long declaredSize) {
        String key = safeSegment(hospitalRef) + "/" + safeSegment(patientRef) + "/"
                + UUID.randomUUID() + "." + safeExtension(extension);

        Path target = resolveWithin(key);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            restrictPermissions(target.getParent());

            // Written aside first: a half-arrived upload must never be readable as a clinical
            // document, and the move below is atomic on the same filesystem.
            temp = Files.createTempFile(target.getParent(), ".incoming-", ".part");
            long written;
            // NOFOLLOW + CREATE_NEW: if anything replaced the temp entry with a link between its
            // creation and this write, the open fails rather than writing through it.
            try (java.io.OutputStream out = Files.newOutputStream(temp,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                written = content.transferTo(out);
            }

            // A fresh UUID should never collide; if it somehow does, refuse rather than overwrite
            // somebody else's record.
            if (Files.exists(target)) {
                throw new IllegalStateException("Storage key already in use");
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
            restrictPermissions(target);

            return new StoredDocument(key, written);
        } catch (IOException | RuntimeException e) {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { /* best effort */ }
            }
            // The path is deliberately absent from the message: it reaches an API client.
            throw new ClinicalDocumentStorageException("The document could not be stored.", e);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        Path path = resolveWithin(storageKey);
        try {
            // NOFOLLOW on the check as well: a regular file reached through a link is not ours.
            if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new ClinicalDocumentStorageException("The document file is missing.", null);
            }
            return Files.newInputStream(path, java.nio.file.StandardOpenOption.READ,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new ClinicalDocumentStorageException("The document could not be read.", e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            return Files.isRegularFile(resolveWithin(storageKey), java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolveWithin(storageKey));
        } catch (IOException | RuntimeException e) {
            // The caller is already unwinding a failed upload. Losing the file is not worse than
            // the failure that got us here, but an operator should be able to find the orphan.
            log.warn("Could not remove a partially stored clinical document; manual cleanup may "
                    + "be needed for key ending {}", tail(storageKey));
        }
    }

    /**
     * Turns a key into a path and proves it stayed inside the root.
     *
     * <p>Keys are generated by this class, so a key that does not look like one did not come from
     * here -- it came from a request. Both the shape check and the containment check are kept: the
     * first rejects the obvious attempts, the second is what actually holds if the first is ever
     * loosened.
     */
    Path resolveWithin(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Missing document storage key");
        }
        if (storageKey.contains("..") || storageKey.startsWith("/")
                || storageKey.indexOf('\\') >= 0 || storageKey.indexOf(' ') >= 0
                || storageKey.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        String[] segments = storageKey.split("/");
        if (segments.length != 3) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            boolean hasDot = i == 2 && segment.contains(".");
            String stem = hasDot ? segment.substring(0, segment.lastIndexOf('.')) : segment;
            String extension = hasDot ? segment.substring(segment.lastIndexOf('.') + 1) : "";
            if (!stem.matches(KEY_CHARS) || (hasDot && !extension.matches(KEY_CHARS))) {
                throw new IllegalArgumentException("Invalid document storage key");
            }
        }

        Path resolved = storageRoot.resolve(storageKey).normalize();
        // Lexical containment first -- cheap, and catches a key that spells its way out.
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        assertNoSymlinkEscape(resolved);
        return resolved;
    }

    /**
     * Lexical containment is not filesystem containment.
     *
     * <p>normalize() works on the text of a path. If a directory inside the root is a symbolic
     * link -- planted by an attacker who reached the disk, or by an operator restoring a backup
     * with cp -a -- then a key that looks perfectly well-behaved resolves through it and lands
     * outside. So every segment that already exists is checked for being a link, and the deepest
     * existing ancestor is resolved to its real location and re-checked against the real root.
     *
     * <p>This narrows but cannot close the gap between the check and the operation: a link
     * created in that window would not be seen. Refusing to follow links at all during the write
     * itself (NOFOLLOW on create) is what actually covers that, and the store path does both.
     */
    private void assertNoSymlinkEscape(Path resolved) {
        Path cursor = storageRoot;
        for (Path segment : storageRoot.relativize(resolved)) {
            cursor = cursor.resolve(segment);
            if (!Files.exists(cursor, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                break; // not created yet; nothing here can redirect us
            }
            if (Files.isSymbolicLink(cursor)) {
                throw new IllegalArgumentException("Invalid document storage key");
            }
            try {
                if (!cursor.toRealPath().startsWith(storageRoot)) {
                    throw new IllegalArgumentException("Invalid document storage key");
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid document storage key");
            }
        }
    }

    private static String safeSegment(String reference) {
        String cleaned = reference == null ? "" : reference.replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    private static String safeExtension(String extension) {
        String cleaned = extension == null ? "" : extension.toLowerCase().replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() ? "bin" : cleaned;
    }

    private static String tail(String key) {
        return key == null || key.length() < 8 ? "(unknown)" : key.substring(key.length() - 8);
    }

    /** Nothing outside the service account needs to read a patient's records. */
    private static void restrictPermissions(Path path) {
        try {
            if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
            Set<PosixFilePermission> permissions = Files.isDirectory(path)
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                             PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException e) {
            log.debug("Could not tighten permissions on a document storage path.");
        }
    }

    private boolean isProduction() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }

    /** Storage trouble, described without naming anything on disk. */
    public static class ClinicalDocumentStorageException extends RuntimeException {
        public ClinicalDocumentStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
