package com.nornorhub.fedfinder;

import android.content.Context;
import android.content.SharedPreferences;
import com.nornorhub.fedfinder.core.SearchQuery;
import org.json.JSONException;
import org.json.JSONObject;

/** USAJOBS API login and the last search form, kept in this app's private storage. */
final class Prefs {

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("fedfinder", Context.MODE_PRIVATE);
    }

    String email() {
        return sp.getString("email", "");
    }

    String apiKey() {
        return sp.getString("apiKey", "");
    }

    boolean hasKey() {
        return !email().isEmpty() && !apiKey().isEmpty();
    }

    void setKey(String email, String apiKey) {
        sp.edit().putString("email", email).putString("apiKey", apiKey).apply();
    }

    void clearKey() {
        sp.edit().remove("email").remove("apiKey").apply();
    }

    SearchQuery lastQuery() {
        try {
            return SearchQuery.fromJson(new JSONObject(sp.getString("lastQuery", "{}")));
        } catch (JSONException e) {
            return new SearchQuery();
        }
    }

    void setLastQuery(SearchQuery q) {
        try {
            sp.edit().putString("lastQuery", q.toJson().toString()).apply();
        } catch (JSONException ignored) {
            // Not worth failing a search over.
        }
    }
}
