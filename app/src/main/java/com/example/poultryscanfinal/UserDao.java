package com.example.poultryscanfinal;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface UserDao {

    @Insert
    long insert(User user);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findByEmail(String email);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findById(int id);

    @Query("SELECT COUNT(*) FROM users WHERE email = :email")
    int countByEmail(String email);
}
