package com.example.poultryscanfinal;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One saved screening result. Rows always belong to exactly one user, and every
 * query in {@link ScanHistoryDao} filters on that user id.
 *
 * The image itself is NOT stored here. Only a path to a JPEG copy in the app's
 * internal storage is kept, so the database stays small.
 */
@Entity(tableName = "scan_history", indices = {@Index("user_id")})
public class ScanHistory {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Matches User.id of the logged-in user who ran the scan. */
    @ColumnInfo(name = "user_id")
    public long userId;

    /** Raw model label: cocci, healthy, ncd or salmo. */
    @ColumnInfo(name = "disease_key")
    public String diseaseKey;

    /** Display name at the time of the scan, e.g. "Coccidiosis". */
    @ColumnInfo(name = "disease_name")
    public String diseaseName;

    /** Probability of the predicted class, 0.0 - 1.0. */
    public float confidence;

    /** Absolute path of the stored JPEG, or null if the copy failed. */
    @ColumnInfo(name = "image_path")
    public String imagePath;

    /** System.currentTimeMillis() at the moment the scan was saved. */
    public long timestamp;
}
