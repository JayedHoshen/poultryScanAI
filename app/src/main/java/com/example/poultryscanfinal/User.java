package com.example.poultryscanfinal;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "full_name")
    public String fullName;

    public String email;

    public String phone;

    @ColumnInfo(name = "password_hash")
    public String passwordHash;
}
