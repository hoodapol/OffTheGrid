package com.example.offthegrid;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.offthegrid.databinding.ActivityAuthBinding;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

public class AuthActivity extends AppCompatActivity {

    private static final int WEAK = 1;
    private static final int OKAY = 2;
    private static final int STRONG = 3;

    private ActivityAuthBinding binding;
    private boolean signUp;
    private boolean busy;
    private boolean passwordVisible;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnAuthBack.setOnClickListener(v -> finish());
        binding.btnAuthSubmit.setOnClickListener(v -> submit());
        binding.tvAuthForgot.setOnClickListener(v -> resetPassword());
        binding.btnTogglePassword.setOnClickListener(v -> togglePassword());
        binding.tvAuthSwitch.setOnClickListener(v -> {
            signUp = !signUp;
            binding.tvAuthError.setVisibility(View.GONE);
            render();
        });
        binding.etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateStrength();
            }
        });
        render();
    }

    private void togglePassword() {
        passwordVisible = !passwordVisible;
        int selection = binding.etPassword.getSelectionEnd();
        int variation = passwordVisible
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD;
        binding.etPassword.setInputType(InputType.TYPE_CLASS_TEXT | variation);
        binding.etPassword.setTypeface(Typeface.DEFAULT);
        if (selection >= 0) {
            binding.etPassword.setSelection(selection);
        }
        binding.btnTogglePassword.setImageResource(
                passwordVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        binding.btnTogglePassword.setContentDescription(getString(
                passwordVisible ? R.string.auth_hide_password : R.string.auth_show_password));
    }

    private void render() {
        binding.tvAuthTitle.setText(signUp ? R.string.auth_title_signup : R.string.auth_title_login);
        binding.btnAuthSubmit.setText(signUp ? R.string.auth_signup : R.string.auth_login);
        binding.tvAuthSwitch.setText(signUp
                ? R.string.auth_switch_to_login : R.string.auth_switch_to_signup);
        binding.tvAuthForgot.setVisibility(signUp ? View.GONE : View.VISIBLE);
        binding.tvAuthRules.setVisibility(signUp ? View.VISIBLE : View.GONE);
        updateStrength();
    }

    private static boolean meetsRules(String password) {
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        return password.length() >= 8 && letter && digit;
    }

    private static int strength(String password) {
        if (!meetsRules(password)) return WEAK;
        boolean lower = false;
        boolean upper = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isUpperCase(c)) {
                upper = true;
            } else if (!Character.isLetterOrDigit(c)) {
                symbol = true;
            }
        }
        int extras = 0;
        if (password.length() >= 12) extras++;
        if (lower && upper) extras++;
        if (symbol) extras++;
        return extras >= 2 ? STRONG : OKAY;
    }

    private void updateStrength() {
        String password = binding.etPassword.getText().toString();
        if (!signUp || password.isEmpty()) {
            binding.layoutStrength.setVisibility(View.GONE);
            return;
        }
        int level = strength(password);
        int label;
        int color;
        if (level == WEAK) {
            label = R.string.auth_strength_weak;
            color = Color.parseColor("#C0392B");
        } else if (level == OKAY) {
            label = R.string.auth_strength_okay;
            color = Color.parseColor("#E0A100");
        } else {
            label = R.string.auth_strength_strong;
            color = Color.parseColor("#2E9E5B");
        }
        binding.layoutStrength.setVisibility(View.VISIBLE);
        binding.barStrength.setProgress(level);
        binding.barStrength.setProgressTintList(ColorStateList.valueOf(color));
        binding.tvStrength.setText(label);
        binding.tvStrength.setTextColor(color);
    }

    private void showError(int message) {
        binding.tvAuthError.setText(message);
        binding.tvAuthError.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.progressAuth.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.btnAuthSubmit.setEnabled(!value);
        binding.btnAuthSubmit.setAlpha(value ? 0.5f : 1f);
    }

    private void submit() {
        if (busy) return;
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            showError(R.string.auth_error_empty);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(R.string.auth_error_email);
            return;
        }
        if (signUp && !meetsRules(password)) {
            showError(R.string.auth_error_weak);
            return;
        }
        binding.tvAuthError.setVisibility(View.GONE);
        setBusy(true);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Task<AuthResult> task = signUp
                ? auth.createUserWithEmailAndPassword(email, password)
                : auth.signInWithEmailAndPassword(email, password);
        task.addOnCompleteListener(this, result -> {
            setBusy(false);
            if (result.isSuccessful()) {
                Toast.makeText(this, getString(R.string.auth_signed_in, email),
                        Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
                return;
            }
            Exception error = result.getException();
            if (signUp && error instanceof FirebaseAuthUserCollisionException) {
                signUp = false;
                render();
                showError(R.string.auth_error_exists_login);
                return;
            }
            showError(messageFor(error));
        });
    }

    private int messageFor(@Nullable Exception error) {
        if (error instanceof FirebaseAuthWeakPasswordException) {
            return R.string.auth_error_weak;
        }
        if (error instanceof FirebaseAuthUserCollisionException) {
            return R.string.auth_error_exists;
        }
        if (error instanceof FirebaseAuthInvalidUserException) {
            return R.string.auth_error_invalid;
        }
        if (error instanceof FirebaseAuthInvalidCredentialsException) {
            return signUp ? R.string.auth_error_email : R.string.auth_error_invalid;
        }
        if (error instanceof FirebaseNetworkException) {
            return R.string.auth_error_network;
        }
        return R.string.auth_error_generic;
    }

    private void resetPassword() {
        if (busy) return;
        String email = binding.etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showError(R.string.auth_reset_need_email);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(R.string.auth_error_email);
            return;
        }
        binding.tvAuthError.setVisibility(View.GONE);
        setBusy(true);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(this, result -> {
                    setBusy(false);
                    if (result.isSuccessful()) {
                        Toast.makeText(this, R.string.auth_reset_sent,
                                Toast.LENGTH_LONG).show();
                    } else {
                        showError(messageFor(result.getException()));
                    }
                });
    }
}