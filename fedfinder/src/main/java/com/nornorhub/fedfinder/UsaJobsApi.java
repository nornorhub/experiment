package com.nornorhub.fedfinder;

import com.nornorhub.fedfinder.core.SearchQuery;
import com.nornorhub.fedfinder.core.SearchResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONException;

/**
 * USAJOBS Search API. Every request carries the developer's email as User-Agent and their
 * API key as Authorization-Key; keys are free from https://developer.usajobs.gov/apirequest/.
 */
final class UsaJobsApi {

    static final String SEARCH_URL = "https://data.usajobs.gov/api/search";

    static final class AuthException extends IOException {
        AuthException(String message) {
            super(message);
        }
    }

    private final String email;
    private final String apiKey;

    UsaJobsApi(Prefs prefs) {
        this(prefs.email(), prefs.apiKey());
    }

    UsaJobsApi(String email, String apiKey) {
        this.email = email;
        this.apiKey = apiKey;
    }

    SearchResponse search(SearchQuery query) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(SEARCH_URL + "?" + query.toQueryString()).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("Host", "data.usajobs.gov");
        conn.setRequestProperty("User-Agent", email);
        conn.setRequestProperty("Authorization-Key", apiKey);
        conn.setRequestProperty("Accept", "application/json");
        try {
            int code = conn.getResponseCode();
            if (code == 401 || code == 403) {
                throw new AuthException("USAJOBS didn't accept that email and API key.");
            }
            String body = read(code < 400 ? conn.getInputStream() : conn.getErrorStream());
            if (code != 200) {
                throw new IOException("USAJOBS search failed (HTTP " + code + ") " + snippet(body));
            }
            try {
                return SearchResponse.parse(body);
            } catch (JSONException e) {
                throw new IOException("Unexpected response from USAJOBS: " + snippet(body));
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = s.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static String snippet(String s) {
        s = s.trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
