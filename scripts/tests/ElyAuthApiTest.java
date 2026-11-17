package com.ashmeet.hyperbackend.authenticator.legacy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Deterministic HTTP contract checks. No credentials or live requests are used. */
public final class ElyAuthApiTest {
    private static final String CLIENT = "9d1dc1bc-4180-45b7-a831-90b145fbd7cf";
    private static final String PROFILE = "9b30cbde-4a84-4bd6-9eb5-c76dfaf3fc73";
    private static int checks;

    public static void main(String[] args) throws Exception {
        String success = success("session.new");
        FakeConnection login = new FakeConnection(200, success);
        ElyAuthApi.Session session = ElyAuthApi.authenticate(
                " player@example.invalid ", "space : password ", CLIENT, "123456", login::open);
        check(session.username.equals("Player_1"), "profile name");
        check(session.profileId.equals(PROFILE), "canonical UUID");
        check(session.accessToken.equals("session.new"), "returned token");
        check(session.clientToken.equals(CLIENT), "returned client token");
        JsonObject request = login.request();
        check(request.get("username").getAsString().equals("player@example.invalid"), "login trimmed");
        check(request.get("password").getAsString().equals("space : password :123456"), "password preserved and TOTP appended");
        check(request.get("requestUser").getAsBoolean(), "requestUser requested");
        check(login.url.get().equals("https://authserver.ely.by/auth/authenticate"), "fixed HTTPS authenticate endpoint");
        check(!login.getInstanceFollowRedirects(), "redirects disabled");
        check(login.getConnectTimeout() > 0 && login.getReadTimeout() > 0, "timeouts bounded");
        check(login.getRequestMethod().equals("POST"), "POST request");
        check(login.getRequestProperty("Content-Type").equals("application/json; charset=UTF-8"), "JSON request");
        check(login.disconnected, "connection disconnected");
        check(login.responseClosed, "successful response stream closed");

        FakeConnection withoutOtp = new FakeConnection(200, success);
        ElyAuthApi.authenticate("player", "space : password ", CLIENT, "", withoutOtp::open);
        check(withoutOtp.request().get("password").getAsString().equals("space : password "),
                "password unchanged without OTP");
        String dashedUppercase = success.replace("9b30cbde4a844bd69eb5c76dfaf3fc73", PROFILE.toUpperCase(java.util.Locale.ROOT));
        FakeConnection canonical = new FakeConnection(200, dashedUppercase);
        check(ElyAuthApi.authenticate("player", "secret", CLIENT, null, canonical::open)
                .profileId.equals(PROFILE), "dashed uppercase UUID normalized");

        FakeConnection refresh = new FakeConnection(200, success);
        ElyAuthApi.refresh("session.old", CLIENT, refresh::open);
        JsonObject refreshRequest = refresh.request();
        check(refresh.url.get().equals("https://authserver.ely.by/auth/refresh"), "fixed refresh endpoint");
        check(refreshRequest.get("accessToken").getAsString().equals("session.old"), "saved token submitted for refresh");
        check(refreshRequest.get("clientToken").getAsString().equals(CLIENT), "same client token refreshed");
        check(!refreshRequest.has("password") && !refreshRequest.has("username"), "refresh excludes credentials");
        check(refreshRequest.get("requestUser").getAsBoolean(), "refresh requests user");

        assertAuthFailure(401, error("Account protected with two factor auth."), ElyAuthApi.Kind.TWO_FACTOR_REQUIRED);
        assertAuthFailure(401, error("Invalid credentials. Invalid email or password."), ElyAuthApi.Kind.INVALID_CREDENTIALS);
        assertAuthFailure(401, "not-json", ElyAuthApi.Kind.INVALID_CREDENTIALS);
        assertAuthFailure(403, error("Account protected with two factor auth."), ElyAuthApi.Kind.INVALID_CREDENTIALS);
        FakeConnection expired = new FakeConnection(401, error("Token expired."));
        expect(ElyAuthApi.Kind.INVALID_SESSION, 401, () -> ElyAuthApi.refresh("expired", CLIENT, expired::open));
        FakeConnection revoked = new FakeConnection(403, error("Forbidden"));
        expect(ElyAuthApi.Kind.INVALID_SESSION, 403, () -> ElyAuthApi.refresh("expired", CLIENT, revoked::open));
        assertAuthFailure(429, error("rate limited"), ElyAuthApi.Kind.SERVICE);
        assertAuthFailure(503, "upstream-unavailable", ElyAuthApi.Kind.SERVICE);
        assertAuthFailure(302, "", ElyAuthApi.Kind.SERVICE);
        assertAuthFailure(200, "<html>secret-body</html>", ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, "[]", ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("Player_1", "../account"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("9b30cbde4a844bd69eb5c76dfaf3fc73", "not-a-uuid"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace(CLIENT, "wrong-client"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("session.new", "token with spaces"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("\"session.new\"", "1234"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("\"session.new\"", "null"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("\"session.new\"", "\"\""), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("Player_1", "SeventeenChars1234"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("Player_1", ""), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("9b30cbde4a844bd69eb5c76dfaf3fc73", "1-1-1-1-1"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success + "{}", ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("\"accessToken\":", "accessToken:"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("\"accessToken\":", "\"accessToken\":\"other\",\"accessToken\":"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, success.replace("\"id\":", "\"id\":\"other\",\"id\":"), ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, "{\"accessToken\":\"session.new\",\"clientToken\":\"" + CLIENT + "\",\"selectedProfile\":null}", ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, "{}", ElyAuthApi.Kind.INVALID_RESPONSE);
        assertAuthFailure(200, repeat('x', 262145), ElyAuthApi.Kind.INVALID_RESPONSE);
        String deeplyNestedExtras = success.substring(0, success.length() - 1)
                + ",\"extra\":" + repeat('[', 10000) + "0" + repeat(']', 10000) + "}";
        FakeConnection extras = new FakeConnection(200, deeplyNestedExtras);
        check(ElyAuthApi.authenticate("player", "secret", CLIENT, null, extras::open)
                .username.equals("Player_1"), "deep extra fields do not consume call stack");
        expect(ElyAuthApi.Kind.INVALID_CREDENTIALS, -1, () -> ElyAuthApi.authenticate("", "secret", CLIENT, null));
        expect(ElyAuthApi.Kind.INVALID_CREDENTIALS, -1, () -> ElyAuthApi.authenticate(null, "secret", CLIENT, null));
        expect(ElyAuthApi.Kind.INVALID_CREDENTIALS, -1, () -> ElyAuthApi.authenticate("player", null, CLIENT, null));
        expect(ElyAuthApi.Kind.INVALID_CREDENTIALS, -1, () -> ElyAuthApi.authenticate("player", "secret", "", null));
        expect(ElyAuthApi.Kind.INVALID_CREDENTIALS, -1, () -> ElyAuthApi.authenticate("player", "secret", CLIENT, "abc123"));
        expect(ElyAuthApi.Kind.INVALID_SESSION, -1, () -> ElyAuthApi.refresh("", CLIENT));
        expect(ElyAuthApi.Kind.INVALID_SESSION, -1, () -> ElyAuthApi.refresh("token\n", CLIENT));
        expect(ElyAuthApi.Kind.INVALID_SESSION, -1, () -> ElyAuthApi.refresh("token", null));
        expect(ElyAuthApi.Kind.SERVICE, -1, () -> ElyAuthApi.authenticate("player", "secret", CLIENT, null,
                url -> { throw new IOException("secret-body"); }));
        check(checks >= 40, "test coverage executed");
        System.out.println("ElyAuthApi: " + checks + " checks passed (no live requests).");
    }

    private static String success(String token) {
        return "{\"accessToken\":\"" + token + "\",\"clientToken\":\"" + CLIENT
                + "\",\"selectedProfile\":{\"id\":\"9b30cbde4a844bd69eb5c76dfaf3fc73\",\"name\":\"Player_1\"}}";
    }

    private static String error(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", "ForbiddenOperationException");
        error.addProperty("errorMessage", message);
        return error.toString();
    }

    private static void assertAuthFailure(int status, String body, ElyAuthApi.Kind kind) throws Exception {
        FakeConnection connection = new FakeConnection(status, body);
        expect(kind, status, () -> ElyAuthApi.authenticate("player", "secret", CLIENT, null, connection::open));
        check(connection.disconnected, "failed connection disconnected");
    }

    private static void expect(ElyAuthApi.Kind kind, int status, Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + kind);
        } catch (ElyAuthApi.AuthException exception) {
            check(exception.kind == kind, "failure kind " + kind + " (was " + exception.kind + ")");
            check(exception.statusCode == status, "failure status " + status);
            check(!exception.toString().contains("secret") && exception.getCause() == null, "safe diagnostic");
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run() throws Exception; }

    private static final class FakeConnection extends HttpURLConnection {
        final java.util.concurrent.atomic.AtomicReference<String> url = new java.util.concurrent.atomic.AtomicReference<>();
        private final int status;
        private final byte[] body;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean disconnected;
        boolean responseClosed;

        FakeConnection(int status, String body) throws Exception {
            super(new URL("https://example.invalid"));
            this.status = status;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        HttpURLConnection open(URL endpoint) { url.set(endpoint.toString()); return this; }
        JsonObject request() { return new JsonParser().parse(new String(output.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject(); }
        @Override public OutputStream getOutputStream() { return output; }
        @Override public int getResponseCode() { return status; }
        private InputStream response() {
            return new ByteArrayInputStream(body) {
                @Override public void close() throws IOException { responseClosed = true; super.close(); }
            };
        }
        @Override public InputStream getInputStream() { return response(); }
        @Override public InputStream getErrorStream() { return response(); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
    }
}
