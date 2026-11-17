package com.ashmeet.hyperbackend.authenticator.legacy;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Ely.by's documented Yggdrasil authentication API. Never persists passwords. */
public final class ElyAuthApi {
    private static final String AUTH_ROOT = "https://authserver.ely.by/auth/";
    private static final String TWO_FACTOR_ERROR = "Account protected with two factor auth.";
    private static final int RESPONSE_LIMIT = 256 * 1024;
    private static final ConnectionFactory NETWORK = url -> (HttpURLConnection) url.openConnection();

    private ElyAuthApi() {}

    public enum Kind {
        INVALID_CREDENTIALS, TWO_FACTOR_REQUIRED, INVALID_SESSION, SERVICE, INVALID_RESPONSE
    }

    /** Messages contain no submitted credentials, service response bodies, or nested exceptions. */
    public static final class AuthException extends IOException {
        public final Kind kind;
        public final int statusCode;

        private AuthException(Kind kind, int statusCode) {
            super("Ely.by authentication: " + kind.name() + " (HTTP " + statusCode + ")");
            this.kind = kind;
            this.statusCode = statusCode;
        }
    }

    public static final class Session {
        public final String accessToken;
        public final String clientToken;
        public final String username;
        public final String profileId;

        private Session(String accessToken, String clientToken, String username, String profileId) {
            this.accessToken = accessToken;
            this.clientToken = clientToken;
            this.username = username;
            this.profileId = profileId;
        }
    }

    interface ConnectionFactory {
        HttpURLConnection open(URL endpoint) throws IOException;
    }

    public static Session authenticate(String login, String password, String clientToken, String otp)
            throws AuthException {
        return authenticate(login, password, clientToken, otp, NETWORK);
    }

    static Session authenticate(String login, String password, String clientToken, String otp,
                                ConnectionFactory factory) throws AuthException {
        String username = login == null ? "" : login.trim();
        String code = otp == null ? "" : otp.trim();
        if (username.isEmpty() || username.length() > 320 || password == null
                || password.isEmpty() || password.length() > 4096 || !validToken(clientToken)
                || (!code.isEmpty() && !code.matches("[0-9]{6}"))) {
            throw new AuthException(Kind.INVALID_CREDENTIALS, -1);
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", code.isEmpty() ? password : password + ":" + code);
        payload.addProperty("clientToken", clientToken);
        payload.addProperty("requestUser", true);
        return exchange("authenticate", payload, clientToken, false, factory);
    }

    public static Session refresh(String accessToken, String clientToken) throws AuthException {
        return refresh(accessToken, clientToken, NETWORK);
    }

    static Session refresh(String accessToken, String clientToken, ConnectionFactory factory)
            throws AuthException {
        if (!validToken(accessToken) || !validToken(clientToken)) {
            throw new AuthException(Kind.INVALID_SESSION, -1);
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("accessToken", accessToken);
        payload.addProperty("clientToken", clientToken);
        payload.addProperty("requestUser", true);
        return exchange("refresh", payload, clientToken, true, factory);
    }

    private static Session exchange(String operation, JsonObject payload, String clientToken,
                                    boolean refresh, ConnectionFactory factory) throws AuthException {
        HttpURLConnection connection = null;
        int status = -1;
        try {
            connection = factory.open(new URL(AUTH_ROOT + operation));
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] request = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(request.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
            }
            status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                String body = readBody(connection.getInputStream(), status);
                return parseSession(body, clientToken, status);
            }
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED
                    || status == HttpURLConnection.HTTP_FORBIDDEN) {
                String body = readBody(connection.getErrorStream(), status);
                if (!refresh && status == HttpURLConnection.HTTP_UNAUTHORIZED && requiresOtp(body)) {
                    throw new AuthException(Kind.TWO_FACTOR_REQUIRED, status);
                }
                throw new AuthException(refresh ? Kind.INVALID_SESSION : Kind.INVALID_CREDENTIALS, status);
            }
            // Includes redirects: credentials must never be forwarded to another endpoint.
            throw new AuthException(Kind.SERVICE, status);
        } catch (AuthException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // An underlying exception may include a URL, token or response body. Do not retain it.
            throw new AuthException(Kind.SERVICE, status);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBody(InputStream stream, int status) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > RESPONSE_LIMIT) {
                    throw new AuthException(Kind.INVALID_RESPONSE, status);
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static Session parseSession(String body, String expectedClientToken, int status)
            throws AuthException {
        String accessToken = null;
        String clientToken = null;
        String username = null;
        String profileId = null;
        boolean hasProfile = false;
        // Streaming parsing also bounds stack use when a service returns deeply nested extra data.
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "accessToken":
                        if (accessToken != null) throw new IOException();
                        accessToken = readString(reader);
                        break;
                    case "clientToken":
                        if (clientToken != null) throw new IOException();
                        clientToken = readString(reader);
                        break;
                    case "selectedProfile":
                        if (hasProfile) throw new IOException();
                        hasProfile = true;
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String name = reader.nextName();
                            if (name.equals("id")) {
                                if (profileId != null) throw new IOException();
                                profileId = readString(reader);
                            } else if (name.equals("name")) {
                                if (username != null) throw new IOException();
                                username = readString(reader);
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                        break;
                    default:
                        reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || !validToken(accessToken)
                    || !expectedClientToken.equals(clientToken) || username == null
                    || !username.matches("[A-Za-z0-9_]{1,16}")) throw new IOException();
            profileId = canonicalUuid(profileId);
            if (profileId == null) throw new IOException();
            return new Session(accessToken, clientToken, username, profileId);
        } catch (IOException | RuntimeException exception) {
            throw new AuthException(Kind.INVALID_RESPONSE, status);
        }
    }

    private static boolean requiresOtp(String body) {
        String error = null;
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("errorMessage")) {
                    if (error != null) return false;
                    error = readString(reader);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return reader.peek() == JsonToken.END_DOCUMENT && TWO_FACTOR_ERROR.equals(error);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static String readString(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw new IOException();
        return reader.nextString();
    }

    static boolean validToken(String value) {
        if (value == null || value.isEmpty() || value.length() > 16384) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < '!' || character > '~') return false;
        }
        return true;
    }

    static String canonicalUuid(String value) {
        if (value == null) return null;
        if (value.matches("[0-9a-fA-F]{32}")) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                    + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20);
        } else if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
