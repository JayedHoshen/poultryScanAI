package com.example.poultryscanfinal;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etRegEmail, etPhone, etRegPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvGoToLogin;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etFullName = findViewById(R.id.etFullName);
        etRegEmail = findViewById(R.id.etRegEmail);
        etPhone = findViewById(R.id.etPhone);
        etRegPassword = findViewById(R.id.etRegPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

        btnRegister.setOnClickListener(v -> attemptRegister());

        // Register -> Login
        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        String name = etFullName.getText().toString().trim();
        String email = etRegEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String password = etRegPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Enter your name");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etRegEmail.setError("Enter your email");
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Enter your phone number");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etRegPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        btnRegister.setEnabled(false);

        dbExecutor.execute(() -> {
            UserDao dao = AppDatabase.getInstance(getApplicationContext()).userDao();
            boolean alreadyExists = dao.countByEmail(email) > 0;

            if (alreadyExists) {
                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    etRegEmail.setError("This email is already registered");
                    etRegEmail.requestFocus();
                });
                return;
            }

            User user = new User();
            user.fullName = name;
            user.email = email;
            user.phone = phone;
            user.passwordHash = PasswordUtils.hash(password);
            dao.insert(user);

            runOnUiThread(() -> {
                btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Account created. Please log in.", Toast.LENGTH_SHORT).show();

                // Register success -> back to Login
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            });
        });
    }
}
