package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.offthegrid.databinding.ActivityEditProfileBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditProfileActivity extends AppCompatActivity {

    private static final float COVER_RATIO = 2.4f;

    private ActivityEditProfileBinding binding;
    private String originalAvatar;
    private String originalCover;
    private String avatarFile;
    private String coverFile;
    private final List<String> created = new ArrayList<>();
    private boolean forAvatar;
    private boolean saved;

    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) prepare(uri, true);
            });

    private final ActivityResultLauncher<String> coverPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) prepare(uri, false);
            });

    private final ActivityResultLauncher<Intent> cropper = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String name = result.getData().getStringExtra(CropActivity.EXTRA_RESULT);
                if (name != null) applyPhoto(name);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        originalAvatar = Profile.getAvatar(this);
        originalCover = Profile.getCover(this);
        avatarFile = originalAvatar;
        coverFile = originalCover;
        binding.etName.setText(Profile.getName(this));
        binding.etBio.setText(Profile.getBio(this));

        binding.btnBack.setOnClickListener(v -> finish());
        binding.editAvatarArea.setOnClickListener(v -> avatarPicker.launch("image/*"));
        binding.editCoverArea.setOnClickListener(v -> coverPicker.launch("image/*"));
        binding.btnRemoveAvatar.setOnClickListener(v -> {
            discardPending(avatarFile);
            avatarFile = null;
            refreshAvatar();
        });
        binding.btnRemoveCover.setOnClickListener(v -> {
            discardPending(coverFile);
            coverFile = null;
            refreshCover();
        });
        binding.btnSave.setOnClickListener(v -> save());

        refreshAvatar();
        refreshCover();
    }

    private void prepare(Uri uri, boolean avatar) {
        forAvatar = avatar;
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
                Intent intent = avatar
                        ? CropActivity.intent(this, temp, 1f, 512, true)
                        : CropActivity.intent(this, temp, COVER_RATIO, 1024, false);
                cropper.launch(intent);
            });
        }).start();
    }

    private void applyPhoto(String name) {
        created.add(name);
        if (forAvatar) {
            discardPending(avatarFile);
            avatarFile = name;
            refreshAvatar();
        } else {
            discardPending(coverFile);
            coverFile = name;
            refreshCover();
        }
    }

    private void discardPending(String file) {
        if (file != null && created.contains(file)) {
            created.remove(file);
            ImageStore.delete(this, file);
        }
    }

    private void refreshAvatar() {
        Bitmap bitmap = avatarFile == null ? null : ImageStore.thumbnail(this, avatarFile);
        if (bitmap != null) {
            binding.ivEditAvatar.setImageBitmap(bitmap);
            binding.ivEditAvatar.setVisibility(View.VISIBLE);
            binding.tvEditAvatarInitial.setVisibility(View.GONE);
            binding.btnRemoveAvatar.setVisibility(View.VISIBLE);
        } else {
            String name = binding.etName.getText().toString().trim();
            binding.ivEditAvatar.setVisibility(View.GONE);
            binding.tvEditAvatarInitial.setVisibility(View.VISIBLE);
            binding.tvEditAvatarInitial.setText(
                    name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.getDefault()));
            binding.btnRemoveAvatar.setVisibility(View.GONE);
        }
    }

    private void refreshCover() {
        Bitmap bitmap = coverFile == null ? null : ImageStore.thumbnail(this, coverFile);
        if (bitmap != null) {
            binding.ivEditCover.setImageBitmap(bitmap);
            binding.ivEditCover.setVisibility(View.VISIBLE);
            binding.tvEditCoverHint.setVisibility(View.GONE);
            binding.btnRemoveCover.setVisibility(View.VISIBLE);
        } else {
            binding.ivEditCover.setVisibility(View.GONE);
            binding.tvEditCoverHint.setVisibility(View.VISIBLE);
            binding.btnRemoveCover.setVisibility(View.GONE);
        }
    }

    private void save() {
        String name = binding.etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String bio = binding.etBio.getText().toString().trim();
        Profile.saveDetails(this, name, bio, avatarFile, coverFile);
        if (originalAvatar != null && !originalAvatar.equals(avatarFile)) {
            ImageStore.delete(this, originalAvatar);
        }
        if (originalCover != null && !originalCover.equals(coverFile)) {
            ImageStore.delete(this, originalCover);
        }
        for (String file : created) {
            if (!file.equals(avatarFile) && !file.equals(coverFile)) {
                ImageStore.delete(this, file);
            }
        }
        saved = true;
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!saved && isFinishing()) {
            for (String file : created) {
                ImageStore.delete(this, file);
            }
        }
    }
}