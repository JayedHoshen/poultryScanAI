package com.example.poultryscanfinal;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    private Button btnScanNow;
    private LinearLayout cardLibrary, cardHistory, cardExpert, cardCommunity;
    private TextView tvLogout, tvGreeting;

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        btnScanNow = findViewById(R.id.btnScanNow);
        cardLibrary = findViewById(R.id.cardLibrary);
        cardHistory = findViewById(R.id.cardHistory);
        cardExpert = findViewById(R.id.cardExpert);
        cardCommunity = findViewById(R.id.cardCommunity);
        tvLogout = findViewById(R.id.tvLogout);
        tvGreeting = findViewById(R.id.tvGreeting);

        showGreeting();

        // Home -> Scan
        btnScanNow.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ScanActivity.class)));

        // Home -> Disease library
        cardLibrary.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, DiseaseLibraryActivity.class)));

        // Home -> Scan history
        cardHistory.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ScanHistoryActivity.class)));

        // Still placeholders, unchanged
        cardExpert.setOnClickListener(v -> notImplemented("Ask an expert"));
        cardCommunity.setOnClickListener(v -> notImplemented("Community"));

        tvLogout.setOnClickListener(v -> confirmLogout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager != null && !sessionManager.isLoggedIn()) {
            redirectToLogin();
        }
    }

    /**
     * Shows the cached name immediately, then confirms it against Room in the
     * background. That keeps the header instant while still being correct if the
     * stored name ever drifts from the database.
     */
    private void showGreeting() {
        String cachedName = sessionManager.getFullName();
        tvGreeting.setText(buildGreeting(cachedName));

        final int userId = sessionManager.getUserId();
        AppExecutors.diskIO().execute(() -> {
            User user = null;
            try {
                user = AppDatabase.getInstance(getApplicationContext()).userDao().findById(userId);
            } catch (RuntimeException e) {
                user = null; // Fall back to the cached name below.
            }
            final User loadedUser = user;
            AppExecutors.mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (loadedUser == null) {
                    // The session points at a user row that no longer exists.
                    if (cachedName == null || cachedName.trim().isEmpty()) {
                        sessionManager.clearSession();
                        redirectToLogin();
                    }
                    return;
                }
                sessionManager.updateFullName(loadedUser.fullName);
                tvGreeting.setText(buildGreeting(loadedUser.fullName));
            });
        });
    }

    private String buildGreeting(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getString(R.string.greeting_fallback);
        }
        return getString(R.string.greeting_format, name.trim());
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log out", (dialog, which) -> doLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Clears the session only. User rows, scan history and the disease library
     * all stay exactly where they are.
     */
    private void doLogout() {
        sessionManager.clearSession();
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void notImplemented(String feature) {
        Toast.makeText(this, feature + " screen coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        // Prevent going back to Login from Home since the auth back-stack was cleared
        moveTaskToBack(true);
    }
}
