package com.ashmeet.hyperlauncher.utils.client;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exercises production staging, integrity, dependency, compatibility and preservation behavior. */
public final class InsaneClientInstallerTest {
    private static int checks, scenarios;
    private static Path suite;
    private static byte[] client, api;
    private interface Checked { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        suite = Files.createTempDirectory("insane-client-installer-tests-");
        try {
            client = jar(manifest("pyesclient"));
            api = jar(manifest("fabric-api"));
            run("loader compatibility boundaries", () -> {
                for (String version : Arrays.asList("0.19.2", "0.19.3", "0.20.0", "1.0.0")) {
                    check(InsaneClientInstaller.isCompatibleLoader(version), "accept " + version);
                }
                for (String version : Arrays.asList(null, "", "0.19.1", "0.18.9", "0.19.2-beta.1", "0.19.2+test", "00.19.2", "1.2", "99999999999999.0.0")) {
                    check(!InsaneClientInstaller.isCompatibleLoader(version), "reject " + version);
                }
            });
            run("verified pair installs together", () -> {
                Path root = root();
                TrackingInput source = new TrackingInput(client);
                int[] requests = {0};
                install(root, source, client, api, url -> {
                    check(url.equals(InsaneClientInstaller.FABRIC_API_URL), "pinned CDN endpoint");
                    check(!Files.exists(root.resolve("mods")), "no ready mods before dependency verification");
                    requests[0]++;
                    return new ByteArrayInputStream(api);
                });
                check(source.closed, "bundled stream closed");
                check(requests[0] == 1, "one required dependency only");
                check(Arrays.equals(Files.readAllBytes(root.resolve("mods/client.jar")), client), "client bytes preserved");
                check(Arrays.equals(Files.readAllBytes(root.resolve("mods/api.jar")), api), "API bytes preserved");
                check(childNames(root).equals(Arrays.asList("mods")), "staging cleaned");
            });
            run("empty mods folder accepted", () -> {
                Path root = root(); Files.createDirectory(root.resolve("mods"));
                install(root, new TrackingInput(client), client, api, url -> new ByteArrayInputStream(api));
                check(childNames(root.resolve("mods")).size() == 2, "two installed mods");
            });
            run("corrupt client rejected before network", () -> {
                Path root = root(); byte[] corrupted = client.clone(); corrupted[corrupted.length - 1] ^= 1;
                TrackingInput source = new TrackingInput(corrupted);
                expectFailure(() -> install(root, source, client, api, url -> { throw new AssertionError("network must not start"); }));
                check(source.closed, "failed bundle closed"); clean(root);
            });
            run("oversize and truncated client rejected", () -> {
                for (byte[] invalid : Arrays.asList(Arrays.copyOf(client, client.length + 1), Arrays.copyOf(client, client.length - 1))) {
                    Path root = root();
                    expectFailure(() -> install(root, new TrackingInput(invalid), client, api, url -> { throw new AssertionError("unexpected network"); }));
                    clean(root);
                }
            });
            run("dependency failure removes staged client", () -> {
                Path root = root();
                expectFailure(() -> install(root, new TrackingInput(client), client, api, url -> { throw new IOException("offline"); }));
                clean(root);
            });
            run("failed setup can be retried in the same instance", () -> {
                Path root = root();
                expectFailure(() -> install(root, new TrackingInput(client), client, api,
                        url -> { throw new IOException("temporary connection failure"); }));
                clean(root);
                TrackingInput dependency = new TrackingInput(api);
                install(root, new TrackingInput(client), client, api, url -> dependency);
                check(dependency.closed, "successful dependency stream closed");
                check(childNames(root.resolve("mods")).equals(Arrays.asList("api.jar", "client.jar")),
                        "retry installs the complete pair");
            });
            run("corrupt dependency never publishes pair", () -> {
                Path root = root(); byte[] wrong = api.clone(); wrong[wrong.length - 1] ^= 1;
                TrackingInput dependency = new TrackingInput(wrong);
                expectFailure(() -> install(root, new TrackingInput(client), client, api, url -> dependency));
                check(dependency.closed, "failed dependency stream closed");
                clean(root);
            });
            run("dependency stream failure cannot publish the pair", () -> {
                Path root = root();
                expectFailure(() -> install(root, new TrackingInput(client), client, api,
                        url -> new ByteArrayInputStream(api) {
                            @Override public void close() throws IOException {
                                throw new IOException("dependency stream close failed");
                            }
                        }));
                clean(root);
            });
            run("existing enabled and disabled mods preserved", () -> {
                for (String name : Arrays.asList("user.jar", "client.jar.disabled")) {
                    Path root = root(); Path mods = Files.createDirectory(root.resolve("mods"));
                    byte[] original = {1, 2, 3}; Files.write(mods.resolve(name), original);
                    TrackingInput source = new TrackingInput(client);
                    expectFailure(() -> install(root, source, client, api, url -> { throw new AssertionError("unexpected network"); }));
                    check(source.closed, "early rejection closes stream");
                    check(Arrays.equals(original, Files.readAllBytes(mods.resolve(name))), "existing file unchanged");
                    check(childNames(mods).equals(Arrays.asList(name)), "no new mod added");
                    check(childNames(root).equals(Arrays.asList("mods")), "no stale staging");
                }
            });
            run("late added mod never overwritten", () -> {
                Path root = root();
                expectFailure(() -> install(root, new TrackingInput(client), client, api, url -> {
                    Files.createDirectory(root.resolve("mods"));
                    Files.write(root.resolve("mods/late.jar"), new byte[]{7});
                    return new ByteArrayInputStream(api);
                }));
                check(Arrays.equals(Files.readAllBytes(root.resolve("mods/late.jar")), new byte[]{7}), "late mod preserved");
                check(childNames(root.resolve("mods")).equals(Arrays.asList("late.jar")), "no partial client");
                check(childNames(root).equals(Arrays.asList("mods")), "failed staging removed");
            });
            run("linked mods folder rejected", () -> {
                Path root = root(); Path outside = root();
                Files.createSymbolicLink(root.resolve("mods"), outside);
                expectFailure(() -> install(root, new TrackingInput(client), client, api, url -> { throw new AssertionError("unexpected network"); }));
                check(Files.isSymbolicLink(root.resolve("mods")), "link not replaced"); clean(outside);
            });
            run("linked instance folder rejected", () -> {
                Path parent = root(); Path outside = root();
                Path linked = parent.resolve("instance");
                Files.createSymbolicLink(linked, outside);
                TrackingInput source = new TrackingInput(client);
                expectFailure(() -> install(linked, source, client, api,
                        url -> { throw new AssertionError("unexpected network"); }));
                check(source.closed, "linked root rejection closes bundled stream");
                check(Files.isSymbolicLink(linked), "instance link retained"); clean(outside);
            });
            run("mismatched and unsupported manifests rejected despite valid hash", () -> {
                List<JsonObject> invalid = new ArrayList<>();
                JsonObject wrongId = manifest("pyesclient"); wrongId.addProperty("id", "another-mod"); invalid.add(wrongId);
                JsonObject wrongVersion = manifest("pyesclient"); wrongVersion.addProperty("version", "9.9.9"); invalid.add(wrongVersion);
                JsonObject wrongMc = manifest("pyesclient"); wrongMc.getAsJsonObject("depends").addProperty("minecraft", "26.1.1"); invalid.add(wrongMc);
                JsonObject missingApi = manifest("pyesclient"); missingApi.getAsJsonObject("depends").remove("fabric-api"); invalid.add(missingApi);
                JsonObject extraDependency = manifest("pyesclient"); extraDependency.getAsJsonObject("depends").addProperty("missing-library", "*"); invalid.add(extraDependency);
                JsonObject wrongJava = manifest("pyesclient"); wrongJava.getAsJsonObject("depends").addProperty("java", ">=25"); invalid.add(wrongJava);
                JsonObject missingNested = manifest("pyesclient");
                com.google.gson.JsonArray nested = new com.google.gson.JsonArray();
                JsonObject file = new JsonObject(); file.addProperty("file", "META-INF/jars/missing.jar"); nested.add(file);
                missingNested.add("jars", nested); invalid.add(missingNested);
                for (JsonObject value : invalid) {
                    byte[] bytes = jar(value); Path root = root();
                    expectFailure(() -> install(root, new TrackingInput(bytes), bytes, api, url -> { throw new AssertionError("unexpected network"); }));
                    clean(root);
                }
            });
            run("incompatible Fabric API rolls back client", () -> {
                JsonObject incompatible = manifest("fabric-api"); incompatible.getAsJsonObject("depends").addProperty("minecraft", ">=26.1");
                byte[] bytes = jar(incompatible); Path root = root();
                expectFailure(() -> install(root, new TrackingInput(client), client, bytes, url -> new ByteArrayInputStream(bytes)));
                clean(root);
            });
            run("interruption closes streams and removes staging", () -> {
                Path root = root(); TrackingInput source = new TrackingInput(client);
                try {
                    Thread.currentThread().interrupt();
                    expectFailure(() -> install(root, source, client, api, url -> { throw new AssertionError("unexpected network"); }));
                } finally { Thread.interrupted(); }
                check(source.closed, "interrupted bundle closed"); clean(root);
            });
            if (args.length == 2) run("real branded client and pinned Fabric API", () -> {
                byte[] actualClient = Files.readAllBytes(java.nio.file.Paths.get(args[0]));
                byte[] actualApi = Files.readAllBytes(java.nio.file.Paths.get(args[1]));
                Path root = root();
                InsaneClientInstaller.install(root.toFile(), new ByteArrayInputStream(actualClient),
                        new InsaneClientInstaller.Artifact("client.jar", "SHA-256", InsaneClientInstaller.CLIENT_SHA256,
                                InsaneClientInstaller.CLIENT_SIZE, "pyesclient", InsaneClientInstaller.CLIENT_VERSION),
                        new InsaneClientInstaller.Artifact("api.jar", "SHA-512", InsaneClientInstaller.FABRIC_API_SHA512,
                                InsaneClientInstaller.FABRIC_API_SIZE, "fabric-api", InsaneClientInstaller.FABRIC_API_VERSION),
                        url -> new ByteArrayInputStream(actualApi));
                check(Files.size(root.resolve("mods/client.jar")) == actualClient.length, "real client staged");
                check(Files.size(root.resolve("mods/api.jar")) == actualApi.length, "real API staged");
            });
            System.out.println("PASS: " + scenarios + " scenarios, " + checks + " checks");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(suite)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }

    private static void install(Path root, InputStream input, byte[] expectedClient, byte[] expectedApi, InsaneClientInstaller.Transport transport) throws Exception {
        InsaneClientInstaller.install(root.toFile(), input,
                new InsaneClientInstaller.Artifact("client.jar", "SHA-256", hash(expectedClient, "SHA-256"), expectedClient.length, "pyesclient", "1.1.2"),
                new InsaneClientInstaller.Artifact("api.jar", "SHA-512", hash(expectedApi, "SHA-512"), expectedApi.length, "fabric-api", "0.141.6+1.21.11"), transport);
    }

    private static JsonObject manifest(String id) {
        boolean client = id.equals("pyesclient");
        JsonObject result = new JsonObject(); result.addProperty("schemaVersion", 1);
        result.addProperty("id", id); result.addProperty("name", client ? "INSANE CLIENT" : "Fabric API");
        result.addProperty("version", client ? "1.1.2" : "0.141.6+1.21.11");
        JsonObject depends = new JsonObject();
        depends.addProperty("minecraft", client ? "1.21.11" : ">=1.21.11- <1.21.12-");
        depends.addProperty("fabricloader", client ? ">=0.19.2" : ">=0.17.3");
        depends.addProperty("java", ">=21");
        if (client) depends.addProperty("fabric-api", "*");
        result.add("depends", depends); return result;
    }

    private static byte[] jar(JsonObject manifest) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static String hash(byte[] bytes, String algorithm) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte b : MessageDigest.getInstance(algorithm).digest(bytes)) text.append(String.format(Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }

    private static Path root() throws IOException { return Files.createTempDirectory(suite, "case-"); }
    private static void clean(Path root) throws IOException { check(childNames(root).isEmpty(), "no partial installation or staging remains"); }
    private static List<String> childNames(Path root) throws IOException {
        List<String> result = new ArrayList<>();
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(root)) { for (Path file : files) result.add(file.getFileName().toString()); }
        java.util.Collections.sort(result); return result;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void expectFailure(Checked action) throws Exception {
        try { action.run(); } catch (IOException expected) { check(!expected.getMessage().isEmpty(), "actionable error"); return; }
        throw new AssertionError("Expected IOException");
    }
    private static void run(String name, Checked action) throws Exception { action.run(); scenarios++; System.out.println("PASS " + name); }
    private static final class TrackingInput extends ByteArrayInputStream {
        boolean closed;
        TrackingInput(byte[] bytes) { super(bytes); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
}
