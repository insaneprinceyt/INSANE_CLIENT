import com.ashmeet.hyperlauncher.utils.horizon.HorizonEngineAssets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Real filesystem and shipped-archive checks; no Android, network, or Gradle dependency. */
public final class HorizonEngineAssetsTest {
    private static int checks;
    private static int scenarios;
    private static final long PINNED_SIZE = 9121637L;
    private static final String PINNED_SHA256 = "3b68ff02893f8d1a380737d8008347b189b70796ef83679b12d4178a72ffde13";

    private interface Action { void run() throws Exception; }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void fails(Action action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            checks++;
        }
    }

    private static void run(String name, Action action) throws Exception {
        action.run();
        scenarios++;
        System.out.println("PASS " + name);
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    // An actual ZIP larger than the helper's copy buffer, with equal-sized distinct versions.
    private static byte[] archive(int version) throws IOException {
        byte[] contents = new byte[180000];
        Arrays.fill(contents, (byte) version);
        CRC32 crc = new CRC32();
        crc.update(contents);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry entry = new ZipEntry("engine/data.bin");
            entry.setTime(0);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(contents.length);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(contents);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static final class Fixture implements AutoCloseable {
        final Path root = Files.createTempDirectory("insane-horizon-assets-");
        final File destination = root.resolve("private/engine-data.zip").toFile();

        Fixture() throws IOException {}

        void put(byte[] bytes) throws IOException {
            Files.createDirectories(destination.toPath().getParent());
            Files.write(destination.toPath(), bytes);
        }

        void ensure(byte[] bytes) throws Exception {
            HorizonEngineAssets.ensure(destination, () -> new ByteArrayInputStream(bytes), bytes.length, hash(bytes));
        }

        void equal(byte[] bytes, String message) throws IOException {
            check(Arrays.equals(bytes, Files.readAllBytes(destination.toPath())), message);
        }

        void noPartials() throws IOException {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                check(!paths.anyMatch(path -> path.getFileName().toString().endsWith(".partial")),
                        "Temporary copies must be removed after success or failure");
            }
        }

        public void close() throws IOException {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }

    private static void freshAndCached(byte[] valid) throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicInteger opens = new AtomicInteger();
            AtomicInteger closes = new AtomicInteger();
            HorizonEngineAssets.ensure(f.destination, () -> {
                opens.incrementAndGet();
                return new ByteArrayInputStream(valid) {
                    @Override public void close() { closes.incrementAndGet(); }
                };
            }, valid.length, hash(valid));
            f.equal(valid, "A new private directory receives the complete verified archive");
            check(opens.get() == 1 && closes.get() == 1, "Asset source is opened and closed once");
            HorizonEngineAssets.ensure(f.destination, () -> {
                throw new AssertionError("A verified installed copy must work even when the source is unavailable");
            }, valid.length, hash(valid));
            f.equal(valid, "Verified cached archive is reused");
            f.noPartials();
        }
    }

    private static void corruptCaches(byte[] valid, byte[] prior) throws Exception {
        for (byte[] stale : new byte[][] {new byte[0], Arrays.copyOf(prior, prior.length - 1), prior}) {
            try (Fixture f = new Fixture()) {
                f.put(stale);
                AtomicInteger opens = new AtomicInteger();
                HorizonEngineAssets.ensure(f.destination, () -> {
                    opens.incrementAndGet();
                    return new ByteArrayInputStream(valid);
                }, valid.length, hash(valid));
                check(opens.get() == 1, "Empty, wrong-size, and wrong-hash caches must be replaced");
                f.equal(valid, "Corrupt cache is replaced by a complete verified copy");
                f.noPartials();
            }
        }
    }

    private static void rejectedCandidates(byte[] valid, byte[] prior) throws Exception {
        for (byte[] candidate : new byte[][] {Arrays.copyOf(valid, valid.length - 1),
                Arrays.copyOf(valid, valid.length + 1), prior}) {
            try (Fixture f = new Fixture()) {
                f.put(prior);
                fails(() -> HorizonEngineAssets.ensure(f.destination,
                        () -> new ByteArrayInputStream(candidate), valid.length, hash(valid)),
                        "Short, oversized, and equal-sized wrong-hash candidates must fail");
                f.equal(prior, "A rejected update must preserve the complete prior archive");
                f.noPartials();
                f.ensure(valid);
                f.equal(valid, "Verification failure permits a successful retry");
                f.noPartials();
            }
        }
        try (Fixture f = new Fixture()) {
            fails(() -> HorizonEngineAssets.ensure(f.destination,
                    () -> new ByteArrayInputStream(prior), valid.length, hash(valid)),
                    "Invalid first installation must fail");
            check(!f.destination.exists(), "Invalid first installation must never publish a destination");
            f.noPartials();
        }
    }

    private static void failedSources(byte[] valid, byte[] prior) throws Exception {
        for (int failurePoint = 0; failurePoint < 3; failurePoint++) {
            final int mode = failurePoint;
            try (Fixture f = new Fixture()) {
                f.put(prior);
                AtomicInteger closes = new AtomicInteger();
                fails(() -> HorizonEngineAssets.ensure(f.destination, () -> {
                    if (mode == 0) throw new IOException("Synthetic asset open failure");
                    return new FilterInputStream(new ByteArrayInputStream(valid)) {
                        private int reads;
                        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                            if (mode == 1 && reads++ == 1) throw new IOException("Synthetic interrupted copy");
                            return in.read(bytes, offset, length);
                        }
                        @Override public void close() throws IOException {
                            closes.incrementAndGet();
                            super.close();
                            if (mode == 2) throw new IOException("Synthetic source close failure");
                        }
                    };
                }, valid.length, hash(valid)), "Source open, read, and close errors must fail installation");
                check(closes.get() == (mode == 0 ? 0 : 1), "Every opened source closes after a failed copy");
                f.equal(prior, "Source failure preserves the complete prior archive");
                f.noPartials();
                f.ensure(valid);
                f.equal(valid, "Source failure permits retry");
            }
        }
    }

    private static void interruptedCopy(byte[] valid, byte[] prior) throws Exception {
        try (Fixture f = new Fixture()) {
            f.put(prior);
            Thread.currentThread().interrupt();
            try {
                fails(() -> f.ensure(valid), "An interrupted copy must fail");
                check(Thread.currentThread().isInterrupted(), "Copy preserves the caller's interrupt signal");
            } finally {
                Thread.interrupted();
            }
            f.equal(prior, "Interrupted copy preserves the complete prior archive");
            f.noPartials();
            f.ensure(valid);
            f.equal(valid, "Interrupted installation can retry");
        }
    }

    private static void atomicReplacement(byte[] valid, byte[] prior) throws Exception {
        try (Fixture f = new Fixture()) {
            f.put(prior);
            CountDownLatch copying = new CountDownLatch(1);
            CountDownLatch resume = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            String expectedHash = hash(valid);
            Thread worker = new Thread(() -> {
                try {
                    HorizonEngineAssets.ensure(f.destination, () -> new FilterInputStream(new ByteArrayInputStream(valid)) {
                        private int reads;
                        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                            if (reads++ == 1) {
                                copying.countDown();
                                try {
                                    if (!resume.await(10, TimeUnit.SECONDS)) throw new IOException("Test copy timed out");
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException(e);
                                }
                            }
                            return in.read(bytes, offset, length);
                        }
                    }, valid.length, expectedHash);
                } catch (Throwable error) {
                    failure.set(error);
                }
            }, "horizon-assets-atomic-test");
            try (InputStream oldReader = new FileInputStream(f.destination)) {
                worker.start();
                try {
                    check(copying.await(10, TimeUnit.SECONDS), "Copy must reach the controlled midpoint");
                    f.equal(prior, "Concurrent readers see the old complete archive during copy");
                } finally {
                    resume.countDown();
                    worker.join(10000);
                }
                check(!worker.isAlive(), "Atomic replacement worker completes");
                if (failure.get() != null) throw new AssertionError("Atomic replacement failed", failure.get());
                f.equal(valid, "New readers see the new complete archive after publication");
                check(Arrays.equals(prior, read(oldReader)), "An existing reader retains the prior complete file after rename");
            }
            f.noPartials();
        }
    }

    private static void filesystemFailures(byte[] valid) throws Exception {
        try (Fixture f = new Fixture()) {
            Files.write(f.destination.toPath().getParent(), new byte[] {1});
            fails(() -> f.ensure(valid), "A non-directory parent must fail cleanly");
            check(Arrays.equals(new byte[] {1}, Files.readAllBytes(f.destination.toPath().getParent())),
                    "A blocking parent file must be preserved");
            f.noPartials();
        }
        try (Fixture f = new Fixture()) {
            Files.createDirectories(f.destination.toPath());
            Path marker = f.destination.toPath().resolve("existing-data");
            Files.write(marker, new byte[] {42});
            fails(() -> f.ensure(valid), "A destination directory must prevent publication");
            check(Arrays.equals(new byte[] {42}, Files.readAllBytes(marker)), "Failed rename preserves existing destination contents");
            f.noPartials();
            Files.delete(marker);
            Files.delete(f.destination.toPath());
            f.ensure(valid);
            f.equal(valid, "Installation retries after a filesystem obstruction is resolved");
        }
    }

    private static void staleSymlinkReplacement(byte[] valid, byte[] prior) throws Exception {
        try (Fixture f = new Fixture()) {
            Path outside = f.root.resolve("outside.zip");
            Files.write(outside, prior);
            Files.createDirectories(f.destination.toPath().getParent());
            Files.createSymbolicLink(f.destination.toPath(), outside);
            f.ensure(valid);
            check(!Files.isSymbolicLink(f.destination.toPath()), "Atomic publication replaces a stale destination symlink");
            check(Arrays.equals(prior, Files.readAllBytes(outside)), "Replacing a stale destination symlink leaves its target unchanged");
            f.equal(valid, "The verified copy is published at the destination itself");
            f.noPartials();
        }
    }

    private static void bundledArchive(String aarPath) throws Exception {
        byte[] payload;
        try (ZipFile aar = new ZipFile(aarPath)) {
            ZipEntry entry = aar.getEntry("assets/engine-data.zip");
            check(entry != null, "Pinned AAR includes engine-data.zip");
            check(entry.getSize() == PINNED_SIZE, "Pinned AAR asset has the expected size");
            try (InputStream input = aar.getInputStream(entry)) { payload = read(input); }
        }
        check(payload.length == PINNED_SIZE && hash(payload).equals(PINNED_SHA256), "Actual asset bytes match the independent release pin");
        check(HorizonEngineAssets.BUNDLED_SIZE == PINNED_SIZE && HorizonEngineAssets.BUNDLED_SHA256.equals(PINNED_SHA256),
                "Production constants agree with the release pin");
        check(payload[0] == 'P' && payload[1] == 'K' && payload[2] == 3 && payload[3] == 4, "Bundled asset has a ZIP local-file header");
        try (Fixture f = new Fixture()) {
            HorizonEngineAssets.ensure(f.destination, () -> new ByteArrayInputStream(payload),
                    HorizonEngineAssets.BUNDLED_SIZE, HorizonEngineAssets.BUNDLED_SHA256);
            f.equal(payload, "Actual pinned asset installs through the production helper");
            Set<String> names = new HashSet<>();
            long uncompressed = 0;
            try (ZipFile zip = new ZipFile(f.destination)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                byte[] buffer = new byte[65536];
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    check(names.add(name), "Archive entries have unique names");
                    check(!name.startsWith("/") && !name.contains("\\") && !name.contains(":")
                                    && !Arrays.asList(name.split("/")).contains(".."),
                            "Bundled archive paths are relative and cannot escape extraction");
                    CRC32 crc = new CRC32();
                    long size = 0;
                    try (InputStream input = zip.getInputStream(entry)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            crc.update(buffer, 0, count);
                            size += count;
                        }
                    }
                    check(size == entry.getSize() && crc.getValue() == entry.getCrc(), "Every bundled entry has valid length and CRC: " + name);
                    uncompressed += size;
                }
            }
            check(names.contains("registries.json") && names.contains("items.json")
                    && names.contains("horizon-resources.json") && names.contains("worldgen/blocks/block-properties.nbt"),
                    "Bundled engine registries, items, resources, and block properties are present");
            check(names.size() == 33 && uncompressed == 126067935L, "All 33 pinned entries are fully checked");
            HorizonEngineAssets.ensure(f.destination, () -> { throw new AssertionError("Pinned asset cache was not reused"); },
                    HorizonEngineAssets.BUNDLED_SIZE, HorizonEngineAssets.BUNDLED_SHA256);
            f.noPartials();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected the pinned libhorizon_android.aar path");
        byte[] valid = archive(2);
        byte[] prior = archive(1);
        check(valid.length == prior.length && !hash(valid).equals(hash(prior)), "Fixture versions differ by hash at equal size");
        run("fresh install and verified cache reuse", () -> freshAndCached(valid));
        run("empty, wrong-size, and wrong-hash cache replacement", () -> corruptCaches(valid, prior));
        run("candidate integrity rejection, preservation, and retries", () -> rejectedCandidates(valid, prior));
        run("source open/read/close failure recovery", () -> failedSources(valid, prior));
        run("thread interruption and retry", () -> interruptedCopy(valid, prior));
        run("atomic publication with concurrent and existing readers", () -> atomicReplacement(valid, prior));
        run("filesystem obstruction preservation and retry", () -> filesystemFailures(valid));
        run("stale destination symlink target preservation", () -> staleSymlinkReplacement(valid, prior));
        run("actual AAR asset pin and full ZIP integrity", () -> bundledArchive(args[0]));
        System.out.println("HorizonEngineAssetsTest: " + scenarios + " scenarios, " + checks + " real-file checks passed");
    }
}
