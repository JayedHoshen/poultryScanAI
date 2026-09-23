package com.example.poultryscanfinal;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lets the user capture or pick a poultry image, then runs the on-device TFLite
 * model against it. Model loading and inference both happen on a single
 * background executor; the UI thread only ever renders results.
 */
public class ScanActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_URI = "state_selected_uri";
    private static final String STATE_PENDING_CAMERA_URI = "state_pending_camera_uri";

    private ImageView ivPreview;
    private LinearLayout layoutPlaceholder;
    private Button btnCamera;
    private Button btnGallery;
    private Button btnAnalyze;
    private TextView btnBack;
    private TextView tvStatus;
    private ProgressBar progressAnalyzing;

    private Uri pendingCameraUri;   // where the camera app writes the full photo
    private Uri selectedImageUri;   // what is currently shown in the preview

    /** Touched from both the UI thread and the executor thread. */
    private volatile DiseaseClassifier classifier;
    private volatile boolean modelLoadFinished;
    private boolean analysisRunning;

    /** Model loading and inference, strictly serialised. */
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    /** Preview decoding, kept separate so it never waits behind an inference. */
    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Launcher: take a full-resolution photo into pendingCameraUri
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraUri != null) {
                    showImage(pendingCameraUri);
                } else {
                    Toast.makeText(this, "Capture cancelled", Toast.LENGTH_SHORT).show();
                }
            });

    // Launcher: ask for the camera permission before opening the camera
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to take a photo",
                            Toast.LENGTH_SHORT).show();
                }
            });

    // Launcher: Android Photo Picker, no storage permission needed
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    showImage(uri);
                } else {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        ivPreview = findViewById(R.id.ivPreview);
        layoutPlaceholder = findViewById(R.id.layoutPlaceholder);
        btnCamera = findViewById(R.id.btnCamera);
        btnGallery = findViewById(R.id.btnGallery);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnBack = findViewById(R.id.btnBack);
        tvStatus = findViewById(R.id.tvStatus);
        progressAnalyzing = findViewById(R.id.progressAnalyzing);

        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_SELECTED_URI);
            if (saved != null) {
                selectedImageUri = Uri.parse(saved);
            }
            String pending = savedInstanceState.getString(STATE_PENDING_CAMERA_URI);
            if (pending != null) {
                pendingCameraUri = Uri.parse(pending);
            }
        }

        btnBack.setOnClickListener(v -> finish());
        btnCamera.setOnClickListener(v -> checkCameraPermissionAndLaunch());
        btnGallery.setOnClickListener(v -> galleryLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        btnAnalyze.setOnClickListener(v -> runAnalysis());

        btnAnalyze.setEnabled(false);
        setStatus("Preparing the AI model\u2026");
        loadModelInBackground();

        if (selectedImageUri != null) {
            showImage(selectedImageUri);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedImageUri != null) {
            outState.putString(STATE_SELECTED_URI, selectedImageUri.toString());
        }
        if (pendingCameraUri != null) {
            outState.putString(STATE_PENDING_CAMERA_URI, pendingCameraUri.toString());
        }
    }

    // ---------------------------------------------------------- model loading

    private void loadModelInBackground() {
        backgroundExecutor.execute(() -> {
            DiseaseClassifier loaded = null;
            String error = null;
            try {
                loaded = new DiseaseClassifier(getApplicationContext());
            } catch (DiseaseClassifier.ClassifierException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "The AI model could not be loaded.";
            }

            final DiseaseClassifier result = loaded;
            final String errorMessage = error;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (result != null) {
                        result.close();
                    }
                    return;
                }
                classifier = result;
                modelLoadFinished = true;
                if (result == null) {
                    setStatus("Model unavailable: " + errorMessage);
                    btnAnalyze.setEnabled(false);
                    Toast.makeText(ScanActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                } else {
                    setStatus(getString(R.string.offline_notice));
                    btnAnalyze.setEnabled(selectedImageUri != null && !analysisRunning);
                }
            });
        });
    }

    // ---------------------------------------------------------- image picking

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            pendingCameraUri = createImageUri();
            cameraLauncher.launch(pendingCameraUri);
        } catch (IOException | IllegalArgumentException e) {
            Toast.makeText(this, "Could not open the camera. Please try the gallery instead.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Creates a temp file in the app cache and returns a content:// Uri via FileProvider. */
    private Uri createImageUri() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File storageDir = new File(getCacheDir(), "images");
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            throw new IOException("Could not create the image folder.");
        }
        File imageFile = File.createTempFile("SCAN_" + timeStamp, ".jpg", storageDir);
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", imageFile);
    }

    private void showImage(Uri uri) {
        selectedImageUri = uri;
        layoutPlaceholder.setVisibility(View.GONE);
        ivPreview.setVisibility(View.VISIBLE);
        btnAnalyze.setEnabled(classifier != null && !analysisRunning);

        // Decode off the UI thread: camera photos are far too large to decode inline.
        previewExecutor.execute(() -> {
            Bitmap preview = null;
            try {
                preview = ImageUtils.decodeBitmap(getContentResolver(), uri, 720);
            } catch (IOException | OutOfMemoryError | RuntimeException e) {
                preview = null;
            }
            final Bitmap finalPreview = preview;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalPreview != null) {
                    ivPreview.setImageBitmap(finalPreview);
                } else {
                    ivPreview.setVisibility(View.GONE);
                    layoutPlaceholder.setVisibility(View.VISIBLE);
                    selectedImageUri = null;
                    btnAnalyze.setEnabled(false);
                    Toast.makeText(ScanActivity.this,
                            "That image could not be opened. Please choose another one.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // -------------------------------------------------------------- inference

    private void runAnalysis() {
        if (analysisRunning) {
            return; // Guards against a double tap queuing two inferences.
        }
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (classifier == null) {
            Toast.makeText(this,
                    modelLoadFinished
                            ? "The AI model is not available, so this image cannot be analysed."
                            : "The AI model is still loading. Please wait a moment.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final Uri uriToAnalyze = selectedImageUri;
        setAnalyzing(true);
        setStatus("Running the AI model\u2026");

        backgroundExecutor.execute(() -> {
            Bitmap bitmap = null;
            DiseaseClassifier.Result result = null;
            String error = null;
            try {
                bitmap = ImageUtils.decodeBitmap(getContentResolver(), uriToAnalyze,
                        ImageUtils.DEFAULT_MAX_DIMENSION);
                DiseaseClassifier active = classifier;
                if (active == null) {
                    error = "The AI model is no longer available.";
                } else {
                    result = active.classify(bitmap);
                }
            } catch (DiseaseClassifier.ClassifierException e) {
                error = e.getMessage();
            } catch (IOException e) {
                error = "This image could not be read. Please choose another photo.";
            } catch (OutOfMemoryError e) {
                error = "Not enough memory to analyse this image.";
            } catch (RuntimeException e) {
                error = "Something went wrong while analysing this image.";
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }

            final DiseaseClassifier.Result finalResult = result;
            final String finalError = error;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setAnalyzing(false);
                if (finalResult != null) {
                    setStatus(getString(R.string.offline_notice));
                    openResult(finalResult, uriToAnalyze);
                } else {
                    setStatus(getString(R.string.offline_notice));
                    Toast.makeText(ScanActivity.this,
                            finalError != null ? finalError : "Analysis failed. Please try again.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void openResult(DiseaseClassifier.Result result, Uri imageUri) {
        Intent intent = new Intent(ScanActivity.this, ResultActivity.class);
        intent.putExtra(ResultActivity.EXTRA_DISEASE, result.label);
        intent.putExtra(ResultActivity.EXTRA_CONFIDENCE, result.confidence);
        intent.putExtra(ResultActivity.EXTRA_IMAGE_URI, imageUri.toString());
        intent.putExtra(ResultActivity.EXTRA_ALL_SCORES, result.allScores);
        intent.putExtra(ResultActivity.EXTRA_ALL_LABELS,
                result.labels.toArray(new String[0]));
        startActivity(intent);
    }

    private void setAnalyzing(boolean analyzing) {
        analysisRunning = analyzing;
        btnAnalyze.setEnabled(!analyzing && classifier != null && selectedImageUri != null);
        btnAnalyze.setText(analyzing ? getString(R.string.analyzing) : getString(R.string.analyze_image));
        btnCamera.setEnabled(!analyzing);
        btnGallery.setEnabled(!analyzing);
        progressAnalyzing.setVisibility(analyzing ? View.VISIBLE : View.GONE);
    }

    private void setStatus(String text) {
        tvStatus.setText(text);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Queued after any in-flight inference, because the executor is single-threaded.
        final DiseaseClassifier toClose = classifier;
        classifier = null;
        backgroundExecutor.execute(() -> {
            if (toClose != null) {
                toClose.close();
            }
        });
        backgroundExecutor.shutdown();
        previewExecutor.shutdown();
    }
}
