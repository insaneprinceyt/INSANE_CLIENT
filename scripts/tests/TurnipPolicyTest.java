import com.ashmeet.hyperbackend.vulkan.TurnipPolicy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Device examples and native-switch transitions through the production policy. */
public final class TurnipPolicyTest {
    private static int checks;
    private static int scenarios;
    private interface Scenario { void run() throws Exception; }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void run(String name, Scenario scenario) throws Exception {
        scenario.run();
        scenarios++;
        System.out.println("PASS " + name);
    }

    /** The JNI boundary is represented by an actual mutable switch, initially left enabled. */
    private static final class NativeSwitch implements TurnipPolicy.DriverSwitch {
        boolean enabled = true;
        final List<Boolean> writes = new ArrayList<>();

        @Override public void setEnabled(boolean value) {
            enabled = value;
            writes.add(value);
        }
    }

    private static void expectSelection(String vendor, String renderer, int api,
                                        boolean preferSystem, boolean expected, String description) {
        NativeSwitch nativeSwitch = new NativeSwitch();
        boolean selected = TurnipPolicy.configure(vendor, renderer, api, preferSystem, nativeSwitch);
        check(selected == expected, description + ": returned the wrong driver selection");
        check(nativeSwitch.enabled == expected, description + ": native driver state was not updated");
        check(nativeSwitch.writes.equals(Arrays.asList(expected)),
                description + ": must publish one explicit native selection, including false");
    }

    private static String source(Path root, String path) throws Exception {
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8);
    }

    private static int count(String text, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    public static void main(String[] args) throws Exception {
        run("reported Mali-G68 device disables Turnip", () -> {
            // User's log: ARM Mali-G68 MC4, Android API34, Zink. The native switch must
            // be cleared even if an earlier path in the same process enabled it.
            expectSelection("ARM", "Mali-G68 MC4", 34, false, false, "reported Xiaomi device");
            expectSelection("ARM", "Mali-G68 MC4", 34, true, false, "Mali system-driver preference");
            expectSelection("ARM", "Mali-G68 MC4", 28, false, false, "Mali at native API boundary");
            expectSelection("ARM", "Mali-G68 MC4", 37, false, false, "Mali on newer Android");
        });

        run("known Qualcomm Adreno devices remain eligible", () -> {
            expectSelection("Qualcomm", "Adreno (TM) 640", 28, false, true, "Adreno640/API28");
            expectSelection("Qualcomm", "Adreno (TM) 730", 33, false, true, "Adreno730/API33");
            expectSelection("Qualcomm", "Adreno (TM) 740", 34, false, true, "Adreno740/API34");
        });

        run("non-Adreno and ambiguous GPU identities use the system driver", () -> {
            String[][] unsupported = {
                    {"ARM", "Immortalis-G715"},
                    {"Imagination Technologies", "PowerVR Rogue GE8320"},
                    {"NVIDIA Corporation", "NVIDIA Tegra"},
                    {"Google", "Android Emulator OpenGL ES Translator"},
                    {"Qualcomm", "Mali-G68 MC4"},
                    {"ARM", "Adreno (TM) 740"},
                    {"Google", "ANGLE (Qualcomm, Adreno (TM) 740, OpenGL ES)"}
            };
            for (String[] gpu : unsupported) {
                expectSelection(gpu[0], gpu[1], 34, false, false, gpu[0] + "/" + gpu[1]);
            }
        });

        run("Android namespace API boundary is respected", () -> {
            expectSelection("Qualcomm", "Adreno (TM) 640", 26, false, false, "Android API26");
            expectSelection("Qualcomm", "Adreno (TM) 640", 27, false, false, "Android API27");
            expectSelection("Qualcomm", "Adreno (TM) 640", 28, false, true, "Android API28");
        });

        run("explicit system-driver choice always overrides eligible hardware", () -> {
            expectSelection("Qualcomm", "Adreno (TM) 640", 28, true, false, "system preference/API28");
            expectSelection("Qualcomm", "Adreno (TM) 740", 34, true, false, "system preference/API34");
            expectSelection("Qualcomm", "Adreno (TM) 740", 37, true, false, "system preference/newer Android");
        });

        run("missing GPU information fails closed without throwing", () -> {
            String[][] unknown = {
                    {null, null}, {null, "Adreno (TM) 740"}, {"Qualcomm", null},
                    {"", ""}, {"<Unknown>", "<Unknown>"},
                    {"", "Adreno (TM) 740"}, {"Qualcomm", ""}
            };
            for (String[] gpu : unknown) {
                expectSelection(gpu[0], gpu[1], 34, false, false, "missing/unknown GPU identity");
            }
        });

        run("reconfiguration clears stale native enablement on every rejected launch", () -> {
            NativeSwitch nativeSwitch = new NativeSwitch();
            TurnipPolicy.configure("Qualcomm", "Adreno (TM) 740", 34, false, nativeSwitch);
            TurnipPolicy.configure("ARM", "Mali-G68 MC4", 34, false, nativeSwitch);
            check(!nativeSwitch.enabled, "Switching from Adreno to Mali must clear the native flag");
            TurnipPolicy.configure("Qualcomm", "Adreno (TM) 740", 34, false, nativeSwitch);
            TurnipPolicy.configure("Qualcomm", "Adreno (TM) 740", 34, true, nativeSwitch);
            check(!nativeSwitch.enabled, "Enabling the system-driver preference must clear the native flag");
            TurnipPolicy.configure("Qualcomm", "Adreno (TM) 740", 27, false, nativeSwitch);
            TurnipPolicy.configure("Qualcomm", "Adreno (TM) 740", 28, false, nativeSwitch);
            TurnipPolicy.configure("<Unknown>", "<Unknown>", 34, false, nativeSwitch);
            check(!nativeSwitch.enabled, "A failed subsequent GPU query must clear the native flag");
            check(nativeSwitch.writes.equals(Arrays.asList(true, false, true, false, false, true, false)),
                    "Every launch must explicitly configure native driver state; false must not be skipped");
        });

        run("all existing launch entry points use the shared driver selector", () -> {
            if (args.length != 1) throw new IllegalArgumentException("Pass repository root");
            Path root = Paths.get(args[0]);
            String prefix = "Hyper_Launcher_v3/src/main/java/com/ashmeet/";
            String renderer = source(root, prefix + "hyperbackend/game/renderer/GameRenderer.java");
            String activity = source(root, prefix + "hyperbackend/game/GameActivity.java");
            String runner = source(root, prefix + "hyperlauncher/utils/jre/GameRunner.kt");
            // Supplementary wiring checks: behavioral device/transition tests above are the
            // primary evidence. These stop old launch paths bypassing that verified selector.
            check(count(renderer, "TurnipPolicy\\.configure\\s*\\(") == 1,
                    "GameRenderer must delegate selection to the tested policy");
            check(renderer.contains("MojoExec::setUseTurnip"),
                    "The policy must control the real JNI driver switch");
            check(count(activity, "mGameRenderer\\.configureVulkanDriver\\s*\\(") == 2,
                    "Both GameActivity initialization paths must configure the driver");
            check(count(runner, "gameRenderer\\.configureVulkanDriver\\s*\\(") == 1,
                    "GameRunner must configure the driver before renderer setup");
            check(!activity.contains("overrideVulkanDriver") && !runner.contains("overrideVulkanDriver")
                            && !renderer.contains("overrideVulkanDriver"),
                    "The former unconditional override entry point must be removed");
            check(count(renderer + activity + runner, "setUseTurnip\\s*\\(\\s*true\\s*\\)") == 0,
                    "No launch path may directly force Turnip on");
        });

        System.out.println("Turnip policy: " + scenarios + " scenarios, " + checks + " checks passed");
    }
}
