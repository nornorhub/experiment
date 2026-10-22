package com.nornorhub.ghostbus;

import android.content.Context;
import android.content.SharedPreferences;

/** NJ Transit developer login and app state, kept in this app's private storage on the phone. */
final class Prefs {

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("ghostbus", Context.MODE_PRIVATE);
    }

    String username() {
        return sp.getString("username", "");
    }

    String password() {
        return sp.getString("password", "");
    }

    void setLogin(String username, String password) {
        sp.edit().putString("username", username).putString("password", password).remove("token").apply();
    }

    boolean hasLogin() {
        return !username().isEmpty() && !password().isEmpty();
    }

    String token() {
        return sp.getString("token", null);
    }

    void setToken(String token) {
        sp.edit().putString("token", token).apply();
    }

    long gtfsImportedAt() {
        return sp.getLong("gtfsImportedAt", 0);
    }

    void setGtfsImportedAt(long millis) {
        sp.edit().putLong("gtfsImportedAt", millis).apply();
    }

    void signOut() {
        sp.edit().clear().apply();
    }
}
