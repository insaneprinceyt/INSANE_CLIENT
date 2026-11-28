package com.ashmeet.hyperbackend.instances;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Tests actual instance publication and rollback against temporary filesystem data. */
public final class PreparedInstanceTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static byte[] read(File file) throws IOException { return Files.readAllBytes(file.toPath()); }

    private static void put(File file, String value) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        Instance existing = Instances.createInstance(i -> {
            i.name = "Existing world";
            i.versionId = "26.1.1";
            i.icon = "default";
        }, "existing");
        Instances.setSelectedInstance(existing);
        File existingMod = new File(existing.getGameDirectory(), "mods/keep.jar");
        put(existingMod, "existing-mod");
        byte[] originalMetadata = read(Instances.metadataLocation(existing.mInstanceRoot));

        Instance ready = Instances.createPreparedInstance(i -> {
            i.name = "INSANE CLIENT";
            i.versionId = "fabric-loader-0.19.2-1.21.11";
            i.icon = "default";
            i.sharedData = false;
            i.insaneClientVersion = "1.1.2";
        }, "insane-client", i -> {
            check(i.getGameDirectory().equals(i.mInstanceRoot), "preset gets private storage");
            check(!Instances.metadataLocation(i.mInstanceRoot).exists(), "no metadata before preparation");
            check(Instances.loadAllInstances().size() == 1, "preparing preset is not listed");
            put(new File(i.getGameDirectory(), "mods/client.jar"), "verified-client");
            check(Instances.loadAllInstances().size() == 1, "partial preset is not listed");
            put(new File(i.getGameDirectory(), "mods/fabric-api.jar"), "verified-api");
        });
        check(Instances.metadataLocation(ready.mInstanceRoot).isFile(), "ready preset has metadata");
        check(Instances.loadAllInstances().size() == 2, "ready preset is listed once");
        Instance reread = Instances.loadAllInstances().stream()
                .filter(i -> "INSANE CLIENT".equals(i.name)).findFirst().get();
        check("1.1.2".equals(reread.insaneClientVersion), "client version persists");
        check("fabric-loader-0.19.2-1.21.11".equals(reread.versionId), "loader profile persists");
        check(!reread.sharedData, "private data persists");
        check(new File(reread.getGameDirectory(), "mods/client.jar").isFile(), "client is ready");
        check(new File(reread.getGameDirectory(), "mods/fabric-api.jar").isFile(), "dependency is ready");

        File[] failedRoot = new File[1];
        try {
            Instances.createPreparedInstance(i -> i.name = "Failed download", "failed", i -> {
                failedRoot[0] = i.mInstanceRoot;
                put(new File(i.getGameDirectory(), "staging/download.part"), "partial");
                throw new IOException("download failed");
            });
            throw new AssertionError("failed download returned success");
        } catch (IOException expected) {
            check("download failed".equals(expected.getMessage()), "original download error propagates");
        }
        check(!failedRoot[0].exists(), "failed download removes entire new instance");
        check(Instances.loadAllInstances().size() == 2, "failed download is not listed");

        try {
            Instances.createPreparedInstance(i -> i.name = "Cancelled", "cancelled", i -> {
                failedRoot[0] = i.mInstanceRoot;
                put(new File(i.getGameDirectory(), "mods/client.jar"), "verified-client");
                throw new CancellationException("screen closed");
            });
            throw new AssertionError("cancelled preparation returned success");
        } catch (CancellationException expected) {
            check("screen closed".equals(expected.getMessage()), "cancellation propagates");
        }
        check(!failedRoot[0].exists(), "cancelled preparation removes fresh files");

        try {
            Instances.createPreparedInstance(i -> {
                failedRoot[0] = i.mInstanceRoot;
                throw new IllegalArgumentException("invalid preset");
            }, "setter-failure", i -> { throw new AssertionError("preparation should not run"); });
            throw new AssertionError("invalid setter returned success");
        } catch (IllegalArgumentException expected) {
            check("invalid preset".equals(expected.getMessage()), "setter failure propagates");
        }
        check(!failedRoot[0].exists(), "setter failure cleans new directory");

        try {
            Instances.createPreparedInstance(i -> i.name = "Failed metadata", "metadata-failure", i -> {
                failedRoot[0] = i.mInstanceRoot;
                put(new File(i.getGameDirectory(), "mods/client.jar"), "verified-client");
                // A directory at the metadata location forces a real FileWriter failure.
                Files.createDirectory(Instances.metadataLocation(i.mInstanceRoot).toPath());
            });
            throw new AssertionError("failed metadata write returned success");
        } catch (IOException expected) {
            check(!failedRoot[0].exists(), "metadata-write failure rolls back prepared files");
        }

        check(Instances.loadAllInstances().size() == 2, "only complete instances remain");
        check(Arrays.equals(originalMetadata, read(Instances.metadataLocation(existing.mInstanceRoot))),
                "existing metadata stays unchanged");
        check("existing-mod".equals(new String(read(existingMod), StandardCharsets.UTF_8)),
                "existing mod stays unchanged");
        check(Instances.loadSelectedInstance().mInstanceRoot.equals(existing.mInstanceRoot),
                "preparing a preset does not replace the user's selection");
        System.out.println("PreparedInstanceTest: 5 scenarios, " + checks + " checks passed");
    }
}
