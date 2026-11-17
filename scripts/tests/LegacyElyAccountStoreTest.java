package com.ashmeet.hyperbackend.authenticator.legacy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Integration checks use production JSON, atomic filesystem writes, and a fake HTTP boundary. */
public final class LegacyElyAccountStoreTest {
    private static final String PROFILE = "9b30cbde-4a84-4bd6-9eb5-c76dfaf3fc73";
    private static final String CLIENT = "9d1dc1bc-4180-45b7-a831-90b145fbd7cf";
    private static final long NOW = 1794000000000L;
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("insane-ely-tests-");
        try {
            File account = root.resolve("account.json").toFile();
            JsonObject original = account();
            write(account, original);
            Path backup = root.resolve("original-backup.json");
            Files.copy(account.toPath(), backup);
            byte[] backupBytes = Files.readAllBytes(backup);
            FakeConnection network = new FakeConnection(200, success(PROFILE, "Player_2", "new.session"));
            JsonObject refreshed = LegacyElyAccountStore.refresh(account, NOW,
                    (access, client) -> ElyAuthApi.refresh(access, client, network::open));
            check(network.request().get("accessToken").getAsString().equals("old.session"), "saved access token used");
            check(network.request().get("clientToken").getAsString().equals(CLIENT), "original client identity used");
            check(network.endpoint.equals("https://authserver.ely.by/auth/refresh"), "legacy refresh uses fixed HTTPS endpoint");
            check(network.disconnected, "network connection closed");
            check(refreshed.get("username").getAsString().equals("Player_2"), "profile name refreshed");
            check(refreshed.get("accessToken").getAsString().equals("new.session"), "new token returned");
            check(refreshed.get("legacyEly").getAsBoolean(), "legacy mode preserved");
            check(refreshed.get("authType").getAsString().equals("elyby"), "provider preserved");
            check(!refreshed.get("isMicrosoft").getAsBoolean(), "legacy Ely never converted to Microsoft");
            check(refreshed.get("refreshToken").getAsString().equals("0"), "Yggdrasil never stored as OAuth refresh");
            check(refreshed.get("expiresAt").getAsLong() == NOW + 300000L, "bounded refresh interval stored");
            check(refreshed.get("skinPath").equals(original.get("skinPath")), "skin preserved");
            check(refreshed.get("capePath").equals(original.get("capePath")), "cape preserved");
            check(refreshed.get("custom").equals(original.get("custom")), "unknown fields preserved");
            check(read(account).equals(refreshed), "live account file synced atomically");
            check(Arrays.equals(backupBytes, Files.readAllBytes(backup)), "original backup untouched");
            byte[] saved = Files.readAllBytes(account.toPath());
            JsonObject cached = LegacyElyAccountStore.refresh(account, NOW + 1,
                    (access, client) -> { throw new AssertionError("Cached session must not refresh"); });
            check(cached.equals(refreshed), "recent session reused");
            check(Arrays.equals(saved, Files.readAllBytes(account.toPath())), "cache does not rewrite account");
            FakeConnection next = new FakeConnection(200, success(PROFILE, "Player_2", "next.session"));
            LegacyElyAccountStore.refresh(account, NOW + 300001L,
                    (access, client) -> ElyAuthApi.refresh(access, client, next::open));
            check(next.request().get("accessToken").getAsString().equals("new.session"), "next refresh uses persisted rotated token");
            check(Arrays.equals(backupBytes, Files.readAllBytes(backup)), "backup remains immutable after rotation");

            JsonObject oauth = account(); oauth.addProperty("legacyEly", false);
            unchangedFailure(account, oauth, "OAuth excluded");
            JsonObject local = account(); local.addProperty("authType", "local");
            unchangedFailure(account, local, "local excluded");
            JsonObject microsoft = account(); microsoft.addProperty("authType", "microsoft");
            unchangedFailure(account, microsoft, "Microsoft excluded");
            for (String field : new String[]{"accessToken", "clientToken"}) {
                for (String invalid : new String[]{"", "0", "token with spaces", "token\n"}) {
                    JsonObject value = account(); value.addProperty(field, invalid);
                    value.addProperty("expiresAt", NOW + 1000L);
                    unchangedFailure(account, value, "invalid " + field + " rejected even when cached");
                }
                JsonObject value = account(); value.remove(field);
                unchangedFailure(account, value, "missing " + field + " rejected");
                value = account(); value.addProperty(field, 1234);
                unchangedFailure(account, value, "numeric " + field + " rejected");
            }
            JsonObject invalidProfile = account(); invalidProfile.addProperty("profileId", "bad-profile");
            unchangedFailure(account, invalidProfile, "invalid profile rejected");
            JsonObject invalidExpiry = account(); invalidExpiry.addProperty("expiresAt", "secret-bad-time");
            unchangedFailure(account, invalidExpiry, "invalid expiry safely rejected");

            write(account, account());
            byte[] before = Files.readAllBytes(account.toPath());
            FakeConnection revoked = new FakeConnection(403, "{\"errorMessage\":\"secret-response\"}");
            fails(() -> LegacyElyAccountStore.refresh(account, NOW,
                    (access, client) -> ElyAuthApi.refresh(access, client, revoked::open)), "revoked session requires sign in");
            check(Arrays.equals(before, Files.readAllBytes(account.toPath())), "failed refresh preserves original session");
            FakeConnection switched = new FakeConnection(200, success("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Other", "new.session"));
            fails(() -> LegacyElyAccountStore.refresh(account, NOW,
                    (access, client) -> ElyAuthApi.refresh(access, client, switched::open)), "profile switch rejected");
            check(Arrays.equals(before, Files.readAllBytes(account.toPath())), "profile mismatch cannot overwrite account");

            JsonObject future = account(); future.addProperty("expiresAt", NOW + 86400000L);
            write(account, future);
            FakeConnection futureNetwork = new FakeConnection(200, success(PROFILE, "Player_1", "future.session"));
            LegacyElyAccountStore.refresh(account, NOW,
                    (access, client) -> ElyAuthApi.refresh(access, client, futureNetwork::open));
            check(futureNetwork.disconnected, "old long OAuth-style expiry cannot skip legacy validation");

            write(account, account()); before = Files.readAllBytes(account.toPath());
            Thread.currentThread().interrupt();
            try { fails(() -> LegacyElyAccountStore.refresh(account, NOW, noNetwork()), "interrupted refresh cancelled"); }
            finally { Thread.interrupted(); }
            check(Arrays.equals(before, Files.readAllBytes(account.toPath())), "cancellation keeps session");
            try {
                fails(() -> LegacyElyAccountStore.refresh(account, NOW, (access, client) -> {
                    ElyAuthApi.Session result = session();
                    Thread.currentThread().interrupt();
                    return result;
                }), "cancellation after request prevents commit");
            } finally { Thread.interrupted(); }
            check(Arrays.equals(before, Files.readAllBytes(account.toPath())), "cancelled response cannot replace session");

            JsonObject replacement = account(); replacement.addProperty("skinPath", "/private/new-skin.png");
            fails(() -> LegacyElyAccountStore.refresh(account, NOW, (access, client) -> {
                write(account, replacement); return session();
            }), "concurrent edit detected");
            check(read(account).equals(replacement), "concurrent edit not overwritten");
            write(account, account());
            fails(() -> LegacyElyAccountStore.refresh(account, NOW, (access, client) -> {
                Files.delete(account.toPath()); return session();
            }), "removed account detected");
            check(!account.exists(), "external deletion cannot resurrect account");

            verifyDeleteRace(account);
            write(account, account());
            Path link = root.resolve("symlink.json"); Files.createSymbolicLink(link, account.toPath());
            fails(() -> LegacyElyAccountStore.refresh(link.toFile(), NOW, noNetwork()), "symlink refused");
            Files.write(account.toPath(), new byte[1024 * 1024 + 1]);
            fails(() -> LegacyElyAccountStore.refresh(account, NOW, noNetwork()), "oversized file refused");
            Files.write(account.toPath(), "not-json-secret".getBytes(StandardCharsets.UTF_8));
            fails(() -> LegacyElyAccountStore.refresh(account, NOW, noNetwork()), "malformed file safe error");
            check(!Files.list(root).anyMatch(path -> path.getFileName().toString().startsWith(".ely-session-")), "no staged credentials left after operations");
            System.out.println("LegacyElyAccountStore: " + checks + " checks passed (real files, fake HTTP, no live accounts).");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyDeleteRace(File file) throws Exception {
        write(file, account());
        CountDownLatch refreshing = new CountDownLatch(1), release = new CountDownLatch(1), deleting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread refresh = new Thread(() -> {
            try {
                LegacyElyAccountStore.refresh(file, NOW, (access, client) -> {
                    refreshing.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test release timed out"); }
                    catch (InterruptedException error) { throw new IOException("Test interrupted"); }
                    return session();
                });
            } catch (Throwable error) { failure.set(error); }
        });
        Thread deletion = new Thread(() -> { deleting.countDown(); LegacyElyAccountStore.delete(file); });
        refresh.start();
        try {
            check(refreshing.await(5, TimeUnit.SECONDS), "refresh started");
            deletion.start();
            check(deleting.await(5, TimeUnit.SECONDS), "delete requested");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (deletion.getState() != Thread.State.BLOCKED && deletion.isAlive() && System.nanoTime() < deadline) Thread.yield();
            check(deletion.getState() == Thread.State.BLOCKED, "delete serialized behind refresh");
            check(file.isFile(), "live file retained until refresh finishes");
        } finally {
            release.countDown(); refresh.join(5000); deletion.join(5000);
        }
        check(!refresh.isAlive() && !deletion.isAlive(), "both operations complete");
        check(failure.get() == null, "refresh succeeded before deletion");
        check(!file.exists(), "account deletion wins without resurrection");
    }

    private static JsonObject account() {
        JsonObject value = new JsonObject();
        value.addProperty("username", "Player_1"); value.addProperty("profileId", PROFILE);
        value.addProperty("accessToken", "old.session"); value.addProperty("clientToken", CLIENT);
        value.addProperty("legacyEly", true); value.addProperty("isElyBy", true);
        value.addProperty("authType", "elyby"); value.addProperty("expiresAt", 0);
        value.addProperty("skinPath", "/private/skin.png"); value.addProperty("capePath", "/private/cape.png");
        value.add("custom", JsonParser.parseString("{\"keep\":[1,true,\"value\"]}"));
        return value;
    }
    private static String success(String profile, String username, String token) {
        JsonObject value = new JsonObject(), selected = new JsonObject();
        value.addProperty("accessToken", token); value.addProperty("clientToken", CLIENT);
        selected.addProperty("id", profile); selected.addProperty("name", username);
        value.add("selectedProfile", selected); return value.toString();
    }
    private static ElyAuthApi.Session session() throws IOException {
        FakeConnection network = new FakeConnection(200, success(PROFILE, "Player_1", "new.session"));
        return ElyAuthApi.refresh("old.session", CLIENT, network::open);
    }
    private static LegacyElyAccountStore.Refresher noNetwork() {
        return (access, client) -> { throw new AssertionError("Unexpected network request"); };
    }
    private static void unchangedFailure(File file, JsonObject value, String message) throws Exception {
        write(file, value); byte[] before = Files.readAllBytes(file.toPath());
        fails(() -> LegacyElyAccountStore.refresh(file, NOW, noNetwork()), message);
        check(Arrays.equals(before, Files.readAllBytes(file.toPath())), message + " keeps file unchanged");
    }
    private static void fails(Action action, String message) throws Exception {
        try { action.run(); throw new AssertionError("Expected failure: " + message); }
        catch (IOException error) {
            check(!error.toString().contains("secret") && error.getCause() == null, message + " has safe error");
        }
    }
    private static void write(File file, JsonObject value) throws IOException { Files.write(file.toPath(), value.toString().getBytes(StandardCharsets.UTF_8)); }
    private static JsonObject read(File file) throws IOException { return JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private interface Action { void run() throws Exception; }
    private static final class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int status; final String body;
        String endpoint; boolean disconnected;
        FakeConnection(int status, String body) throws IOException { super(new URL("https://example.invalid")); this.status = status; this.body = body; }
        HttpURLConnection open(URL url) { endpoint = url.toString(); return this; }
        JsonObject request() { return JsonParser.parseString(new String(output.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject(); }
        @Override public OutputStream getOutputStream() { return output; }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
        @Override public InputStream getErrorStream() { return getInputStream(); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
