package com.example.poultryscanfinal;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Static reference information for one model class. No inference happens here.
 */
public class DiseaseDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DISEASE_KEY = "extra_disease_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disease_detail);

        String key = getIntent().getStringExtra(EXTRA_DISEASE_KEY);
        DiseaseInfo info = DiseaseInfo.forLabel(key);

        if (info == null) {
            // Unknown or missing key: leave rather than showing a blank screen.
            Toast.makeText(this, R.string.unknown_disease, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvDetailTitle);
        TextView tvName = findViewById(R.id.tvDetailName);
        TextView tvOverview = findViewById(R.id.tvDetailOverview);
        TextView tvSymptoms = findViewById(R.id.tvDetailSymptoms);
        TextView tvSpread = findViewById(R.id.tvDetailSpread);
        TextView tvPrevention = findViewById(R.id.tvDetailPrevention);
        TextView tvManagement = findViewById(R.id.tvDetailManagement);
        TextView tvVet = findViewById(R.id.tvDetailVet);

        tvTitle.setText(info.displayName);
        tvName.setText(info.displayName);
        tvOverview.setText(info.about);
        tvSymptoms.setText(info.symptoms);
        tvSpread.setText(info.spread);
        tvPrevention.setText(info.prevention);
        tvManagement.setText(info.management);
        tvVet.setText(info.whenToCallVet);

        btnBack.setOnClickListener(v -> finish());
    }
}
