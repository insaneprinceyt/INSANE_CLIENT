package com.ashmeet.hyperlauncher.utils.horizon;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Deterministic API fixtures through the transport seam and actual filesystem caches. */
public final class HorizonReleaseCatalogTest {
    private static final String ORGANIZATION = "starling-client/starling";
    private static final String LEGACY = "JuanLuci/starling";
    private static final long NOW = 1_800_000_000_000L;
    private static final long SIX_HOURS = 21_600_000L;
    private static int checks, scenarios;
    private static Path root;

    private interface Action { void run() throws Exception; }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void scenario(String name, Action action) throws Exception {
        action.run();
        scenarios++;
        System.out.println("PASS " + name);
    }
    private static File directory(String name) throws IOException {
        return Files.createDirectory(root.resolve(name)).toFile();
    }
    private static String endpoint(String repository, int page) {
        return "https://api.github.com/repos/" + repository + "/releases?per_page=100&page=" + page;
    }
    private static final class Transport implements HorizonReleaseCatalog.Transport {
        final Map<String, String> replies = new HashMap<>();
        final List<String> requested = new ArrayList<>();
        Transport page(String repository, int page, JsonArray rows) {
            replies.put(endpoint(repository, page), rows.toString());
            return this;
        }
        Transport text(String repository, int page, String text) {
            replies.put(endpoint(repository, page), text);
            return this;
        }
        @Override public String get(String url) throws IOException {
            requested.add(url);
            if (!replies.containsKey(url)) throw new IOException("Fixture: unavailable source/page");
            return replies.get(url);
        }
    }
    private static JsonObject release(String repository, String tag, String date) {
        JsonObject value = new JsonObject();
        value.addProperty("tag_name", tag);
        value.addProperty("name", "Engine " + tag);
        value.addProperty("published_at", date);
        value.addProperty("html_url", "https://github.com/" + repository + "/releases/tag/" + tag);
        value.addProperty("draft", false);
        value.addProperty("prerelease", false);
        JsonObject asset = new JsonObject();
        asset.addProperty("name", "engine-arm64-v8a.apk");
        asset.addProperty("browser_download_url", "https://github.com/" + repository
                + "/releases/download/" + tag + "/engine-arm64-v8a.apk");
        JsonArray assets = new JsonArray();
        assets.add(asset);
        value.add("assets", assets);
        return value;
    }
    private static JsonArray rows(JsonObject... values) {
        JsonArray result = new JsonArray();
        for (JsonObject value : values) result.add(value);
        return result;
    }
    private static JsonArray hundred(String prefix) {
        JsonArray result = new JsonArray();
        for (int index = 0; index < 100; index++) result.add(release(ORGANIZATION,
                prefix + index, "2026-11-01T00:00:00Z"));
        return result;
    }
    private static Transport normal(String organizationTag, String legacyTag) {
        return new Transport()
                .page(ORGANIZATION, 1, rows(release(ORGANIZATION, organizationTag, "2026-11-01T00:00:00Z")))
                .page(LEGACY, 1, rows(release(LEGACY, legacyTag, "2026-10-01T00:00:00Z")));
    }
    private static Path cache(File directory, String repository) {
        return directory.toPath().resolve("rust-releases-" + repository.replace('/', '-') + ".json");
    }
    private static boolean has(HorizonReleaseCatalog.Result result, String tag) {
        for (HorizonReleaseCatalog.Release release : result.releases)
            if (!release.bundled && release.tag.equals(tag)) return true;
        return false;
    }
    private static void bundledAndHistory(HorizonReleaseCatalog.Result result, int officialCount) {
        check(result.releases.size() == officialCount + 1, "wrong official release count");
        check(result.releases.get(0).bundled, "integrated build must remain first");
        check(result.releases.get(0).id.equals("insane-horizon-0.7.7"), "wrong integrated build identity");
        check(result.releases.get(0).gameVersion.equals("26.3"), "wrong integrated game target");
        for (int index = 1; index < result.releases.size(); index++) {
            check(!result.releases.get(index).bundled, "official APK marked internally installable");
            check(result.releases.get(index).gameVersion.isEmpty(), "unknown official game target fabricated");
        }
    }

    private static void freshAndForced() throws Exception {
        File dir = directory("fresh");
        Transport online = normal("v0.8.0", "v0.2.3");
        HorizonReleaseCatalog.Result first = HorizonReleaseCatalog.load(dir, false, online, NOW);
        bundledAndHistory(first, 2);
        check(first.notice.isEmpty() && !first.fromCache, "fresh source reported as fallback");
        check(online.requested.size() == 2, "did not request both official sources");
        check(Files.isRegularFile(cache(dir, ORGANIZATION)), "organization cache missing");
        check(Files.isRegularFile(cache(dir, LEGACY)), "legacy cache missing");
        Transport offline = new Transport();
        HorizonReleaseCatalog.Result cached = HorizonReleaseCatalog.load(dir, false, offline,
                NOW + SIX_HOURS - 1);
        bundledAndHistory(cached, 2);
        check(cached.fromCache && cached.notice.isEmpty(), "fresh cache not used normally");
        check(offline.requested.isEmpty(), "fresh cache unnecessarily made network requests");
        Transport refresh = normal("v0.8.1", "v0.2.4");
        HorizonReleaseCatalog.Result forced = HorizonReleaseCatalog.load(dir, true, refresh, NOW + 1);
        check(has(forced, "v0.8.1") && !has(forced, "v0.8.0"), "force refresh retained old source");
        check(refresh.requested.size() == 2 && !forced.fromCache, "force refresh skipped source");
        try {
            first.releases.clear();
            throw new AssertionError("release list is mutable");
        } catch (UnsupportedOperationException expected) { checks++; }
    }

    private static void staleFutureAndOffline() throws Exception {
        File dir = directory("stale");
        HorizonReleaseCatalog.load(dir, false, normal("v0.7.7", "v0.2.2"), NOW);
        Transport refresh = normal("v0.8.0", "v0.2.3");
        HorizonReleaseCatalog.Result expired = HorizonReleaseCatalog.load(dir, false, refresh, NOW + SIX_HOURS);
        check(has(expired, "v0.8.0") && refresh.requested.size() == 2, "six-hour cache boundary not refreshed");
        byte[] old = Files.readAllBytes(cache(dir, ORGANIZATION));
        Transport offline = new Transport();
        HorizonReleaseCatalog.Result fallback = HorizonReleaseCatalog.load(dir, true, offline, NOW + SIX_HOURS + 1);
        bundledAndHistory(fallback, 2);
        check(fallback.fromCache && !fallback.notice.isEmpty(), "offline cached result lacks disclosure");
        check(Arrays.equals(old, Files.readAllBytes(cache(dir, ORGANIZATION))), "failed refresh modified valid cache");
        Transport earlierClock = normal("v0.8.1", "v0.2.4");
        HorizonReleaseCatalog.Result future = HorizonReleaseCatalog.load(dir, false, earlierClock, NOW);
        check(has(future, "v0.8.1") && earlierClock.requested.size() == 2,
                "future-dated cache treated as fresh after clock rollback");
        HorizonReleaseCatalog.Result empty = HorizonReleaseCatalog.load(directory("offline-empty"), false,
                new Transport(), NOW);
        bundledAndHistory(empty, 0);
        check(!empty.fromCache && !empty.notice.isEmpty(), "offline without cache lacks error");
    }

    private static void partialSources() throws Exception {
        File dir = directory("partial");
        HorizonReleaseCatalog.load(dir, false, normal("v0.7.7", "v0.2.2"), NOW);
        Transport partial = new Transport().page(ORGANIZATION, 1,
                rows(release(ORGANIZATION, "v0.8.0", "2026-11-01T00:00:00Z")));
        HorizonReleaseCatalog.Result mixed = HorizonReleaseCatalog.load(dir, true, partial, NOW + 1);
        bundledAndHistory(mixed, 2);
        check(has(mixed, "v0.8.0") && has(mixed, "v0.2.2"), "partial refresh dropped available history");
        check(mixed.fromCache && !mixed.notice.isEmpty(), "partial refresh fallback not disclosed");
        Transport onlyLegacy = new Transport().page(LEGACY, 1,
                rows(release(LEGACY, "v0.2.3", "2026-10-01T00:00:00Z")));
        HorizonReleaseCatalog.Result one = HorizonReleaseCatalog.load(directory("only-legacy"), false,
                onlyLegacy, NOW);
        bundledAndHistory(one, 1);
        check(has(one, "v0.2.3") && !one.notice.isEmpty(), "first-source failure hid second source");
    }

    private static void corruptCaches() throws Exception {
        String[] corrupt = {"{", "[]", "{}", "{\"schema\":2,\"fetchedAt\":1,\"releases\":[]}",
                "{\"schema\":1,\"fetchedAt\":0,\"releases\":[]}",
                "{\"schema\":1,\"fetchedAt\":\"bad\",\"releases\":[]}",
                "{\"schema\":1,\"fetchedAt\":1,\"releases\":{}}"};
        for (int index = 0; index < corrupt.length; index++) {
            File dir = directory("corrupt-" + index);
            Files.write(cache(dir, ORGANIZATION), corrupt[index].getBytes(StandardCharsets.UTF_8));
            Files.write(cache(dir, LEGACY), corrupt[index].getBytes(StandardCharsets.UTF_8));
            HorizonReleaseCatalog.Result offline = HorizonReleaseCatalog.load(dir, false, new Transport(), NOW);
            bundledAndHistory(offline, 0);
            HorizonReleaseCatalog.Result repaired = HorizonReleaseCatalog.load(dir, false,
                    normal("v0.8.0", "v0.2.3"), NOW);
            bundledAndHistory(repaired, 2);
            check(repaired.notice.isEmpty() && !repaired.fromCache, "corrupt cache not replaced by valid response");
        }
    }

    private static void paginationAndFailure() throws Exception {
        File dir = directory("pagination");
        Transport pages = new Transport().page(ORGANIZATION, 1, hundred("page1-"))
                .page(ORGANIZATION, 2, rows(release(ORGANIZATION, "page2", "2026-11-02T00:00:00Z")))
                .page(LEGACY, 1, new JsonArray());
        HorizonReleaseCatalog.Result all = HorizonReleaseCatalog.load(dir, false, pages, NOW);
        bundledAndHistory(all, 101);
        check(pages.requested.size() == 3 && has(all, "page2"), "catalog omitted second page");
        check(all.releases.get(1).tag.equals("page2"), "release dates not sorted newest first");
        byte[] previous = Files.readAllBytes(cache(dir, ORGANIZATION));
        Transport failingPage = new Transport().page(ORGANIZATION, 1, hundred("new-"))
                .page(LEGACY, 1, new JsonArray());
        HorizonReleaseCatalog.Result fallback = HorizonReleaseCatalog.load(dir, true, failingPage, NOW + 1);
        bundledAndHistory(fallback, 101);
        check(has(fallback, "page2") && !has(fallback, "new-0"), "partial page published after next-page failure");
        check(fallback.fromCache && !fallback.notice.isEmpty(), "later-page failure not disclosed");
        check(Arrays.equals(previous, Files.readAllBytes(cache(dir, ORGANIZATION))), "later-page failure replaced old cache");
        Transport exact = new Transport().page(ORGANIZATION, 1, hundred("exact-"))
                .page(ORGANIZATION, 2, new JsonArray()).page(LEGACY, 1, new JsonArray());
        HorizonReleaseCatalog.Result boundary = HorizonReleaseCatalog.load(dir, true, exact, NOW + 2);
        bundledAndHistory(boundary, 100);
        check(exact.requested.size() == 3, "exactly 100 rows did not probe empty next page");
    }

    private static void sourceFilteringAndDedup() throws Exception {
        JsonArray mixed = new JsonArray();
        JsonObject valid = release(ORGANIZATION, "v0.7.7", "2026-11-01T00:00:00Z");
        valid.addProperty("name", "Organization choice");
        mixed.add(valid);
        JsonObject preview = release(ORGANIZATION, "preview", "2026-11-02T00:00:00Z");
        preview.addProperty("prerelease", true);
        mixed.add(preview);
        JsonObject draft = release(ORGANIZATION, "draft", "2026-11-03T00:00:00Z");
        draft.addProperty("draft", true); mixed.add(draft);
        JsonObject desktop = release(ORGANIZATION, "desktop", "2026-11-03T00:00:00Z");
        desktop.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("name", "engine.tar.gz"); mixed.add(desktop);
        mixed.add(JsonNull.INSTANCE); mixed.add(new JsonPrimitive("bad")); mixed.add(new JsonObject());
        JsonObject malformed = release(ORGANIZATION, "malformed", "2026-11-03T00:00:00Z");
        malformed.addProperty("assets", "wrong type"); mixed.add(malformed);
        JsonObject missingTag = release(ORGANIZATION, "missing", "2026-11-03T00:00:00Z");
        missingTag.remove("tag_name"); mixed.add(missingTag);
        JsonObject duplicate = release(LEGACY, "v0.7.7", "2026-11-04T00:00:00Z");
        duplicate.addProperty("name", "Legacy duplicate");
        Transport transport = new Transport().page(ORGANIZATION, 1, mixed).page(LEGACY, 1, rows(duplicate));
        HorizonReleaseCatalog.Result result = HorizonReleaseCatalog.load(directory("filter"), false, transport, NOW);
        bundledAndHistory(result, 2);
        check(result.releases.get(1).prerelease && result.releases.get(1).tag.equals("preview"), "preview filtering/sorting wrong");
        check(result.releases.get(2).name.equals("Organization choice"), "legacy duplicate overrode organization");
        check(!result.releases.get(2).bundled, "official v0.7.7 confused with bundled adapter");
        JsonObject noName = release(ORGANIZATION, "fallback-name", ""); noName.remove("name");
        check(HorizonReleaseCatalog.parse(ORGANIZATION, rows(noName)).get(0).name.equals("fallback-name"),
                "missing display name does not fall back to actual tag");
    }

    private static void unsafeUrls() {
        String[] badReleaseUrls = {
                "http://github.com/starling-client/starling/releases/tag/v1",
                "https://github.com.evil.example/starling-client/starling/releases/tag/v1",
                "https://user@github.com/starling-client/starling/releases/tag/v1",
                "https://github.com:443/starling-client/starling/releases/tag/v1",
                "https://github.com/attacker/starling/releases/tag/v1",
                "https://github.com/starling-client/starling-evil/releases/tag/v1",
                "javascript:alert(1)",
                "https://github.com/starling-client/starling/releases/tag/../../../../attacker/evil",
                "https://github.com/starling-client/starling/releases/tag/%2e%2e/%2e%2e/%2e%2e/evil"
        };
        for (String url : badReleaseUrls) {
            JsonObject row = release(ORGANIZATION, "v1", ""); row.addProperty("html_url", url);
            check(HorizonReleaseCatalog.parse(ORGANIZATION, rows(row)).isEmpty(), "unsafe release URL admitted: " + url);
        }
        String[] badAssets = {
                "https://evil.example/engine.apk",
                "http://github.com/starling-client/starling/releases/download/v1/a.apk",
                "https://github.com/starling-client/starling/releases/download/../../../../evil/a.apk"
        };
        for (String url : badAssets) {
            JsonObject row = release(ORGANIZATION, "v1", "");
            row.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("browser_download_url", url);
            check(HorizonReleaseCatalog.parse(ORGANIZATION, rows(row)).isEmpty(), "unsafe APK URL admitted: " + url);
        }
    }

    private static void failurePreservesAndRetry() throws Exception {
        File dir = directory("bad-refresh");
        HorizonReleaseCatalog.load(dir, false, normal("old", "legacy"), NOW);
        byte[] original = Files.readAllBytes(cache(dir, ORGANIZATION));
        char[] huge = new char[4 * 1024 * 1024 + 1]; Arrays.fill(huge, ' ');
        for (String response : new String[]{"{", "{\"message\":\"rate limit\"}", new String(huge)}) {
            Transport broken = new Transport().text(ORGANIZATION, 1, response).page(LEGACY, 1, new JsonArray());
            HorizonReleaseCatalog.Result result = HorizonReleaseCatalog.load(dir, true, broken, NOW + 1);
            check(has(result, "old") && result.fromCache && !result.notice.isEmpty(), "bad response lost prior cache");
            check(Arrays.equals(original, Files.readAllBytes(cache(dir, ORGANIZATION))), "bad response changed valid cache");
        }
        HorizonReleaseCatalog.Result retried = HorizonReleaseCatalog.load(dir, true, normal("new", "legacy-new"), NOW + 2);
        check(has(retried, "new") && !has(retried, "old") && retried.notice.isEmpty(), "retry failed after response error");
        File blocked = root.resolve("blocked-parent").toFile();
        Files.write(blocked.toPath(), new byte[]{42});
        HorizonReleaseCatalog.Result unsaved = HorizonReleaseCatalog.load(blocked, false, normal("live", "other"), NOW);
        bundledAndHistory(unsaved, 2);
        check(!unsaved.fromCache && !unsaved.notice.isEmpty(), "unsaved catalog failed to disclose storage error");
        check(Arrays.equals(new byte[]{42}, Files.readAllBytes(blocked.toPath())), "cache storage failure overwrote obstruction");
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            check(paths.noneMatch(path -> path.getFileName().toString().endsWith(".partial")), "partial cache file leaked");
        }
    }

    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("insane-catalog-test-");
        try {
            scenario("fresh cache, six-hour reuse, force refresh and immutable results", HorizonReleaseCatalogTest::freshAndForced);
            scenario("stale cache, clock rollback and offline fallback", HorizonReleaseCatalogTest::staleFutureAndOffline);
            scenario("independent source failures and mixed cached/live catalog", HorizonReleaseCatalogTest::partialSources);
            scenario("corrupt cache recovery", HorizonReleaseCatalogTest::corruptCaches);
            scenario("pagination boundary and failed later-page cache preservation", HorizonReleaseCatalogTest::paginationAndFailure);
            scenario("Android/draft/malformed filtering, preview identity and organization dedup", HorizonReleaseCatalogTest::sourceFilteringAndDedup);
            scenario("official URL confinement", HorizonReleaseCatalogTest::unsafeUrls);
            scenario("invalid/oversize response recovery and cache write failures", HorizonReleaseCatalogTest::failurePreservesAndRetry);
            System.out.println("HorizonReleaseCatalogTest: " + scenarios + " scenarios, " + checks + " checks passed");
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                Path[] all = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for (Path path : all) Files.deleteIfExists(path);
            }
        }
    }
}
