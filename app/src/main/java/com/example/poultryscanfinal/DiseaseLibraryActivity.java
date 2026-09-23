package com.example.poultryscanfinal;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists exactly the four classes the TFLite model can output. The content is
 * static local data from DiseaseInfo - the model is never run here.
 */
public class DiseaseLibraryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disease_library);

        TextView btnBack = findViewById(R.id.btnBack);
        RecyclerView recyclerView = findViewById(R.id.recyclerDiseases);

        List<DiseaseInfo> diseases = new ArrayList<>();
        for (String key : DiseaseInfo.LIBRARY_ORDER) {
            DiseaseInfo info = DiseaseInfo.forLabel(key);
            if (info != null) {
                diseases.add(info);
            }
        }

        DiseaseLibraryAdapter adapter = new DiseaseLibraryAdapter(diseases);
        adapter.setOnItemClickListener(info -> {
            Intent intent = new Intent(DiseaseLibraryActivity.this, DiseaseDetailActivity.class);
            intent.putExtra(DiseaseDetailActivity.EXTRA_DISEASE_KEY, info.key);
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
    }
}
