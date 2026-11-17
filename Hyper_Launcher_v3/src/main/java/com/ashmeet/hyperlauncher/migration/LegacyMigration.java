package com.ashmeet.hyperlauncher.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One-time upgrade using private backups and references to existing game data; no game files move. */
public final class LegacyMigration {
    private static final Object LOCK = new Object();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ACCOUNT = 1024 * 1024, MAX_PROFILES = 8 * 1024 * 1024;
    private final File dataRoot, gameRoot, backupRoot, instancesRoot;

    public static final class Result {
        public String selectedAccount = "", selectedInstance = "";
        public int accounts, instances;
    }

    public LegacyMigration(File dataRoot, File gameRoot) throws IOException {
        this.dataRoot = dataRoot.getCanonicalFile();
        this.gameRoot = gameRoot.getCanonicalFile();
        backupRoot = child(this.dataRoot, "insane-1x-backup");
        instancesRoot = child(this.gameRoot, "instances");
    }

    public Result migrate(String selectedAccount, String selectedProfile) throws IOException {
        synchronized (LOCK) {
            return migrateLocked(selectedAccount, selectedProfile);
        }
    }

    private Result migrateLocked(String selectedAccount, String selectedProfile) throws IOException {
        directory(backupRoot);
        File lockFile = child(backupRoot, ".lock");
        try (FileOutputStream lockStream = new FileOutputStream(lockFile, true);
             FileLock ignored = lockStream.getChannel().lock()) {
            File done = child(backupRoot, "migration-v1.json");
            if (done.isFile()) {
                JsonObject saved = object(read(done, 16 * 1024));
                if (saved.has("complete") && saved.get("complete").getAsBoolean())
                    return GSON.fromJson(saved, Result.class);
            }
            Result result = new Result();
            migrateAccounts(result, selectedAccount);
            migrateProfiles(result, selectedProfile);
            JsonObject saved = GSON.toJsonTree(result).getAsJsonObject();
            saved.addProperty("schema", 1);
            saved.addProperty("complete", true);
            atomicWrite(done, GSON.toJson(saved).getBytes(StandardCharsets.UTF_8));
            return result;
        } catch (RuntimeException error) {
            // JSON exceptions can include credential values; never attach them to user-visible logs.
            throw new IOException("Existing launcher data could not be upgraded. Original backups were kept.");
        }
    }

    private void migrateAccounts(Result result, String selection) throws IOException {
        File accounts = child(dataRoot, "accounts");
        if (!accounts.exists()) return;
        safeDirectory(accounts);
        File[] files = accounts.listFiles();
        if (files == null || files.length > 10000) throw new IOException("Cannot read existing accounts.");
        Set<String> names = new LinkedHashSet<>();
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".json.bak")) names.add(name.substring(0, name.length() - 4));
            else if (name.endsWith(".json")) names.add(name);
        }
        String chosen = selection == null ? "" : new File(selection).getName();
        if (!chosen.isEmpty() && !chosen.endsWith(".json")) chosen += ".json";
        for (String name : names) {
            interrupted();
            File target = child(accounts, name), oldBackup = child(accounts, name + ".bak");
            // A retry after another profile failed must not restore a stale AtomicFile backup
            // over an account already converted by this upgrade.
            if (target.isFile()) {
                try {
                    JsonObject current = object(read(target, MAX_ACCOUNT));
                    if (current.has("authType")) {
                        if (name.equals(chosen)) result.selectedAccount = name;
                        continue;
                    }
                } catch (IOException incomplete) {
                    if (!oldBackup.isFile()) throw incomplete;
                }
            }
            // An interrupted AtomicFile write leaves the authoritative old value in .bak.
            File source = oldBackup.isFile() ? oldBackup : target;
            if (!source.isFile()) continue;
            byte[] bytes = read(source, MAX_ACCOUNT);
            JsonObject value = object(bytes);
            if (value.has("authType")) {
                if (name.equals(chosen)) result.selectedAccount = name;
                continue;
            }
            if (!value.has("username") || !value.has("accessToken")) continue;
            backup("accounts/" + name, bytes);
            if (source.equals(oldBackup) && target.isFile()) backup("accounts-incomplete/" + name, read(target, MAX_ACCOUNT));
            boolean ely = bool(value, "isElyBy"), microsoft = bool(value, "isMicrosoft");
            value.addProperty("authType", ely ? "elyby" : microsoft ? "microsoft" : "local");
            value.addProperty("refreshToken", microsoft ? string(value, "msaRefreshToken", "0") : "0");
            if (ely) value.addProperty("legacyEly", true);
            if (!ely && !microsoft) addLocalAppearance(value);
            atomicWrite(target, GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
            if (name.equals(chosen)) result.selectedAccount = name;
            result.accounts++;
        }
    }

    private void addLocalAppearance(JsonObject account) throws IOException {
        String username = string(account, "username", "");
        if (!username.matches("[A-Za-z0-9_]{3,16}")) return;
        String id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
        File appearanceRoot = child(dataRoot, "local-appearance");
        if (!appearanceRoot.exists()) return;
        safeDirectory(appearanceRoot);
        File appearance = child(appearanceRoot, id);
        if (!appearance.isDirectory()) return;
        safeDirectory(appearance);
        File config = child(appearance, "appearance.json");
        if (!config.isFile()) return;
        JsonObject saved = object(read(config, 4096));
        for (String kind : new String[]{"skin", "cape"}) {
            String filename = string(saved, kind, "");
            if (!filename.matches("[a-f0-9]{64}\\.png")) continue;
            File texture = child(appearance, filename);
            if (texture.isFile()) {
                read(texture, 4 * 1024 * 1024); // bounded, no symlink; existing PNG is kept in place
                account.addProperty(kind + "Path", texture.getAbsolutePath());
            }
        }
        account.addProperty("skinModel", bool(saved, "slim") ? "ALEX" : "STEVE");
    }

    private void migrateProfiles(Result result, String selectedProfile) throws IOException {
        File minecraft = child(gameRoot, ".minecraft");
        if (!minecraft.exists()) return;
        safeDirectory(minecraft);
        File profiles = child(minecraft, "launcher_profiles.json");
        if (!profiles.isFile()) return;
        byte[] bytes = read(profiles, MAX_PROFILES);
        JsonObject document = object(bytes);
        if (!document.has("profiles") || !document.get("profiles").isJsonObject())
            throw new IOException("Existing installation metadata is invalid. Original data was kept.");
        JsonObject entries = document.getAsJsonObject("profiles");
        if (entries.size() > 10000) throw new IOException("Too many existing installations to upgrade safely.");
        backup("launcher_profiles.json", bytes);
        directory(instancesRoot);
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            interrupted();
            if (!entry.getValue().isJsonObject()) throw new IOException("An existing installation is invalid.");
            String key = entry.getKey();
            JsonObject old = entry.getValue().getAsJsonObject();
            String id = "insane-legacy-" + hash(key.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
            File wrapper = child(instancesRoot, id);
            File metadata = child(wrapper, "hyper_instance.json");
            if (metadata.exists()) {
                JsonObject existing = object(read(metadata, MAX_ACCOUNT));
                if (!key.equals(string(existing, "legacyProfileId", "")))
                    throw new IOException("An upgrade installation name is already in use.");
                if (key.equals(selectedProfile)) result.selectedInstance = id;
                continue; // preserve edits made after an interrupted/repeated upgrade
            }
            if (wrapper.exists()) {
                safeDirectory(wrapper);
                String[] contents = wrapper.list();
                if (contents == null || contents.length != 0)
                    throw new IOException("An upgrade installation folder is already in use.");
            }
            File legacyDirectory = resolveGameDirectory(string(old, "gameDir", ""), minecraft);
            if (legacyDirectory.equals(wrapper) || legacyDirectory.getPath().startsWith(wrapper.getPath() + File.separator))
                throw new IOException("An existing game directory overlaps its upgrade wrapper.");
            directory(wrapper);
            JsonObject converted = new JsonObject();
            converted.addProperty("name", string(old, "name", "Minecraft"));
            String version = string(old, "lastVersionId", "latest-release");
            if (version.equals("latest-release")) version = "latest_release";
            else if (version.equals("latest-snapshot")) version = "latest_snapshot";
            converted.addProperty("versionId", version);
            converted.addProperty("icon", "default");
            converted.addProperty("legacyProfileId", key);
            converted.addProperty("legacyGameDirectory", legacyDirectory.getAbsolutePath());
            converted.addProperty("sharedData", false);
            copyString(old, converted, "javaArgs", "jvmArgs");
            copyString(old, converted, "pojavRendererName", "renderer");
            converted.addProperty("argsMode", 0);
            String runtime = string(old, "javaDir", "");
            if (runtime.startsWith("amethyst://")) converted.addProperty("selectedRuntime", runtime.substring("amethyst://".length()));
            String controls = string(old, "controlFile", "");
            if (!controls.isEmpty()) converted.addProperty("legacyControlLayout", resolveControl(controls).getAbsolutePath());
            atomicWrite(metadata, GSON.toJson(converted).getBytes(StandardCharsets.UTF_8));
            if (key.equals(selectedProfile)) result.selectedInstance = id;
            result.instances++;
        }
    }

    private File resolveGameDirectory(String value, File defaultDirectory) throws IOException {
        if (value.isEmpty()) return defaultDirectory;
        File path;
        if (value.startsWith("amethyst://")) path = new File(gameRoot, value.substring("amethyst://".length()));
        // Match 1.x Tools.getGameDirPath, including its File(parent, child) semantics.
        else path = new File(gameRoot, value);
        File result = path.getCanonicalFile();
        if (result.getParentFile() == null) throw new IOException("Invalid existing game directory.");
        return result;
    }

    private File resolveControl(String value) throws IOException {
        // 1.x stores the selection relative to CTRLMAP_PATH.
        return new File(new File(gameRoot, "controlmap"), value).getCanonicalFile();
    }

    private void backup(String relative, byte[] bytes) throws IOException {
        File target = new File(backupRoot, relative);
        if (!target.getCanonicalPath().startsWith(backupRoot.getCanonicalPath() + File.separator))
            throw new IOException("Invalid backup location.");
        directory(target.getParentFile());
        if (target.exists()) { read(target, MAX_PROFILES); return; }
        atomicWrite(target, bytes);
    }

    private static File child(File parent, String name) throws IOException {
        File target = new File(parent, name);
        if (!target.getAbsoluteFile().equals(target.getCanonicalFile())) throw new IOException("Symbolic links are not supported during upgrade.");
        return target;
    }
    private static void directory(File path) throws IOException {
        if (!path.exists() && !path.mkdirs()) throw new IOException("Could not create upgrade storage.");
        safeDirectory(path);
    }
    private static void safeDirectory(File path) throws IOException {
        if (!path.isDirectory() || !path.getAbsoluteFile().equals(path.getCanonicalFile()))
            throw new IOException("Invalid upgrade storage directory.");
    }
    private static byte[] read(File path, int maximum) throws IOException {
        interrupted();
        if (!path.isFile() || Files.isSymbolicLink(path.toPath()) || path.length() > maximum)
            throw new IOException("Existing launcher data is missing or too large.");
        try (FileInputStream in = new FileInputStream(path)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int length;
            while ((length = in.read(buffer)) != -1) {
                if (out.size() + length > maximum) throw new IOException("Existing launcher data is too large.");
                out.write(buffer, 0, length);
            }
            return out.toByteArray();
        }
    }
    private static JsonObject object(byte[] bytes) throws IOException {
        try {
            JsonElement value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!value.isJsonObject()) throw new IOException("Existing launcher metadata is invalid.");
            return value.getAsJsonObject();
        } catch (RuntimeException error) { throw new IOException("Existing launcher metadata is invalid."); }
    }
    private static String string(JsonObject value, String key, String fallback) {
        JsonElement element = value.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }
    private static boolean bool(JsonObject value, String key) { return value.has(key) && !value.get(key).isJsonNull() && value.get(key).getAsBoolean(); }
    private static void copyString(JsonObject from, JsonObject to, String oldKey, String newKey) {
        String value = string(from, oldKey, ""); if (!value.isEmpty()) to.addProperty(newKey, value);
    }
    private static String hash(byte[] bytes) {
        try { StringBuilder result = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return result.toString(); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static void atomicWrite(File target, byte[] bytes) throws IOException {
        interrupted();
        if (Files.isSymbolicLink(target.toPath())) throw new IOException("Invalid upgrade target.");
        File temporary = File.createTempFile(".insane-upgrade-", ".tmp", target.getParentFile());
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(bytes); out.getFD().sync(); }
            interrupted();
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }
    private static void interrupted() throws InterruptedIOException { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Upgrade cancelled; original game data was kept."); }
}
