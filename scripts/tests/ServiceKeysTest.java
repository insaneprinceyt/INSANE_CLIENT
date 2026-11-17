import com.ashmeet.hyperlauncher.utils.ServiceKeys;

/** Run with the standalone JDK; no service credentials or network calls are used. */
public final class ServiceKeysTest {
    private static int assertions;

    private static void equal(String expected, String actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("Assertion " + assertions + " failed");
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Assertion " + assertions + " failed");
    }

    public static void main(String[] args) {
        String[] absent = {null, "", "  ", "DUMMY", " dummy ", "DUMMY_KEY", "PLACEHOLDER",
            "gemini_placeholder_value", "YOUR_API_KEY", "your_curseforge_api_key",
            "YOUR_KEY_HERE", "CHANGE_ME", "replace-me", "null", "undefined", "TODO"};
        for (String value : absent) {
            equal("", ServiceKeys.normalize(value));
            equal(ServiceKeys.CURSEFORGE_MIRROR, ServiceKeys.curseForgeEndpoint(value));
        }
        // Reject header injection and invisible embedded control characters.
        for (String value : new String[]{"test\r\nX-Header: injected", "test\0value", "test\tvalue", "test\u007fvalue"}) {
            equal("", ServiceKeys.normalize(value));
        }
        String sampleKey = "synthetic-key-for-tests";
        equal(sampleKey, ServiceKeys.normalize("  " + sampleKey + "  "));
        equal(sampleKey, ServiceKeys.resolve(sampleKey, "bundled-test-key"));
        equal("bundled-test-key", ServiceKeys.resolve("DUMMY", "bundled-test-key"));
        equal("", ServiceKeys.resolve("PLACEHOLDER", "DUMMY"));
        equal("", ServiceKeys.resolve(null, null));
        equal(ServiceKeys.CURSEFORGE_OFFICIAL, ServiceKeys.curseForgeEndpoint(sampleKey));
        equal("CurseForge mirror", ServiceKeys.curseForgeLabel(""));
        equal("CurseForge", ServiceKeys.curseForgeLabel(sampleKey));
        check(ServiceKeys.curseForgeUnavailable("").contains("mirror"));
        check(ServiceKeys.curseForgeUnavailable(sampleKey).contains("Settings > Miscellaneous"));
        check(!ServiceKeys.curseForgeUnavailable(sampleKey).contains(sampleKey));
        System.out.println("ServiceKeysTest: " + assertions + " assertions passed");
    }
}
