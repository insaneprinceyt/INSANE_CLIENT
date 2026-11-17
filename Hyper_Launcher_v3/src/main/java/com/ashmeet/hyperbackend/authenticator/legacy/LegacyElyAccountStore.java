package com.ashmeet.hyperbackend.authenticator.legacy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Refreshes migrated Yggdrasil sessions without converting them to OAuth or losing account fields. */
public final class LegacyElyAccountStore {
    public static final Object LOCK = new Object();
    private static final int LIMIT = 1024 * 1024;
    private LegacyElyAccountStore() { }

    public static JsonObject refresh(File file, long now) throws IOException {
        return refresh(file, now, ElyAuthApi::refresh);
    }

    interface Refresher {
        ElyAuthApi.Session refresh(String accessToken, String clientToken) throws IOException;
    }

    static JsonObject refresh(File file, long now, Refresher refresher) throws IOException {
        synchronized (LOCK) {
            checkInterrupted();
            byte[] original = read(file);
            JsonObject account = object(original);
            try {
                if (!account.has("legacyEly") || !account.get("legacyEly").getAsBoolean()
                        || !"elyby".equals(account.get("authType").getAsString())) {
                    throw new IOException("Select a migrated Ely.by account.");
                }
                String oldToken = string(account, "accessToken");
                String client = string(account, "clientToken");
                String profile = ElyAuthApi.canonicalUuid(string(account, "profileId"));
                if ("0".equals(oldToken) || "0".equals(client)
                        || !ElyAuthApi.validToken(oldToken) || !ElyAuthApi.validToken(client)
                        || profile == null)
                    throw new IOException("This Ely.by session is unavailable. Sign in again.");
                long expiresAt = account.has("expiresAt") ? account.get("expiresAt").getAsLong() : 0;
                if (expiresAt > now && expiresAt <= now + 5 * 60 * 1000L) return account;
                ElyAuthApi.Session session = refresher.refresh(oldToken, client);
                if (!session.profileId.replace("-", "").equalsIgnoreCase(profile.replace("-", "")))
                    throw new IOException("Ely.by returned a different account. Sign in again.");
                checkInterrupted();
                if (!Arrays.equals(original, read(file)))
                    throw new IOException("The Ely.by account changed during refresh. Try again.");
                account.addProperty("accessToken", session.accessToken);
                account.addProperty("clientToken", session.clientToken);
                account.addProperty("profileId", session.profileId);
                account.addProperty("username", session.username);
                account.addProperty("isMicrosoft", false);
                account.addProperty("refreshToken", "0");
                // Yggdrasil publishes no expiry; five minutes is our refresh interval only.
                account.addProperty("expiresAt", now + 5 * 60 * 1000L);
                atomicWrite(file, account.toString().getBytes(StandardCharsets.UTF_8));
                return account;
            } catch (RuntimeException error) {
                throw new IOException("The migrated Ely.by account is invalid. Sign in again.");
            }
        }
    }

    /** The same lock as refresh prevents an in-flight refresh from recreating a removed account. */
    public static boolean delete(File file) {
        synchronized (LOCK) {
            return file != null && file.delete();
        }
    }

    private static String string(JsonObject account, String name) {
        if (!account.get(name).isJsonPrimitive() || !account.get(name).getAsJsonPrimitive().isString())
            throw new IllegalStateException();
        return account.get(name).getAsString();
    }

    private static byte[] read(File file) throws IOException {
        if (file == null || !file.isFile() || Files.isSymbolicLink(file.toPath()))
            throw new IOException("This Ely.by account was removed. Sign in again.");
        if (file.length() > LIMIT) throw new IOException("The Ely.by account file is too large.");
        try (FileInputStream input = new FileInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > LIMIT) throw new IOException("The Ely.by account file is too large.");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static JsonObject object(byte[] bytes) throws IOException {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException | StackOverflowError error) {
            throw new IOException("The migrated Ely.by account is invalid. Sign in again.");
        }
    }

    private static void atomicWrite(File file, byte[] bytes) throws IOException {
        Path target = file.toPath();
        Path staged = Files.createTempFile(target.toAbsolutePath().getParent(), ".ely-session-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(staged.toFile())) {
                output.write(bytes);
                output.getFD().sync();
            }
            checkInterrupted();
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Ely.by refresh cancelled.");
    }
}
