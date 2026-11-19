package com.ashmeet.hyperlauncher.utils.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Verified opt-in World Host setup. Call blocking methods on a worker thread. */
public final class WorldHostInstaller {
    public static final String PROJECT_ID = "2AIZDfYo";
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final long MAX_JAR_BYTES = 32 * 1024 * 1024;
    private static final long TRANSFER_TIMEOUT_MS = 120_000;
    private static final Pattern STABLE_VERSION = Pattern.compile(
            "(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})(?:\\+[A-Za-z0-9._-]{1,100})?");

    public static final class Release {
        public final String version, filename, url, sha512, minecraftVersion;
        public final long size;
        private Release(String version, String filename, String url, String sha512,
                        long size, String minecraftVersion) {
            this.version = version;
            this.filename = filename;
            this.url = url;
            this.sha512 = sha512;
            this.size = size;
            this.minecraftVersion = minecraftVersion;
        }
    }

    public static final class Installed {
        public final String version, filename, sha512;
        public final boolean disabled, secure;
        private Installed(String version, String filename, String sha512, boolean disabled) {
            this.version = version;
            this.filename = filename;
            this.sha512 = sha512;
            this.disabled = disabled;
            this.secure = isSecureVersion(version);
        }
        /** Readiness means the enabled JAR is the exact artifact resolved for this instance. */
        public boolean matches(Release release) {
            return release != null && !disabled && secure && sha512.equals(release.sha512);
        }
    }

    interface Transport { InputStream open(String url) throws IOException; }

    private WorldHostInstaller() { }

    /** Returns null when no supported stable Fabric release exists for this exact game version. */
    public static Release findRelease(String minecraftVersion) throws IOException {
        return findRelease(minecraftVersion, WorldHostInstaller::openHttps);
    }

    static Release findRelease(String minecraftVersion, Transport transport) throws IOException {
        if (minecraftVersion == null || !minecraftVersion.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
            throw new IOException("Choose an installed Minecraft Java version first.");
        }
        String endpoint = "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version?loaders="
                + URLEncoder.encode("[\"fabric\"]", "UTF-8") + "&game_versions="
                + URLEncoder.encode("[\"" + minecraftVersion + "\"]", "UTF-8");
        try (InputStream stream = transport.open(endpoint)) {
            JsonElement document = JsonParser.parseString(readText(stream, MAX_CATALOG_BYTES));
            if (!document.isJsonArray()) throw new IOException("Modrinth returned an invalid version list. Try again.");
            Release best = null;
            for (JsonElement item : document.getAsJsonArray()) {
                Release candidate = parseRelease(item, minecraftVersion);
                if (candidate != null && (best == null || compareVersion(candidate.version, best.version) > 0)) {
                    best = candidate;
                }
            }
            return best;
        } catch (RuntimeException malformed) {
            throw new IOException("Could not read World Host releases from Modrinth. Try again.");
        }
    }

    private static Release parseRelease(JsonElement item, String minecraftVersion) {
        try {
            if (!item.isJsonObject()) return null;
            JsonObject row = item.getAsJsonObject();
            String id = text(row, "id"), version = text(row, "version_number");
            if (!PROJECT_ID.equals(text(row, "project_id")) || !id.matches("[A-Za-z0-9]{8}")
                    || !"release".equals(text(row, "version_type")) || !isSecureVersion(version)
                    || !contains(row.get("game_versions"), minecraftVersion)
                    || !contains(row.get("loaders"), "fabric")) return null;
            String status = text(row, "status");
            if (!status.isEmpty() && !status.equals("listed") && !status.equals("archived")) return null;
            // Current supported releases bundle their required libraries. A future required external
            // dependency must be deliberately supported instead of silently shipping a broken setup.
            JsonElement dependencies = row.get("dependencies");
            if (dependencies == null || !dependencies.isJsonArray()) return null;
            for (JsonElement dependency : dependencies.getAsJsonArray()) {
                if (!dependency.isJsonObject()) return null;
                String kind = text(dependency.getAsJsonObject(), "dependency_type");
                if (!kind.equals("optional") && !kind.equals("embedded")) return null;
            }
            JsonElement files = row.get("files");
            if (files == null || !files.isJsonArray()) return null;
            Release selected = null;
            for (JsonElement value : files.getAsJsonArray()) {
                if (!value.isJsonObject()) continue;
                JsonObject file = value.getAsJsonObject();
                if (!bool(file, "primary")) continue;
                if (selected != null) return null;
                String name = text(file, "filename"), url = text(file, "url");
                if (!safeFilename(name) || !trustedDownload(url, id, name)) return null;
                JsonElement hashObject = file.get("hashes");
                if (hashObject == null || !hashObject.isJsonObject()) return null;
                String hash = text(hashObject.getAsJsonObject(), "sha512");
                if (!hash.matches("[a-fA-F0-9]{128}")) return null;
                JsonElement sizeElement = file.get("size");
                if (sizeElement == null || !sizeElement.isJsonPrimitive()
                        || !sizeElement.getAsJsonPrimitive().isNumber()
                        || !sizeElement.getAsString().matches("[0-9]{1,10}")) return null;
                long size = sizeElement.getAsLong();
                if (size <= 0 || size > MAX_JAR_BYTES) return null;
                selected = new Release(version, name, url, hash.toLowerCase(Locale.ROOT), size, minecraftVersion);
            }
            return selected;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** Older World Host protocols do not provide the authenticated, encrypted friends flow. */
    public static boolean isSecureVersion(String version) {
        if (version == null || !STABLE_VERSION.matcher(version).matches()) return false;
        return compareVersion(version, "0.5.0") >= 0;
    }

    private static int compareVersion(String left, String right) {
        Matcher a = STABLE_VERSION.matcher(left), b = STABLE_VERSION.matcher(right);
        if (!a.matches() || !b.matches()) throw new IllegalArgumentException("Invalid stable version");
        for (int index = 1; index <= 3; index++) {
            int difference = Integer.compare(Integer.parseInt(a.group(index)), Integer.parseInt(b.group(index)));
            if (difference != 0) return difference;
        }
        return 0;
    }

    public static String installedVersion(File gameDirectory) throws IOException {
        Installed installed = inspectInstalled(gameDirectory);
        return installed == null ? null : installed.version + (installed.disabled ? " (disabled)" : "");
    }

    /** Finds renamed and disabled World Host JARs by their Fabric manifest, never by filename alone. */
    public static synchronized Installed inspectInstalled(File gameDirectory) throws IOException {
        File directory = modsDirectory(gameDirectory, false);
        if (!directory.exists()) return null;
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Could not read this instance's mods. Check storage access.");
        Installed installed = null;
        for (File file : files) {
            String name = file.getName();
            boolean disabled = name.endsWith(".jar.disabled");
            if (!name.endsWith(".jar") && !disabled) continue;
            if (!file.isFile()) throw new IOException("A mod could not be inspected. Check this instance's Mods screen.");
            String version;
            try {
                version = worldHostVersion(file);
            } catch (IOException | RuntimeException invalid) {
                // Do not install a second copy beside a damaged/unknown existing mod.
                throw new IOException("A mod JAR could not be inspected. Check this instance's Mods screen before setup.");
            }
            if (version == null) continue;
            if (installed != null) throw new IOException("Multiple World Host copies are installed. Keep one copy in Mods.");
            if (file.length() > MAX_JAR_BYTES) throw new IOException("The existing World Host JAR is too large to verify.");
            try (InputStream stream = new FileInputStream(file)) {
                installed = new Installed(version, name, digest(stream, MAX_JAR_BYTES), disabled);
            }
        }
        return installed;
    }

    public static void install(Release release, File gameDirectory) throws IOException {
        install(release, gameDirectory, WorldHostInstaller::openHttps);
    }

    /** No automatic replacement: a user's installed/disabled copy must first be managed in Mods. */
    static synchronized void install(Release release, File gameDirectory, Transport transport) throws IOException {
        if (release == null) throw new IOException("Check for a compatible World Host release first.");
        if (inspectInstalled(gameDirectory) != null) {
            throw new IOException("World Host is already present. Manage the existing copy in Mods first.");
        }
        File directory = modsDirectory(gameDirectory, true);
        Path destination = new File(directory, release.filename).toPath();
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A file with this mod's name already exists. Check Mods before setup.");
        }
        Path staged = Files.createTempFile(directory.toPath(), ".insane-world-host-", ".part");
        try {
            MessageDigest digest = sha512();
            long count = 0, started = System.currentTimeMillis();
            try (InputStream input = transport.open(release.url); OutputStream output = Files.newOutputStream(staged)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("World Host download was interrupted. Try again.");
                    if (System.currentTimeMillis() - started > TRANSFER_TIMEOUT_MS) {
                        throw new IOException("World Host download timed out. Check your connection and try again.");
                    }
                    count += read;
                    if (count > release.size) throw new IOException("World Host download size did not match. Try again.");
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (count != release.size || !hex(digest.digest()).equals(release.sha512)) {
                throw new IOException("World Host download verification failed. Try again.");
            }
            if (!release.version.equals(worldHostVersion(staged.toFile()))) {
                throw new IOException("The download is not the expected World Host Fabric mod.");
            }
            // Recheck after network IO so a mod installed by another launcher screen isn't duplicated.
            if (inspectInstalled(gameDirectory) != null || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The mods folder changed during setup. Check Mods and try again.");
            }
            // Same-directory move exposes only a fully verified JAR and refuses an existing target.
            Files.move(staged, destination);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static File modsDirectory(File gameDirectory, boolean create) throws IOException {
        if (gameDirectory == null) throw new IOException("Choose a Minecraft instance first.");
        File root = gameDirectory.getCanonicalFile();
        File mods = new File(root, "mods");
        if (!mods.getCanonicalFile().getParentFile().equals(root)
                || Files.isSymbolicLink(mods.toPath())) {
            throw new IOException("Linked mods folders must be managed manually in Mods.");
        }
        if (create && !mods.isDirectory() && !mods.mkdirs()) {
            throw new IOException("Could not create the mods folder. Check available storage.");
        }
        if (mods.exists() && !mods.isDirectory()) throw new IOException("The mods folder is not a directory.");
        return mods;
    }

    private static String worldHostVersion(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return null;
            JsonElement document;
            try (InputStream stream = zip.getInputStream(entry)) {
                document = JsonParser.parseString(readText(stream, MAX_MANIFEST_BYTES));
            }
            if (!document.isJsonObject()) throw new IOException("Invalid Fabric manifest.");
            JsonObject manifest = document.getAsJsonObject();
            if (!"world-host".equals(text(manifest, "id"))) return null;
            String version = text(manifest, "version");
            if (version.isEmpty() || version.length() > 128) throw new IOException("Missing World Host version.");
            return version;
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid Fabric mod manifest.");
        }
    }

    private static InputStream openHttps(String address) throws IOException {
        URI uri;
        try { uri = URI.create(address); } catch (IllegalArgumentException error) {
            throw new IOException("Invalid World Host download address.");
        }
        boolean api = "api.modrinth.com".equals(uri.getHost())
                && ("/v2/project/" + PROJECT_ID + "/version").equals(uri.getPath());
        boolean cdn = "cdn.modrinth.com".equals(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/data/" + PROJECT_ID + "/versions/");
        if (!"https".equals(uri.getScheme()) || (!api && !cdn) || uri.getUserInfo() != null
                || uri.getPort() != -1 || uri.getFragment() != null || (cdn && uri.getQuery() != null)) {
            throw new IOException("Untrusted World Host download address.");
        }
        final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("User-Agent", "INSANE-CLIENT/WorldHost-Setup (github.com/insaneprinceyt/INSANE_CLIENT)");
        connection.setRequestProperty("Accept", api ? "application/json" : "application/java-archive");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Modrinth request failed (HTTP " + status + "). Try again later.");
            return new FilterInputStream(connection.getInputStream()) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { connection.disconnect(); }
                }
            };
        } catch (IOException error) {
            connection.disconnect();
            throw error;
        }
    }

    private static boolean trustedDownload(String address, String releaseId, String filename) {
        try {
            URI uri = URI.create(address);
            return "https".equals(uri.getScheme()) && "cdn.modrinth.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null
                    && uri.getFragment() == null
                    && ("/data/" + PROJECT_ID + "/versions/" + releaseId + "/" + filename).equals(uri.getPath());
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static boolean safeFilename(String name) {
        return name.length() <= 180 && name.matches("[A-Za-z0-9][A-Za-z0-9._+()-]*\\.jar");
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static boolean contains(JsonElement list, String expected) {
        if (list == null || !list.isJsonArray()) return false;
        for (JsonElement value : list.getAsJsonArray()) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && expected.equals(value.getAsString())) return true;
        }
        return false;
    }

    private static String readText(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long started = System.currentTimeMillis();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            if (output.size() + read > limit) throw new IOException("World Host metadata is too large.");
            if (Thread.currentThread().isInterrupted() || System.currentTimeMillis() - started > TRANSFER_TIMEOUT_MS) {
                throw new IOException("World Host metadata request timed out. Try again.");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String digest(InputStream stream, long limit) throws IOException {
        MessageDigest digest = sha512();
        long count = 0;
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            count += read;
            if (count > limit) throw new IOException("The mod is too large to verify.");
            digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha512() {
        try { return MessageDigest.getInstance("SHA-512"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
