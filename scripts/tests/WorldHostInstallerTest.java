package com.ashmeet.hyperlauncher.utils.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

/** Real JARs and temporary directories; only the external HTTP boundary is substituted. */
public final class WorldHostInstallerTest {
    private static final String MC = "1.21.1";
    private static final String VERSION = "0.5.2+1.21.1-fabric";
    private static final String NAME = "world-host-" + VERSION + ".jar";
    private static final String CDN = "https://cdn.modrinth.com/data/2AIZDfYo/versions/blBlWzm7/";
    private static Path root;
    private static int checks, scenarios;
    private interface Action { void run() throws Exception; }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void scenario(String name, Action action) throws Exception {
        action.run();
        scenarios++;
        System.out.println("PASS " + name);
    }
    private static void failure(Action action, String message) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }
    private static File directory(String name) throws IOException {
        return Files.createDirectory(root.resolve(name)).toFile();
    }
    private static JsonArray array(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }
    private static JsonArray rows(JsonObject... values) {
        JsonArray result = new JsonArray();
        for (JsonObject value : values) result.add(value);
        return result;
    }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-512").digest(bytes)) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        }
        return result.toString();
    }
    private static byte[] jar(String id, String version) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            JsonObject manifest = new JsonObject();
            manifest.addProperty("schemaVersion", 1);
            manifest.addProperty("id", id);
            manifest.addProperty("version", version);
            JsonObject depends = new JsonObject();
            depends.addProperty("minecraft", ">=1.21 <1.21.2");
            depends.addProperty("fabricloader", "*");
            manifest.add("depends", depends);
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("fixture.txt"));
            zip.write("World Host test fixture, not executable mod code.".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static JsonObject metadata(byte[] bytes) throws Exception {
        JsonObject release = new JsonObject();
        release.addProperty("id", "blBlWzm7");
        release.addProperty("project_id", "2AIZDfYo");
        release.addProperty("version_number", VERSION);
        release.addProperty("version_type", "release");
        release.addProperty("status", "listed");
        release.addProperty("date_published", "2025-04-05T20:11:00Z");
        release.add("game_versions", array(MC));
        release.add("loaders", array("fabric"));
        release.add("dependencies", new JsonArray());
        JsonObject file = new JsonObject();
        file.addProperty("url", CDN + NAME.replace("+", "%2B"));
        file.addProperty("filename", NAME);
        file.addProperty("primary", true);
        file.addProperty("size", bytes.length);
        JsonObject hashes = new JsonObject();
        hashes.addProperty("sha512", hash(bytes));
        file.add("hashes", hashes);
        release.add("files", rows(file));
        return release;
    }
    private static JsonObject file(JsonObject release) {
        return release.getAsJsonArray("files").get(0).getAsJsonObject();
    }
    private static class Transport implements WorldHostInstaller.Transport {
        String api;
        byte[] artifact;
        final List<String> requested = new ArrayList<>();
        int closed;
        boolean breakDownload;
        Transport(JsonArray api, byte[] artifact) {
            this.api = api.toString();
            this.artifact = artifact;
        }
        @Override public InputStream open(String url) throws IOException {
            requested.add(url);
            if (url.startsWith("https://api.modrinth.com/v2/project/2AIZDfYo/version?")) {
                return stream(api.getBytes(StandardCharsets.UTF_8));
            }
            if (url.startsWith(CDN)) {
                if (breakDownload) return new InputStream() {
                    int read;
                    @Override public int read() throws IOException {
                        if (++read > 24) throw new IOException("Fixture connection lost");
                        return artifact[read - 1] & 255;
                    }
                    @Override public void close() { closed++; }
                };
                return stream(artifact);
            }
            throw new IOException("Unexpected URL in test fixture");
        }
        private InputStream stream(byte[] bytes) {
            return new ByteArrayInputStream(bytes) {
                @Override public void close() throws IOException { closed++; super.close(); }
            };
        }
    }
    private static WorldHostInstaller.Release release(byte[] bytes) throws Exception {
        return WorldHostInstaller.findRelease(MC, new Transport(rows(metadata(bytes)), bytes));
    }
    private static void rejected(JsonObject value, String message) throws Exception {
        try {
            check(WorldHostInstaller.findRelease(MC, new Transport(rows(value), new byte[0])) == null, message);
        } catch (IOException expected) {
            checks++;
        }
    }
    private static void noArtifact(File dir) throws IOException {
        File mods = new File(dir, "mods");
        File[] entries = mods.listFiles();
        check(!mods.exists() || entries != null && entries.length == 0,
                "Failed setup left a partial JAR or temporary artifact");
    }
    private static void catalog() throws Exception {
        byte[] bytes = jar("world-host", VERSION);
        Transport transport = new Transport(rows(metadata(bytes)), bytes);
        WorldHostInstaller.Release found = WorldHostInstaller.findRelease(MC, transport);
        check(found != null, "Compatible stable release was not found");
        check(found.version.equals(VERSION) && found.minecraftVersion.equals(MC), "Release target changed");
        check(found.filename.equals(NAME) && found.size == bytes.length, "Artifact metadata changed");
        check(found.sha512.equals(hash(bytes)), "Published hash changed");
        String query = URLDecoder.decode(transport.requested.get(0), "UTF-8");
        check(query.contains("game_versions=[\"1.21.1\"]"), "Request lacks exact Minecraft filter");
        check(query.contains("loaders=[\"fabric\"]"), "Request lacks Fabric filter");
        check(transport.closed == 1, "API response stream was not closed");
        check(WorldHostInstaller.findRelease("26.3", new Transport(rows(metadata(bytes)), bytes)) == null,
                "Mismatched Minecraft version was accepted");
        JsonObject otherLoader = metadata(bytes);
        otherLoader.add("loaders", array("forge"));
        rejected(otherLoader, "Forge artifact was accepted");
        JsonObject wrongProject = metadata(bytes);
        wrongProject.addProperty("project_id", "wrong123");
        rejected(wrongProject, "Artifact from another project was accepted");
        check(WorldHostInstaller.findRelease(MC, new Transport(new JsonArray(), bytes)) == null,
                "Empty compatible catalog did not report unsupported");
    }
    private static void versions() throws Exception {
        for (String good : new String[]{"0.5.0", VERSION, "0.5.10+1.21.1-fabric", "1.0.0"}) {
            check(WorldHostInstaller.isSecureVersion(good), "Secure stable version rejected: " + good);
        }
        for (String bad : new String[]{"0.4.14", "0.4.99", "0.5.0-beta", "0.5.2-rc1+1.21.1-fabric", "dev", "", "999999999999.0.0"}) {
            check(!WorldHostInstaller.isSecureVersion(bad), "Unsafe or malformed version accepted: " + bad);
        }
        byte[] bytes = jar("world-host", VERSION);
        JsonObject old = metadata(bytes);
        old.addProperty("version_number", "0.4.14+1.21.1-fabric");
        rejected(old, "Pre-encryption release was accepted");
        JsonObject beta = metadata(bytes);
        beta.addProperty("version_type", "beta");
        rejected(beta, "Beta release was accepted");
        JsonObject nine = metadata(bytes), ten = metadata(bytes);
        nine.addProperty("version_number", "0.5.9+1.21.1-fabric");
        ten.addProperty("version_number", "0.5.10+1.21.1-fabric");
        WorldHostInstaller.Release selected = WorldHostInstaller.findRelease(MC, new Transport(rows(nine, ten), bytes));
        check(selected != null && selected.version.startsWith("0.5.10+"), "Release ordering was lexical or API-order dependent");
    }
    private static void dependencies() throws Exception {
        byte[] bytes = jar("world-host", VERSION);
        JsonObject optional = metadata(bytes);
        JsonObject dependency = new JsonObject();
        dependency.addProperty("project_id", "mOgUt4GM");
        dependency.addProperty("dependency_type", "optional");
        optional.add("dependencies", rows(dependency));
        check(WorldHostInstaller.findRelease(MC, new Transport(rows(optional), bytes)) != null,
                "Optional dependency blocked installation");
        dependency.addProperty("dependency_type", "required");
        rejected(optional, "Required dependency was silently ignored");
        dependency.addProperty("dependency_type", "incompatible");
        rejected(optional, "Declared incompatible mod was accepted without checking installed mods");
    }
    private static void trustBoundaries() throws Exception {
        byte[] bytes = jar("world-host", VERSION);
        for (String url : new String[]{
                "http://cdn.modrinth.com/data/2AIZDfYo/versions/blBlWzm7/" + NAME,
                "https://cdn.modrinth.com.evil.test/data/2AIZDfYo/versions/blBlWzm7/" + NAME,
                "https://user@cdn.modrinth.com/data/2AIZDfYo/versions/blBlWzm7/" + NAME,
                "https://cdn.modrinth.com:444/data/2AIZDfYo/versions/blBlWzm7/" + NAME,
                "https://cdn.modrinth.com/data/Other123/versions/blBlWzm7/" + NAME,
                "https://cdn.modrinth.com/data/2AIZDfYo/versions/Other123/" + NAME,
                CDN + "%2e%2e/" + NAME, CDN + "%2e%2e%2f" + NAME,
                CDN + NAME + "?redirect=evil", CDN + NAME + "#fragment",
                CDN + "different.jar"}) {
            JsonObject value = metadata(bytes);
            file(value).addProperty("url", url);
            rejected(value, "Unsafe artifact URL accepted: " + url);
        }
        for (String name : new String[]{"../world-host.jar", "nested/world-host.jar", "world-host\\escape.jar", ".hidden.jar", "world-host.apk", "world-host.jar.disabled"}) {
            JsonObject value = metadata(bytes);
            file(value).addProperty("filename", name);
            rejected(value, "Unsafe artifact filename accepted: " + name);
        }
        JsonObject badHash = metadata(bytes);
        file(badHash).getAsJsonObject("hashes").addProperty("sha512", "abc");
        rejected(badHash, "Invalid hash accepted");
        for (long size : new long[]{0, -1, Long.MAX_VALUE}) {
            JsonObject value = metadata(bytes);
            file(value).addProperty("size", size);
            rejected(value, "Unbounded artifact size accepted");
        }
        Transport invalidInput = new Transport(rows(metadata(bytes)), bytes);
        try {
            check(WorldHostInstaller.findRelease("1.21.1\"],\"26.3", invalidInput) == null,
                    "Query injection input accepted");
        } catch (IOException expected) { checks++; }
        check(invalidInput.requested.isEmpty(), "Malformed Minecraft version reached network boundary");
    }
    private static void malformedCatalog() throws Exception {
        Transport malformed = new Transport(new JsonArray(), new byte[0]);
        malformed.api = "{not json";
        failure(() -> WorldHostInstaller.findRelease(MC, malformed), "Malformed API JSON accepted");
        Transport object = new Transport(new JsonArray(), new byte[0]);
        object.api = "{}";
        failure(() -> WorldHostInstaller.findRelease(MC, object), "Non-array API catalog accepted");
        Transport oversized = new Transport(new JsonArray(), new byte[0]);
        char[] text = new char[5 * 1024 * 1024];
        Arrays.fill(text, ' ');
        oversized.api = "[" + new String(text) + "]";
        failure(() -> WorldHostInstaller.findRelease(MC, oversized), "Oversized API response accepted");
    }
    private static void success() throws Exception {
        File dir = directory("success");
        byte[] bytes = jar("world-host", VERSION);
        WorldHostInstaller.Release release = release(bytes);
        Transport transport = new Transport(new JsonArray(), bytes);
        WorldHostInstaller.install(release, dir, transport);
        Path installed = dir.toPath().resolve("mods").resolve(NAME);
        check(Arrays.equals(bytes, Files.readAllBytes(installed)), "Installed bytes differ from verified download");
        WorldHostInstaller.Installed status = WorldHostInstaller.inspectInstalled(dir);
        check(status != null && status.version.equals(VERSION), "Installed manifest not discovered");
        check(status.filename.equals(NAME) && !status.disabled && status.secure, "Installed state is inaccurate");
        check(status.sha512.equals(hash(bytes)) && status.matches(release), "Exact verified release not recognized");
        check(WorldHostInstaller.installedVersion(dir).equals(VERSION), "Installed version wrapper changed version");
        check(transport.closed == 1, "Artifact stream was not closed");
        check(new File(dir, "mods").list().length == 1, "Successful setup left temporary artifacts");
        failure(() -> WorldHostInstaller.install(release, dir, transport), "Duplicate installation was allowed");
        check(Arrays.equals(bytes, Files.readAllBytes(installed)), "Duplicate setup changed installed mod");
    }
    private static void failedDownloadAndRetry() throws Exception {
        File dir = directory("retry");
        byte[] bytes = jar("world-host", VERSION);
        WorldHostInstaller.Release release = release(bytes);
        Transport transport = new Transport(new JsonArray(), bytes);
        transport.breakDownload = true;
        failure(() -> WorldHostInstaller.install(release, dir, transport), "Interrupted download succeeded");
        noArtifact(dir);
        check(transport.closed == 1, "Interrupted stream was not closed");
        transport.breakDownload = false;
        WorldHostInstaller.install(release, dir, transport);
        check(WorldHostInstaller.inspectInstalled(dir).matches(release), "Failed download prevented a clean retry");
    }
    private static void integrityFailures() throws Exception {
        byte[] bytes = jar("world-host", VERSION);
        WorldHostInstaller.Release release = release(bytes);
        File wrongHash = directory("wrong-hash");
        byte[] altered = bytes.clone();
        altered[altered.length / 2] ^= 1;
        failure(() -> WorldHostInstaller.install(release, wrongHash, new Transport(new JsonArray(), altered)),
                "Hash mismatch was installed");
        noArtifact(wrongHash);
        File truncated = directory("truncated");
        failure(() -> WorldHostInstaller.install(release, truncated,
                new Transport(new JsonArray(), Arrays.copyOf(bytes, bytes.length - 10))), "Truncated JAR was installed");
        noArtifact(truncated);
        File oversized = directory("oversized");
        failure(() -> WorldHostInstaller.install(release, oversized,
                new Transport(new JsonArray(), Arrays.copyOf(bytes, bytes.length + 1))), "Oversized JAR was installed");
        noArtifact(oversized);
        for (String id : new String[]{"another-mod", ""}) {
            File wrongManifest = directory("manifest-" + (id.isEmpty() ? "empty" : id));
            byte[] wrong = jar(id, VERSION);
            failure(() -> WorldHostInstaller.install(release(wrong), wrongManifest,
                    new Transport(new JsonArray(), wrong)), "Non-World-Host manifest was installed");
            noArtifact(wrongManifest);
        }
        File wrongVersion = directory("manifest-version");
        byte[] old = jar("world-host", "0.4.14+1.21.1-fabric");
        failure(() -> WorldHostInstaller.install(release(old), wrongVersion,
                new Transport(new JsonArray(), old)), "Manifest version mismatch was installed");
        noArtifact(wrongVersion);
    }
    private static void existingMods() throws Exception {
        byte[] bytes = jar("world-host", VERSION);
        WorldHostInstaller.Release release = release(bytes);
        File dir = directory("renamed");
        Path mods = Files.createDirectory(dir.toPath().resolve("mods"));
        Path renamed = mods.resolve("my-friends.jar");
        Files.write(renamed, bytes);
        check(WorldHostInstaller.inspectInstalled(dir).matches(release), "Renamed World Host escaped detection");
        failure(() -> WorldHostInstaller.install(release, dir, new Transport(new JsonArray(), bytes)),
                "Renamed existing mod allowed duplicate installation");
        check(Arrays.equals(bytes, Files.readAllBytes(renamed)), "Existing mod was changed");
        Files.move(renamed, mods.resolve("my-friends.jar.disabled"));
        WorldHostInstaller.Installed disabled = WorldHostInstaller.inspectInstalled(dir);
        check(disabled.disabled && !disabled.matches(release), "Disabled mod was presented as ready");
        failure(() -> WorldHostInstaller.install(release, dir, new Transport(new JsonArray(), bytes)),
                "Disabled existing mod allowed duplicate installation");
        File older = directory("older");
        Path oldMods = Files.createDirectory(older.toPath().resolve("mods"));
        Files.write(oldMods.resolve("old.jar"), jar("world-host", "0.4.14+1.21.1-fabric"));
        WorldHostInstaller.Installed old = WorldHostInstaller.inspectInstalled(older);
        check(!old.secure && !old.matches(release), "Old mod was presented as secure/ready");
        failure(() -> WorldHostInstaller.install(release, older, new Transport(new JsonArray(), bytes)),
                "Old existing mod allowed duplicate installation");
    }
    private static void collisionsAndOtherMods() throws Exception {
        byte[] bytes = jar("world-host", VERSION), other = jar("another-mod", "1.0.0");
        WorldHostInstaller.Release release = release(bytes);
        File dir = directory("other-mods");
        Path mods = Files.createDirectory(dir.toPath().resolve("mods"));
        Path unrelated = Files.write(mods.resolve("unrelated.jar"), other);
        WorldHostInstaller.install(release, dir, new Transport(new JsonArray(), bytes));
        check(Arrays.equals(other, Files.readAllBytes(unrelated)), "Setup modified an unrelated mod");
        File collision = directory("collision");
        Path occupied = Files.createDirectory(collision.toPath().resolve("mods")).resolve(NAME);
        Files.write(occupied, other);
        failure(() -> WorldHostInstaller.install(release, collision, new Transport(new JsonArray(), bytes)),
                "Setup overwrote a user file occupying the target filename");
        check(Arrays.equals(other, Files.readAllBytes(occupied)), "Collision damaged the existing file");
        File duplicates = directory("duplicates");
        Path duplicateMods = Files.createDirectory(duplicates.toPath().resolve("mods"));
        Files.write(duplicateMods.resolve("a.jar"), bytes);
        Files.write(duplicateMods.resolve("b.jar"), bytes);
        failure(() -> WorldHostInstaller.inspectInstalled(duplicates), "Duplicate mods were reported as ready");
    }
    private static void malformedExistingAndSymlink() throws Exception {
        byte[] bytes = jar("world-host", VERSION);
        WorldHostInstaller.Release release = release(bytes);
        File broken = directory("broken-existing");
        Path mods = Files.createDirectory(broken.toPath().resolve("mods"));
        Path invalid = Files.write(mods.resolve("world-host-broken.jar"), new byte[]{1, 2, 3});
        failure(() -> WorldHostInstaller.install(release, broken, new Transport(new JsonArray(), bytes)),
                "Unreadable suspected World Host mod was ignored");
        check(Arrays.equals(Files.readAllBytes(invalid), new byte[]{1, 2, 3}), "Broken user mod was deleted");
        File linked = directory("linked-mods");
        Path outside = Files.createDirectory(root.resolve("outside-mods"));
        Files.createSymbolicLink(linked.toPath().resolve("mods"), outside);
        failure(() -> WorldHostInstaller.install(release, linked, new Transport(new JsonArray(), bytes)),
                "Setup followed a mods directory outside the selected instance");
        try (Stream<Path> paths = Files.list(outside)) {
            check(!paths.findAny().isPresent(), "Setup wrote outside the selected instance");
        }
    }
    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("insane-world-host-tests-");
        try {
            scenario("exact Minecraft and Fabric catalog filtering", WorldHostInstallerTest::catalog);
            scenario("secure stable version selection", WorldHostInstallerTest::versions);
            scenario("required, incompatible and optional dependencies", WorldHostInstallerTest::dependencies);
            scenario("artifact URL, path, hash and size boundaries", WorldHostInstallerTest::trustBoundaries);
            scenario("malformed and oversized API responses", WorldHostInstallerTest::malformedCatalog);
            scenario("verified install and exact installed identity", WorldHostInstallerTest::success);
            scenario("interrupted download cleanup and retry", WorldHostInstallerTest::failedDownloadAndRetry);
            scenario("hash, size and manifest verification failures", WorldHostInstallerTest::integrityFailures);
            scenario("renamed, disabled and old World Host detection", WorldHostInstallerTest::existingMods);
            scenario("user mods, filename collisions and duplicate detection", WorldHostInstallerTest::collisionsAndOtherMods);
            scenario("unreadable existing mod and directory escape refusal", WorldHostInstallerTest::malformedExistingAndSymlink);
            System.out.println("World Host: " + scenarios + " scenarios, " + checks + " checks passed");
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
            }
        }
    }
}
