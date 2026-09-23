package com.example.poultryscanfinal;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the logged-in user's previous scans, newest first.
 *
 * Every query is filtered by the session's user id, so one user can never see
 * another user's history.
 */
public class ScanHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View emptyState;
    private TextView btnBack;
    private Button btnScanFirst;

    private ScanHistoryAdapter adapter;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_history);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        recyclerView = findViewById(R.id.recyclerHistory);
        emptyState = findViewById(R.id.emptyState);
        btnBack = findViewById(R.id.btnBack);
        btnScanFirst = findViewById(R.id.btnScanFirst);

        adapter = new ScanHistoryAdapter(new ArrayList<>());
        adapter.setOnItemClickListener(this::openDetail);
        adapter.setOnItemLongClickListener(this::confirmDelete);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnScanFirst.setOnClickListener(v -> {
            startActivity(new Intent(ScanHistoryActivity.this, ScanActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }
        loadHistory();
    }

    private void loadHistory() {
        final int userId = sessionManager.getUserId();
        AppExecutors.diskIO().execute(() -> {
            List<ScanHistory> records;
            try {
                records = AppDatabase.getInstance(getApplicationContext())
                        .scanHistoryDao()
                        .getUserHistory(userId);
            } catch (RuntimeException e) {
                records = null;
            }

            final List<ScanHistory> loaded = records;
            AppExecutors.mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (loaded == null) {
                    Toast.makeText(ScanHistoryActivity.this,
                            R.string.history_load_failed, Toast.LENGTH_SHORT).show();
                    showEmptyState(true);
                    return;
                }
                adapter.replaceAll(loaded);
                showEmptyState(loaded.isEmpty());
            });
        });
    }

    private void showEmptyState(boolean empty) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** Opens the stored record. The ML model is deliberately never re-run here. */
    private void openDetail(ScanHistory record) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra(ResultActivity.EXTRA_FROM_HISTORY, true);
        intent.putExtra(ResultActivity.EXTRA_DISEASE, record.diseaseKey);
        intent.putExtra(ResultActivity.EXTRA_CONFIDENCE, record.confidence);
        intent.putExtra(ResultActivity.EXTRA_IMAGE_PATH, record.imagePath);
        intent.putExtra(ResultActivity.EXTRA_TIMESTAMP, record.timestamp);
        startActivity(intent);
    }

    private void confirmDelete(ScanHistory record) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_scan_title)
                .setMessage(R.string.delete_scan_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteRecord(record))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteRecord(ScanHistory record) {
        AppExecutors.diskIO().execute(() -> {
            boolean deleted;
            try {
                AppDatabase.getInstance(getApplicationContext())
                        .scanHistoryDao()
                        .deleteScan(record);
                ImageUtils.deleteScanImage(record.imagePath);
                deleted = true;
            } catch (RuntimeException e) {
                deleted = false;
            }

            final boolean success = deleted;
            AppExecutors.mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (success) {
                    loadHistory();
                } else {
                    Toast.makeText(ScanHistoryActivity.this,
                            R.string.delete_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
