package com.nornorhub.ghostbus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * NJ Transit's bus GTFS API (pcsdata.njtransit.com/api/GTFS/). Every call is a POST: first
 * authenticateUser with the developer username and password returns a token, then each data
 * method is called with that token as a multipart form field.
 */
final class NjtApi {

    static final String BASE_URL = "https://pcsdata.njtransit.com/api/GTFS/";

    static final class AuthException extends IOException {
        AuthException(String message) {
            super(message);
        }
    }

    private final Prefs prefs;

    NjtApi(Prefs prefs) {
        this.prefs = prefs;
    }

    /** Logs in and stores the token. Throws AuthException if NJ Transit rejects the login. */
    String authenticate() throws IOException {
        String body = "username=" + URLEncoder.encode(prefs.username(), "UTF-8")
                + "&password=" + URLEncoder.encode(prefs.password(), "UTF-8");
        HttpURLConnection conn = open("authenticateUser", 30_000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        String text = readText(code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (code == 401 || code == 403) {
            throw new AuthException("NJ Transit rejected that username or password.");
        }
        if (code != 200) {
            throw new IOException("NJ Transit login failed (HTTP " + code + ")");
        }
        try {
            JSONObject json = new JSONObject(text);
            if (!"True".equalsIgnoreCase(json.optString("Authenticated"))) {
                throw new AuthException("NJ Transit rejected that username or password.");
            }
            String token = json.getString("UserToken");
            prefs.setToken(token);
            return token;
        } catch (JSONException e) {
            throw new IOException("Unexpected login response from NJ Transit: " + snippet(text));
        }
    }

    /** Calls a feed method such as getTripUpdates and returns the raw protobuf bytes. */
    byte[] fetchFeed(String method) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        call(method, out, 30_000, FEED);
        return out.toByteArray();
    }

    /** Downloads the static GTFS zip (all stops, routes and trips) to a file. */
    void downloadGtfs(File dest) throws IOException {
        try (OutputStream out = new FileOutputStream(dest)) {
            call("getGTFS", out, 180_000, ZIP);
        }
    }

    private interface Check {
        boolean looksRight(int firstByte, int secondByte);
    }

    // A GTFS-realtime FeedMessage always starts with its header (field 1, length-delimited: 0x0a).
    private static final Check FEED = (a, b) -> a == 0x0a;
    private static final Check ZIP = (a, b) -> a == 'P' && b == 'K';

    private void call(String method, OutputStream dest, int timeoutMs, Check check) throws IOException {
        String token = prefs.token();
        if (token == null) {
            token = authenticate();
        }
        if (!tryCall(method, token, dest, timeoutMs, check)) {
            // Tokens expire; NJ Transit answers with an error message instead of data. Log in again once.
            token = authenticate();
            if (!tryCall(method, token, dest, timeoutMs, check)) {
                throw new IOException("NJ Transit returned an error for " + method + ": " + lastError);
            }
        }
    }

    private String lastError = "";

    private boolean tryCall(String method, String token, OutputStream dest, int timeoutMs, Check check)
            throws IOException {
        String boundary = "----ghostbus" + UUID.randomUUID();
        HttpURLConnection conn = open(method, timeoutMs);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"token\"\r\n\r\n"
                + token + "\r\n"
                + "--" + boundary + "--\r\n";
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        try {
            if (code != 200) {
                InputStream err = conn.getErrorStream();
                lastError = "HTTP " + code + " " + snippet(err == null ? "" : readText(err));
                if (code == 401 || code == 403) {
                    return false;
                }
                throw new IOException("NJ Transit " + method + " failed: " + lastError);
            }
            PushbackInputStream in = new PushbackInputStream(conn.getInputStream(), 2);
            int a = in.read();
            int b = a < 0 ? -1 : in.read();
            if (!check.looksRight(a, b)) {
                byte[] head = a < 0 ? new byte[0] : b < 0 ? new byte[] {(byte) a} : new byte[] {(byte) a, (byte) b};
                lastError = snippet(new String(head, StandardCharsets.UTF_8) + readText(in));
                return false;
            }
            dest.write(a);
            if (b >= 0) {
                dest.write(b);
            }
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                dest.write(buf, 0, n);
            }
            return true;
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String method, int timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + method).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("User-Agent", "GhostBus/1.0 (Android)");
        return conn;
    }

    private static String readText(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }

    private static String snippet(String s) {
        s = s.trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
