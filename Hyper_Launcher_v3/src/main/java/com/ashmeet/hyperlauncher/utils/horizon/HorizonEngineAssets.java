package com.ashmeet.hyperlauncher.utils.horizon;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Verified atomic preparation of the engine data shipped in the pinned Horizon AAR. */
public final class HorizonEngineAssets {
    public static final long BUNDLED_SIZE = 9121637L;
    public static final String BUNDLED_SHA256 = "3b68ff02893f8d1a380737d8008347b189b70796ef83679b12d4178a72ffde13";

    private HorizonEngineAssets() {}

    public interface Source { InputStream open() throws IOException; }

    public static synchronized void ensure(File destination, Source source,
                                           long expectedSize, String expectedSha256) throws IOException {
        if (matches(destination, expectedSize, expectedSha256)) return;
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Could not create the Rust engine data folder.");
        }
        File partial = File.createTempFile("engine-data-", ".partial", parent);
        try {
            MessageDigest digest = sha256();
            long count = 0;
            try (InputStream input = source.open(); FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Engine data preparation interrupted.");
                    count += read;
                    if (count > expectedSize) throw new IOException("Rust engine data size does not match this build.");
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
                output.getFD().sync();
            }
            if (count != expectedSize || !hex(digest.digest()).equals(expectedSha256)) {
                throw new IOException("Rust engine data verification failed. Reinstall the launcher APK.");
            }
            // Same-directory rename leaves the old complete file intact on copy/verification failure.
            Files.move(partial.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }

    private static boolean matches(File file, long size, String expected) throws IOException {
        if (!file.isFile() || file.length() != size) return false;
        MessageDigest digest = sha256();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest()).equals(expected);
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
