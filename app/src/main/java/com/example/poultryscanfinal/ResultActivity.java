package com.example.poultryscanfinal;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Locale;

/**
 * Shows the outcome of a scan: the scanned image, the detected class, the
 * confidence, and general husbandry guidance.
 *
 * The same screen serves two modes:
 *   live scan  - arrives from ScanActivity, and the result is saved to history
 *   history    - arrives from ScanHistoryActivity with EXTRA_FROM_HISTORY set,
 *                reads the stored record, saves nothing and NEVER re-runs the
 *                ML model
 *
 * Everything here is framed as an AI screening result, never as a diagnosis.
 */
public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_DISEASE = "extra_disease";
    public static final String EXTRA_CONFIDENCE = "extra_confidence";
    public static final String EXTRA_IMAGE_URI = "extra_image_uri";
    public static final String EXTRA_ALL_SCORES = "extra_all_scores";
    public static final String EXTRA_ALL_LABELS = "extra_all_labels";

    /** Set by ScanHistoryActivity so this screen knows not to save again. */
    public static final String EXTRA_FROM_HISTORY = "extra_from_history";
    /** Absolute path of the stored JPEG, used in history mode. */
    public static final String EXTRA_IMAGE_PATH = "extra_image_path";
    /** Scan time in millis, used in history mode. */
    public static final String EXTRA_TIMESTAMP = "extra_timestamp";

    private static final String STATE_TIMESTAMP = "state_timestamp";

    /**
     * Minimum probability before a class is presented as a finding.
     *
     * No validation metrics were supplied with this model, so this is a
     * conservative default rather than a tuned operating point. If a validation
     * set is ever evaluated, pick the threshold from that instead.
     */
    public static final float CONFIDENCE_THRESHOLD = 0.60f;

    private static final int COLOR_HEALTHY = Color.parseColor("#2E7D32");
    private static final int COLOR_DISEASE = Color.parseColor("#C62828");
    private static final int COLOR_UNCERTAIN = Color.parseColor("#E65100");

    private long scanTimestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        ImageView ivResultImage = findViewById(R.id.ivResultImage);
        TextView tvDiseaseName = findViewById(R.id.tvDiseaseName);
        TextView tvConfidence = findViewById(R.id.tvConfidence);
        TextView tvScanTime = findViewById(R.id.tvScanTime);
        ProgressBar confidenceBar = findViewById(R.id.confidenceBar);
        TextView tvUncertainNotice = findViewById(R.id.tvUncertainNotice);
        View sectionsContainer = findViewById(R.id.sectionsContainer);
        View scoresCard = findViewById(R.id.scoresCard);
        TextView tvAbout = findViewById(R.id.tvAbout);
        TextView tvSymptoms = findViewById(R.id.tvSymptoms);
        TextView tvWhatToDo = findViewById(R.id.tvWhatToDo);
        TextView tvPrevention = findViewById(R.id.tvPrevention);
        TextView tvAllScores = findViewById(R.id.tvAllScores);
        Button btnDone = findViewById(R.id.btnDone);

        Intent intent = getIntent();
        String rawLabel = intent.getStringExtra(EXTRA_DISEASE);
        float confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f);
        String imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI);
        String imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH);
        float[] allScores = intent.getFloatArrayExtra(EXTRA_ALL_SCORES);
        String[] allLabels = intent.getStringArrayExtra(EXTRA_ALL_LABELS);
        boolean fromHistory = intent.getBooleanExtra(EXTRA_FROM_HISTORY, false);

        if (savedInstanceState != null) {
            scanTimestamp = savedInstanceState.getLong(STATE_TIMESTAMP, System.currentTimeMillis());
        } else if (fromHistory) {
            scanTimestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        } else {
            scanTimestamp = System.currentTimeMillis();
        }

        loadImage(ivResultImage, imageUriString, imagePath);

        DiseaseInfo info = DiseaseInfo.forLabel(rawLabel);
        boolean uncertain = confidence < CONFIDENCE_THRESHOLD || info == null;

        tvConfidence.setText(String.format(Locale.US, "Confidence: %.1f%%", confidence * 100f));
        confidenceBar.setProgress(Math.max(0, Math.min(100, Math.round(confidence * 100f))));
        tvScanTime.setText(TimeUtils.formatDateTime(scanTimestamp));

        if (uncertain) {
            tvDiseaseName.setText(R.string.uncertain_result);
            tvDiseaseName.setTextColor(COLOR_UNCERTAIN);
            tvUncertainNotice.setVisibility(View.VISIBLE);
            tvUncertainNotice.setText(R.string.uncertain_body);
            sectionsContainer.setVisibility(View.GONE);
        } else {
            tvDiseaseName.setText(info.displayName);
            tvDiseaseName.setTextColor(info.healthy ? COLOR_HEALTHY : COLOR_DISEASE);
            tvUncertainNotice.setVisibility(View.GONE);
            sectionsContainer.setVisibility(View.VISIBLE);
            tvAbout.setText(info.about);
            tvSymptoms.setText(info.symptoms);
            tvWhatToDo.setText(info.whatToDo);
            tvPrevention.setText(info.prevention);
        }

        // History records store only the winning class, not the distribution.
        if (allScores != null && allLabels != null && allScores.length == allLabels.length) {
            scoresCard.setVisibility(View.VISIBLE);
            tvAllScores.setText(buildScoreBreakdown(allLabels, allScores));
        } else {
            scoresCard.setVisibility(View.GONE);
        }

        btnDone.setOnClickListener(v -> finish());

        // Save exactly once per live scan: not on rotation, never in history mode.
        if (!fromHistory && savedInstanceState == null) {
            saveToHistory(rawLabel, confidence, imageUriString);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_TIMESTAMP, scanTimestamp);
    }

    // ------------------------------------------------------------ persistence

    private void saveToHistory(String rawLabel, float confidence, String imageUriString) {
        if (rawLabel == null || rawLabel.trim().isEmpty()) {
            return; // Never store an empty prediction.
        }

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            // No valid user: send them back to log in rather than saving anonymously.
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        final int userId = session.getUserId();
        final long timestamp = scanTimestamp;
        final Uri sourceUri = imageUriString != null ? Uri.parse(imageUriString) : null;
        final String diseaseName = DiseaseInfo.displayNameFor(rawLabel);
        final String label = rawLabel;

        AppExecutors.diskIO().execute(() -> {
            String storedPath = null;
            try {
                if (sourceUri != null) {
                    storedPath = ImageUtils.saveScanImage(getApplicationContext(), sourceUri);
                }
            } catch (IOException | OutOfMemoryError | RuntimeException e) {
                storedPath = null; // The record is still worth keeping without its image.
            }

            boolean saved;
            try {
                ScanHistory record = new ScanHistory();
                record.userId = userId;
                record.diseaseKey = label;
                record.diseaseName = diseaseName;
                record.confidence = confidence;
                record.imagePath = storedPath;
                record.timestamp = timestamp;

                AppDatabase.getInstance(getApplicationContext()).scanHistoryDao().insertScan(record);
                saved = true;
            } catch (RuntimeException e) {
                saved = false;
                ImageUtils.deleteScanImage(storedPath); // Do not leave an orphan file behind.
            }

            if (!saved) {
                AppExecutors.mainThread().post(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(ResultActivity.this,
                                R.string.history_save_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // ----------------------------------------------------------------- render

    private void loadImage(ImageView target, String imageUriString, String imagePath) {
        AppExecutors.diskIO().execute(() -> {
            Bitmap bitmap = null;
            if (imagePath != null) {
                bitmap = ImageUtils.decodeStoredScanImage(imagePath, 720);
            } else if (imageUriString != null) {
                try {
                    bitmap = ImageUtils.decodeBitmap(
                            getContentResolver(), Uri.parse(imageUriString), 720);
                } catch (IOException | OutOfMemoryError | RuntimeException e) {
                    bitmap = null;
                }
            }
            final Bitmap finalBitmap = bitmap;
            AppExecutors.mainThread().post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalBitmap != null) {
                    target.setImageBitmap(finalBitmap);
                }
                // If it is null the placeholder background stays visible: an old
                // image that was deleted must not break the rest of the screen.
            });
        });
    }

    /** Full distribution, so the screening result stays transparent. */
    private String buildScoreBreakdown(String[] labels, float[] scores) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(DiseaseInfo.displayNameFor(labels[i]))
                    .append(": ")
                    .append(String.format(Locale.US, "%.1f%%", scores[i] * 100f));
        }
        return builder.toString();
    }
}
