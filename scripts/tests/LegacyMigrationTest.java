package com.ashmeet.hyperlauncher.migration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

/** Real-file migration tests. Uses synthetic credentials and never contacts a provider. */
public final class LegacyMigrationTest {
    private static int checks;
    private static final String LOCAL = "{\"username\":\"Player\",\"accessToken\":\"0\",\"profileId\":\"original-id\"}";
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void equal(Object wanted, Object actual, String message) { check(wanted.equals(actual), message + ": " + actual); }
    private interface Action { void run() throws Exception; }
    private static void fails(Action action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); }
        catch (IOException expected) { checks++; check(!expected.toString().contains("secret-fixture"), "Errors redact credentials"); }
    }
    private static void put(File file, String value) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
    private static String text(File file) throws IOException { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    private static JsonObject json(File file) throws IOException { return JsonParser.parseString(text(file)).getAsJsonObject(); }
    private static String field(JsonObject value, String key) { return value.get(key).getAsString(); }
    private static String id(String key) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b & 255));
        return "insane-legacy-" + hex.substring(0, 24);
    }
    private static final class Fixture implements AutoCloseable {
        final Path root = Files.createTempDirectory("insane-migration-test-");
        final File data = root.resolve("private").toFile(), game = root.resolve("game").toFile();
        Fixture() throws IOException { Files.createDirectories(data.toPath()); Files.createDirectories(game.toPath()); }
        File account(String name) { return new File(data, "accounts/" + name); }
        File profiles() { return new File(game, ".minecraft/launcher_profiles.json"); }
        File metadata(String key) throws Exception { return new File(game, "instances/" + id(key) + "/hyper_instance.json"); }
        LegacyMigration.Result migrate(String account, String profile) throws IOException { return new LegacyMigration(data, game).migrate(account, profile); }
        public void close() throws IOException {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
    private static void allAccountsAndAppearance() throws Exception {
        try (Fixture f = new Fixture()) {
            put(f.account("local.json"), LOCAL);
            String ms = "{\"username\":\"MicrosoftUser\",\"accessToken\":\"secret-fixture\",\"isMicrosoft\":true,\"msaRefreshToken\":\"refresh-fixture\",\"extra\":\"keep\"}";
            String ely = "{\"username\":\"ElyUser\",\"accessToken\":\"ely-fixture\",\"isElyBy\":true,\"clientToken\":\"client-fixture\",\"profileId\":\"ely-id\"}";
            put(f.account("ms.json"), ms); put(f.account("ely.json"), ely);
            String playerId = UUID.nameUUIDFromBytes("OfflinePlayer:Player".getBytes(StandardCharsets.UTF_8)).toString();
            File appearance = new File(f.data, "local-appearance/" + playerId);
            String texture = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png";
            put(new File(appearance, texture), "existing texture bytes");
            put(new File(appearance, "appearance.json"), "{\"skin\":\"" + texture + "\",\"cape\":\"" + texture + "\",\"slim\":true}");
            LegacyMigration.Result result = f.migrate("/old/path/ms", "");
            equal(3, result.accounts, "All old account providers converted");
            equal("ms.json", result.selectedAccount, "Old selected file normalized");
            JsonObject local = json(f.account("local.json"));
            equal("local", field(local, "authType"), "Local auth type");
            equal("original-id", field(local, "profileId"), "Local identity preserved");
            equal(new File(appearance, texture).getAbsolutePath(), field(local, "skinPath"), "Existing skin reused");
            equal(field(local, "skinPath"), field(local, "capePath"), "Existing cape reused");
            equal("ALEX", field(local, "skinModel"), "Slim model mapped");
            JsonObject microsoft = json(f.account("ms.json"));
            equal("microsoft", field(microsoft, "authType"), "Microsoft auth type");
            equal("refresh-fixture", field(microsoft, "refreshToken"), "Microsoft refresh preserved");
            equal("keep", field(microsoft, "extra"), "Unknown account metadata retained");
            JsonObject legacy = json(f.account("ely.json"));
            equal("elyby", field(legacy, "authType"), "Ely auth type");
            check(legacy.get("legacyEly").getAsBoolean(), "Ely legacy adapter selected");
            equal("client-fixture", field(legacy, "clientToken"), "Ely client token preserved");
            equal(ms, text(new File(f.data, "insane-1x-backup/accounts/ms.json")), "Exact private account backup");
            equal(ely, text(new File(f.data, "insane-1x-backup/accounts/ely.json")), "Exact Ely backup");
            equal("existing texture bytes", text(new File(appearance, texture)), "Texture unmodified");
        }
    }
    private static void profilesAndData() throws Exception {
        try (Fixture f = new Fixture()) {
            String profiles = "{\"profiles\":{\"default\":{\"name\":\"Default\",\"lastVersionId\":\"latest-release\"},\"custom\":{\"name\":\"Fabric\",\"lastVersionId\":\"fabric-1.21.1\",\"gameDir\":\"amethyst://.minecraft/instances/pvp\",\"controlFile\":\"custom.json\",\"javaDir\":\"amethyst://Internal-21\",\"javaArgs\":\"-Xmx2G\",\"pojavRendererName\":\"vulkan\"},\"absolute\":{\"gameDir\":\"/old/absolute\",\"lastVersionId\":\"latest-snapshot\"}}}";
            put(f.profiles(), profiles);
            File world = new File(f.game, ".minecraft/instances/pvp/saves/world/level.dat");
            put(world, "world bytes"); put(new File(f.game, "controlmap/custom.json"), "control bytes");
            LegacyMigration.Result result = f.migrate("", "custom");
            equal(3, result.instances, "All profiles converted");
            equal(id("custom"), result.selectedInstance, "Selected profile mapped");
            JsonObject custom = json(f.metadata("custom"));
            equal(new File(f.game, ".minecraft/instances/pvp").getCanonicalPath(), field(custom, "legacyGameDirectory"), "Amethyst URI resolved");
            equal(new File(f.game, "controlmap/custom.json").getCanonicalPath(), field(custom, "legacyControlLayout"), "Control path resolved relative to controlmap");
            equal("Internal-21", field(custom, "selectedRuntime"), "Runtime retained");
            equal("-Xmx2G", field(custom, "jvmArgs"), "Java args retained");
            equal("vulkan", field(custom, "renderer"), "Renderer retained");
            equal("latest_release", field(json(f.metadata("default")), "versionId"), "Release selector mapped");
            equal(new File(f.game, ".minecraft").getCanonicalPath(), field(json(f.metadata("default")), "legacyGameDirectory"), "Default game path preserved");
            equal("latest_snapshot", field(json(f.metadata("absolute")), "versionId"), "Snapshot selector mapped");
            equal(new File(f.game, "/old/absolute").getCanonicalPath(), field(json(f.metadata("absolute")), "legacyGameDirectory"), "Old absolute path behavior preserved");
            equal(profiles, text(f.profiles()), "Original profiles unchanged");
            equal(profiles, text(new File(f.data, "insane-1x-backup/launcher_profiles.json")), "Exact profile backup");
            equal("world bytes", text(world), "World never moved or rewritten");
            String edited = text(f.metadata("custom")).replace("Fabric", "Renamed");
            put(f.metadata("custom"), edited);
            f.migrate("changed", "default");
            equal(edited, text(f.metadata("custom")), "Completed migration preserves user edits");
            equal(3, new File(f.game, "instances").list().length, "No duplicate wrappers on repeat");
        }
    }
    private static void interruptedRetry() throws Exception {
        try (Fixture f = new Fixture()) {
            put(f.account("player.json"), LOCAL);
            put(f.account("player.json.bak"), LOCAL.replace("original-id", "backup-id"));
            put(f.profiles(), "{\"profiles\":{\"valid\":{\"name\":\"First\"},\"broken\":false}}");
            fails(() -> f.migrate("player.json", "valid"), "Broken profile aborts migration");
            check(!new File(f.data, "insane-1x-backup/migration-v1.json").exists(), "Partial migration not marked complete");
            equal("backup-id", field(json(f.account("player.json")), "profileId"), "AtomicFile backup recovered");
            JsonObject current = json(f.account("player.json"));
            current.addProperty("accessToken", "refreshed-fixture");
            put(f.account("player.json"), current.toString());
            JsonObject wrapper = json(f.metadata("valid")); wrapper.addProperty("name", "Edited during retry");
            put(f.metadata("valid"), wrapper.toString());
            put(f.profiles(), "{\"profiles\":{\"valid\":{\"name\":\"First\"},\"broken\":{\"name\":\"Second\"}}}");
            LegacyMigration.Result result = f.migrate("player", "valid");
            equal("refreshed-fixture", field(json(f.account("player.json")), "accessToken"), "Retry never restores stale credentials");
            equal("Edited during retry", field(json(f.metadata("valid")), "name"), "Retry preserves wrapper edits");
            equal("player.json", result.selectedAccount, "Retry keeps account selection");
            equal(id("valid"), result.selectedInstance, "Retry keeps profile selection");
            equal(LOCAL, text(new File(f.data, "insane-1x-backup/accounts-incomplete/player.json")), "Incomplete original target retained");
            check(text(new File(f.data, "insane-1x-backup/launcher_profiles.json")).contains("false"), "First profile backup immutable");
        }
    }
    private static void rejectionAndFreshInstall() throws Exception {
        try (Fixture f = new Fixture()) {
            LegacyMigration.Result empty = f.migrate("", ""); equal(0, empty.accounts, "Fresh install has no accounts"); equal(0, empty.instances, "Fresh install has no legacy profiles");
        }
        try (Fixture f = new Fixture()) {
            put(f.account("bad.json"), "{\"accessToken\":\"secret-fixture\", broken}");
            fails(() -> f.migrate("", ""), "Malformed account rejected");
            check(text(f.account("bad.json")).contains("secret-fixture"), "Malformed input preserved");
        }
        try (Fixture f = new Fixture()) {
            put(f.profiles(), "{\"profiles\":{\"collision\":{}}}");
            File existing = new File(f.metadata("collision").getParentFile(), "world/level.dat"); put(existing, "keep");
            fails(() -> f.migrate("", "collision"), "Occupied wrapper rejected"); equal("keep", text(existing), "Existing wrapper files retained");
        }
        try (Fixture f = new Fixture()) {
            put(f.profiles(), "{\"profiles\":{\"overlap\":{\"gameDir\":\"instances/" + id("overlap") + "/saves\"}}}");
            fails(() -> f.migrate("", "overlap"), "Alias inside its deletable wrapper rejected");
        }
        try (Fixture f = new Fixture()) {
            File outside = f.root.resolve("outside.json").toFile(); put(outside, LOCAL);
            Files.createDirectories(f.account("linked.json").toPath().getParent());
            Files.createSymbolicLink(f.account("linked.json").toPath(), outside.toPath());
            fails(() -> f.migrate("", ""), "Symlink account rejected"); equal(LOCAL, text(outside), "Symlink target unchanged");
        }
        try (Fixture f = new Fixture()) {
            byte[] oversized = new byte[1024 * 1024 + 1]; Arrays.fill(oversized, (byte)' ');
            Files.createDirectories(f.account("huge.json").toPath().getParent()); Files.write(f.account("huge.json").toPath(), oversized);
            fails(() -> f.migrate("", ""), "Oversized metadata rejected"); equal((long)oversized.length, f.account("huge.json").length(), "Oversized original retained");
        }
        try (Fixture f = new Fixture()) {
            put(f.account("player.json"), LOCAL); Thread.currentThread().interrupt();
            try { fails(() -> f.migrate("", ""), "Interrupted migration stops"); } finally { Thread.interrupted(); }
            equal(LOCAL, text(f.account("player.json")), "Interrupted input unchanged");
            f.migrate("", ""); equal("local", field(json(f.account("player.json")), "authType"), "Interrupted migration can retry");
        }
    }
    public static void main(String[] args) throws Exception {
        allAccountsAndAppearance(); profilesAndData(); interruptedRetry(); rejectionAndFreshInstall();
        System.out.println("PASS LegacyMigration: " + checks + " real-file checks");
    }
}
