package com.ashmeet.hyperlauncher.utils.horizon;

import com.google.gson.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Official engine history. A release APK is not an INSANE native-engine adapter. */
public final class HorizonReleaseCatalog {
    private static final String[] REPOSITORIES = {"starling-client/starling", "JuanLuci/starling"};
    private static final long CACHE_AGE_MS = 6 * 60 * 60 * 1000L;
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    public static final class Release {
        public final String id, tag, name, publishedAt, releaseUrl, gameVersion;
        public final boolean prerelease, bundled;
        Release(String id, String tag, String name, String publishedAt, String releaseUrl,
                String gameVersion, boolean prerelease, boolean bundled) {
            this.id = id; this.tag = tag; this.name = name; this.publishedAt = publishedAt;
            this.releaseUrl = releaseUrl; this.gameVersion = gameVersion;
            this.prerelease = prerelease; this.bundled = bundled;
        }
    }

    public static final class Result {
        public final List<Release> releases;
        public final String notice;
        public final boolean fromCache;
        Result(List<Release> releases, String notice, boolean fromCache) {
            this.releases = Collections.unmodifiableList(releases);
            this.notice = notice; this.fromCache = fromCache;
        }
    }

    interface Transport { String get(String url) throws IOException; }

    private HorizonReleaseCatalog() {}

    public static Release bundledRelease() {
        return new Release("insane-horizon-0.7.7", "v0.7.7", "INSANE integrated build", "",
                "https://github.com/starling-client/starling/releases/tag/v0.7.7",
                "26.3", false, true);
    }

    /** Runs on a worker. Offline/rate-limit failures retain cached history and the bundled choice. */
    public static Result load(File cacheDirectory, boolean forceRefresh) {
        return load(cacheDirectory, forceRefresh, HorizonReleaseCatalog::download, System.currentTimeMillis());
    }

    static Result load(File directory, boolean force, Transport transport, long now) {
        Map<String, Release> official = new LinkedHashMap<>();
        boolean cached = false, failed = false, saveFailed = false;
        for (String repository : REPOSITORIES) {
            File cache = new File(directory, "rust-releases-" + repository.replace('/', '-') + ".json");
            JsonObject saved = readCache(cache);
            JsonArray rows = null;
            if (!force && saved != null && now >= saved.get("fetchedAt").getAsLong()
                    && now - saved.get("fetchedAt").getAsLong() < CACHE_AGE_MS) {
                rows = saved.getAsJsonArray("releases");
                cached = true;
            } else {
                try {
                    rows = fetchAll(repository, transport);
                    try { writeCache(cache, rows, now); } catch (IOException e) { saveFailed = true; }
                } catch (IOException | RuntimeException e) {
                    failed = true;
                    if (saved != null) {
                        rows = saved.getAsJsonArray("releases");
                        cached = true;
                    }
                }
            }
            if (rows != null) for (Release release : parse(repository, rows)) {
                // The official organization owns the preferred copy of duplicate tags.
                if (!official.containsKey(release.tag)) official.put(release.tag, release);
            }
        }
        List<Release> releases = new ArrayList<>(official.values());
        releases.sort((a, b) -> b.publishedAt.compareTo(a.publishedAt));
        releases.add(0, bundledRelease());
        String notice = failed
                ? (cached ? "Could not refresh every source. Showing saved releases where available."
                          : "Could not load every official release. Check your connection and tap Refresh.")
                : saveFailed ? "Release list loaded, but could not be saved for offline use." : "";
        return new Result(releases, notice, cached);
    }

    private static JsonArray fetchAll(String repository, Transport transport) throws IOException {
        JsonArray all = new JsonArray();
        for (int page = 1; page <= 10; page++) {
            String text = transport.get("https://api.github.com/repos/" + repository
                    + "/releases?per_page=100&page=" + page);
            if (text.length() > MAX_BYTES) throw new IOException("Release list is too large.");
            JsonElement json = JsonParser.parseString(text);
            if (!json.isJsonArray()) throw new IOException("Invalid release list.");
            JsonArray rows = json.getAsJsonArray();
            all.addAll(rows);
            if (rows.size() < 100) return all;
        }
        throw new IOException("Release history exceeded the supported catalog size.");
    }

    static List<Release> parse(String repository, JsonArray rows) {
        List<Release> releases = new ArrayList<>();
        for (JsonElement row : rows) {
            try {
                if (!row.isJsonObject()) continue;
                JsonObject value = row.getAsJsonObject();
                if (bool(value, "draft")) continue;
                String tag = string(value, "tag_name");
                String link = string(value, "html_url");
                if (tag.isEmpty() || tag.length() > 160 || !officialUrl(link, "/releases/tag/")) continue;
                JsonArray assets = value.getAsJsonArray("assets");
                boolean android = false;
                if (assets != null) for (JsonElement asset : assets) {
                    if (!asset.isJsonObject()) continue;
                    JsonObject file = asset.getAsJsonObject();
                    if (string(file, "name").toLowerCase(Locale.ROOT).endsWith(".apk")
                            && officialUrl(string(file, "browser_download_url"), "/releases/download/")) {
                        android = true; break;
                    }
                }
                if (!android) continue;
                String name = string(value, "name");
                releases.add(new Release(repository + ":" + tag, tag, name.isEmpty() ? tag : name,
                        string(value, "published_at"), link, "", bool(value, "prerelease"), false));
            } catch (RuntimeException ignored) {
                // A malformed entry must not hide the remaining valid Android releases.
            }
        }
        return releases;
    }

    private static boolean officialUrl(String link, String path) {
        try {
            URI uri = URI.create(link);
            if (!"https".equals(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getQuery() != null || uri.getFragment() != null) return false;
            String decodedPath = uri.getPath();
            if (decodedPath == null || decodedPath.indexOf('\\') >= 0) return false;
            for (String segment : decodedPath.split("/")) {
                if (segment.equals(".") || segment.equals("..")) return false;
            }
            for (String repo : REPOSITORIES) {
                String prefix = "/" + repo + path;
                if (decodedPath.startsWith(prefix) && decodedPath.length() > prefix.length()) return true;
            }
        } catch (IllegalArgumentException ignored) { }
        return false;
    }

    private static String string(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : "";
    }

    private static boolean bool(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static JsonObject readCache(File cache) {
        if (!cache.isFile() || cache.length() > MAX_BYTES) return null;
        try (InputStream input = new FileInputStream(cache)) {
            JsonObject value = JsonParser.parseString(readBounded(input)).getAsJsonObject();
            if (value.get("schema").getAsInt() != 1 || value.get("fetchedAt").getAsLong() <= 0
                    || !value.get("releases").isJsonArray()) return null;
            return value;
        } catch (IOException | RuntimeException ignored) { return null; }
    }

    private static void writeCache(File cache, JsonArray rows, long now) throws IOException {
        File parent = cache.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create catalog cache.");
        JsonObject value = new JsonObject();
        value.addProperty("schema", 1); value.addProperty("fetchedAt", now); value.add("releases", rows);
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Catalog cache is too large.");
        File temporary = File.createTempFile("rust-catalog-", ".partial", parent);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes); output.getFD().sync();
            }
            Files.move(temporary.toPath(), cache.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    private static String download(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(12000); connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "INSANE-CLIENT-Rust-Catalog");
            if (connection.getResponseCode() != 200) throw new IOException("Release server unavailable.");
            try (InputStream input = connection.getInputStream()) { return readBounded(input); }
        } finally { connection.disconnect(); }
    }

    private static String readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Catalog request interrupted.");
            if (output.size() + count > MAX_BYTES) throw new IOException("Release response is too large.");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
