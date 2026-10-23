package com.example.offthegrid;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.offthegrid.databinding.ActivitySetupBinding;

public class SetupActivity extends AppCompatActivity {

    public static final String EXTRA_EDIT = "edit";

    private static final String TAG = "SetupActivity";

    private ActivitySetupBinding binding;
    private boolean editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        editing = getIntent().getBooleanExtra(EXTRA_EDIT, false);
        if (editing) {
            String current = Profile.getName(this);
            binding.etName.setText(current);
            binding.etName.setSelection(current.length());
            binding.btnGetStarted.setText(R.string.save);
        }
        binding.btnGetStarted.setOnClickListener(v -> saveAndContinue());
    }

    private void saveAndContinue() {
        String name = binding.etName.getText().toString().trim();
        if (name.isEmpty()) {
            binding.etName.setError(getString(R.string.name_required));
            return;
        }
        Profile.save(this, name);
        if (!editing) {
            startActivity(new Intent(this, DiscoverActivity.class));
        }
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
}