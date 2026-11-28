package com.ashmeet.hyperlauncher.utils.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Opt-in, reproducible client setup for a new, private Fabric instance. Call on a worker thread. */
public final class InsaneClientInstaller {
    public static final String MINECRAFT_VERSION = "1.21.11";
    public static final String MIN_LOADER_VERSION = "0.19.2";
    public static final String CLIENT_VERSION = "1.1.2";
    public static final String CLIENT_ASSET = "client/insane-client-1.1.2.jar";
    // Deterministic branded artifact; provenance and license live in third_party/pyes-client.
    public static final String CLIENT_SHA256 = "74b4d2d4543b9ed522d4c2d9ba87baa028cd9be60ad4c71895c3dcf7eb6aa70f";
    public static final long CLIENT_SIZE = 36634366;
    public static final String FABRIC_API_VERSION = "0.141.6+1.21.11";
    public static final String FABRIC_API_URL = "https://cdn.modrinth.com/data/P7dR8mSH/versions/6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar";
    public static final String FABRIC_API_SHA512 = "852d3682c4f353fd0ca86546af8578513e8bcf628377fa9d49c556bc59c5024c69ab56c92fc6dfba11fed19e4316a3f5636e7e07fd3d96f7043b531aef59b637";
    public static final long FABRIC_API_SIZE = 2426039;
    private static final long MAX_JAR_BYTES = 64L * 1024 * 1024;
    private static final long TRANSFER_TIMEOUT_MS = 120_000;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final Pattern LOADER_VERSION = Pattern.compile("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})");

    interface Transport { InputStream open(String url) throws IOException; }

    static final class Artifact {
        final String filename, hashAlgorithm, hash, modId, version;
        final long size;
        Artifact(String filename, String hashAlgorithm, String hash, long size, String modId, String version) {
            this.filename = filename;
            this.hashAlgorithm = hashAlgorithm;
            this.hash = hash;
            this.size = size;
            this.modId = modId;
            this.version = version;
        }
    }

    private InsaneClientInstaller() { }

    /** Only stable numeric loader releases qualify; never guess compatibility for snapshots. */
    public static boolean isCompatibleLoader(String version) {
        if (version == null) return false;
        Matcher candidate = LOADER_VERSION.matcher(version);
        Matcher minimum = LOADER_VERSION.matcher(MIN_LOADER_VERSION);
        if (!candidate.matches() || !minimum.matches()) return false;
        for (int index = 1; index <= 3; index++) {
            int difference = Integer.compare(Integer.parseInt(candidate.group(index)), Integer.parseInt(minimum.group(index)));
            if (difference != 0) return difference > 0;
        }
        return true;
    }

    /**
     * Installs the bundled fork and one pinned Fabric API release. The input stream is always closed.
     * Existing mods are never replaced. No mods folder becomes ready until both JARs are verified.
     */
    public static void install(File gameDirectory, InputStream bundledClient) throws IOException {
        install(gameDirectory, bundledClient,
                new Artifact("INSANE-CLIENT-" + CLIENT_VERSION + ".jar", "SHA-256", CLIENT_SHA256,
                        CLIENT_SIZE, "pyesclient", CLIENT_VERSION),
                new Artifact("fabric-api-" + FABRIC_API_VERSION + ".jar", "SHA-512", FABRIC_API_SHA512,
                        FABRIC_API_SIZE, "fabric-api", FABRIC_API_VERSION), InsaneClientInstaller::openHttps);
    }

    static synchronized void install(File gameDirectory, InputStream bundledClient,
                                     Artifact client, Artifact fabricApi, Transport transport) throws IOException {
        if (bundledClient == null) throw new IOException("The bundled INSANE CLIENT could not be opened. Reinstall the launcher.");
        Path staged = null;
        try (InputStream source = bundledClient) {
            validateArtifact(client);
            validateArtifact(fabricApi);
            if (gameDirectory == null) throw new IOException("Create a private Fabric instance first.");
            Path root = gameDirectory.toPath().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(root)) throw new IOException("Linked instance folders cannot be used for client setup.");
            Files.createDirectories(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("The instance folder is not a directory.");
            Path mods = root.resolve("mods");
            requireEmptyMods(mods);
            staged = Files.createTempDirectory(root, ".insane-client-setup-");
            verifyCopy(source, staged.resolve(client.filename), client);
            try (InputStream api = transport.open(FABRIC_API_URL)) {
                verifyCopy(api, staged.resolve(fabricApi.filename), fabricApi);
            }
            // A second caller or another screen must not lose files added during download.
            requireEmptyMods(mods);
            if (Files.exists(mods, LinkOption.NOFOLLOW_LINKS)) Files.delete(mods);
            // Same-filesystem directory move publishes both complete JARs together and never requests replacement.
            Files.move(staged, mods);
            staged = null;
        } finally {
            if (staged != null) {
                // Only these two owned paths can exist: no recursive deletion of an instance or user files.
                Files.deleteIfExists(staged.resolve(client.filename));
                Files.deleteIfExists(staged.resolve(fabricApi.filename));
                Files.deleteIfExists(staged);
            }
        }
    }

    private static void requireEmptyMods(Path mods) throws IOException {
        if (!Files.exists(mods, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(mods) || !Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The mods folder is linked or invalid. Choose a new private instance.");
        }
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(mods)) {
            if (entries.iterator().hasNext()) throw new IOException("This instance already has mods. Create a new INSANE CLIENT instance instead.");
        }
    }

    private static void validateArtifact(Artifact artifact) throws IOException {
        int hashLength = "SHA-256".equals(artifact.hashAlgorithm) ? 64 : "SHA-512".equals(artifact.hashAlgorithm) ? 128 : 0;
        if (artifact.size <= 0 || artifact.size > MAX_JAR_BYTES || hashLength == 0
                || !artifact.hash.matches("[a-f0-9]{" + hashLength + "}")
                || !artifact.filename.matches("[A-Za-z0-9][A-Za-z0-9._+()-]{0,160}\\.jar")) {
            throw new IOException("The bundled client release metadata is invalid. Reinstall the launcher.");
        }
    }

    private static void verifyCopy(InputStream input, Path destination, Artifact artifact) throws IOException {
        MessageDigest digest = digest(artifact.hashAlgorithm);
        long count = 0, started = System.nanoTime();
        try (OutputStream output = Files.newOutputStream(destination, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                checkTransfer(started);
                count += read;
                if (count > artifact.size) throw new IOException("Client download size did not match. Try setup again.");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        checkTransfer(started);
        if (count != artifact.size || !hex(digest.digest()).equals(artifact.hash)) {
            throw new IOException("Client download verification failed. Try setup again.");
        }
        verifyManifest(destination.toFile(), artifact);
    }

    private static void verifyManifest(File jar, Artifact artifact) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("The client download is not a Fabric mod.");
            JsonElement document;
            try (InputStream input = zip.getInputStream(entry)) {
                document = JsonParser.parseString(readText(input));
            }
            if (!document.isJsonObject()) throw new IOException("The client Fabric manifest is invalid.");
            JsonObject manifest = document.getAsJsonObject();
            if (!artifact.modId.equals(text(manifest, "id")) || !artifact.version.equals(text(manifest, "version"))) {
                throw new IOException("The downloaded client mod does not match this release.");
            }
            JsonElement dependencies = manifest.get("depends");
            if (dependencies == null || !dependencies.isJsonObject()) throw new IOException("The client dependencies are missing.");
            JsonObject depends = dependencies.getAsJsonObject();
            Set<String> expected = new HashSet<>();
            expected.add("minecraft"); expected.add("fabricloader"); expected.add("java");
            if ("pyesclient".equals(artifact.modId)) {
                expected.add("fabric-api");
                if (!"INSANE CLIENT".equals(text(manifest, "name"))
                        || !MINECRAFT_VERSION.equals(text(depends, "minecraft"))
                        || !(">=" + MIN_LOADER_VERSION).equals(text(depends, "fabricloader"))
                        || !"*".equals(text(depends, "fabric-api"))) {
                    throw new IOException("The bundled client is not compatible with this Fabric setup.");
                }
            } else if ("fabric-api".equals(artifact.modId)) {
                if (!">=1.21.11- <1.21.12-".equals(text(depends, "minecraft"))
                        || !">=0.17.3".equals(text(depends, "fabricloader"))) {
                    throw new IOException("Fabric API is not compatible with this client release.");
                }
            } else {
                throw new IOException("Unexpected mod in client setup.");
            }
            if (!">=21".equals(text(depends, "java")) || !depends.keySet().equals(expected)) {
                throw new IOException("This client has unsupported dependencies.");
            }
            // Pinned bytes cover nested dependency identity; also reject missing embedded JARs explicitly.
            JsonElement jars = manifest.get("jars");
            if (jars != null) {
                if (!jars.isJsonArray()) throw new IOException("Invalid embedded client libraries.");
                for (JsonElement nested : jars.getAsJsonArray()) {
                    String name = nested.isJsonObject() ? text(nested.getAsJsonObject(), "file") : "";
                    if (!name.startsWith("META-INF/jars/") || !name.endsWith(".jar")
                            || zip.getEntry(name) == null) throw new IOException("A required embedded client library is missing.");
                }
            }
        } catch (RuntimeException malformed) {
            throw new IOException("The client Fabric manifest could not be read.", malformed);
        }
    }

    private static InputStream openHttps(String address) throws IOException {
        // Only the pinned Fabric API artifact is fetched; no mutable third-party download redirect.
        if (!FABRIC_API_URL.equals(address)) throw new IOException("Unexpected client dependency URL.");
        final HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("User-Agent", "INSANE-CLIENT/Client-Setup (github.com/insaneprinceyt/INSANE_CLIENT)");
        connection.setRequestProperty("Accept", "application/java-archive");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Fabric API download failed (HTTP " + status + "). Try setup again.");
            return new FilterInputStream(connection.getInputStream()) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { connection.disconnect(); }
                }
            };
        } catch (IOException failure) {
            connection.disconnect();
            throw failure;
        }
    }

    private static void checkTransfer(long started) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Client setup was interrupted. Try again.");
        if ((System.nanoTime() - started) / 1_000_000 > TRANSFER_TIMEOUT_MS) {
            throw new IOException("Client setup timed out. Check your connection and try again.");
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    private static String readText(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_MANIFEST_BYTES) throw new IOException("The client manifest is too large.");
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static MessageDigest digest(String algorithm) {
        try { return MessageDigest.getInstance(algorithm); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
