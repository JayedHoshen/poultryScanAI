package com.example.poultryscanfinal;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    public static final int NO_USER = -1;

    private static final String PREF_NAME = "poultryscan_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_LOGGED_IN = "logged_in";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(int userId, String fullName) {
        prefs.edit()
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_FULL_NAME, fullName)
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false) && getUserId() != NO_USER;
    }

    /** Row id of the logged-in user, or {@link #NO_USER} when nobody is logged in. */
    public int getUserId() {
        int id = prefs.getInt(KEY_USER_ID, NO_USER);
        return id > 0 ? id : NO_USER;
    }

    public String getFullName() {
        return prefs.getString(KEY_FULL_NAME, null);
    }

    /** Keeps the cached display name in step with the database. */
    public void updateFullName(String fullName) {
        prefs.edit().putString(KEY_FULL_NAME, fullName).apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
