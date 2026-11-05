package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.offthegrid.databinding.ActivityCreatePostBinding;

public class CreatePostActivity extends AppCompatActivity {

    private static final int MAX_TEXT = 280;

    private ActivityCreatePostBinding binding;
    private String photoFile;
    private int visibility = PostStorage.VISIBILITY_EVERYONE;
    private boolean posted;

    private final ActivityResultLauncher<String> picker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) prepare(uri);
            });

    private final ActivityResultLauncher<Intent> cropper = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String name = result.getData().getStringExtra(CropActivity.EXTRA_RESULT);
                if (name != null) {
                    discardPhoto();
                    photoFile = name;
                    refreshPhoto();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreatePostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.etPostText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_TEXT)});
        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnPost.setOnClickListener(v -> post());
        binding.btnAddPhoto.setOnClickListener(v -> picker.launch("image/*"));
        binding.btnRemovePhoto.setOnClickListener(v -> {
            discardPhoto();
            refreshPhoto();
        });
        binding.tvVisEveryone.setOnClickListener(v -> chooseVisibility(
                PostStorage.VISIBILITY_EVERYONE));
        binding.tvVisFriends.setOnClickListener(v -> chooseVisibility(
                PostStorage.VISIBILITY_FRIENDS));

        refreshPhoto();
        refreshVisibility();
    }

    private void prepare(Uri uri) {
        Context app = getApplicationContext();
        new Thread(() -> {
            String temp = ImageStore.saveFromUri(app, uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (temp != null) ImageStore.delete(app, temp);
                    return;
                }
                if (temp == null) {
                    Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                cropper.launch(CropActivity.intent(this, temp, 1f, 800, false));
            });
        }).start();
    }

    private void discardPhoto() {
        if (photoFile != null) {
            ImageStore.delete(this, photoFile);
            photoFile = null;
        }
    }

    private void refreshPhoto() {
        Bitmap bitmap = photoFile == null ? null : ImageStore.thumbnail(this, photoFile);
        if (bitmap != null) {
            binding.ivPostPreview.setImageBitmap(bitmap);
            binding.ivPostPreview.setVisibility(View.VISIBLE);
            binding.btnRemovePhoto.setVisibility(View.VISIBLE);
        } else {
            binding.ivPostPreview.setVisibility(View.GONE);
            binding.btnRemovePhoto.setVisibility(View.GONE);
        }
    }

    private void chooseVisibility(int value) {
        visibility = value;
        refreshVisibility();
    }

    private void refreshVisibility() {
        binding.tvVisEveryone.setSelected(visibility == PostStorage.VISIBILITY_EVERYONE);
        binding.tvVisFriends.setSelected(visibility == PostStorage.VISIBILITY_FRIENDS);
    }

    private void post() {
        String text = binding.etPostText.getText().toString().trim();
        if (text.isEmpty() && photoFile == null) {
            Toast.makeText(this, R.string.post_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }
        PostStore.add(this, text, photoFile, visibility);
        posted = true;
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!posted && isFinishing()) {
            discardPhoto();
        }
    }
}