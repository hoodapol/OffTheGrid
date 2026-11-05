package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.offthegrid.databinding.ActivityCropBinding;

public class CropActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_RATIO = "ratio";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_CIRCLE = "circle";
    public static final String EXTRA_RESULT = "result";

    private ActivityCropBinding binding;
    private String source;
    private int outWidth;
    private int outHeight;
    private boolean busy;

    public static Intent intent(Context context, String source, float ratio, int width,
                                boolean circle) {
        Intent intent = new Intent(context, CropActivity.class);
        intent.putExtra(EXTRA_SOURCE, source);
        intent.putExtra(EXTRA_RATIO, ratio);
        intent.putExtra(EXTRA_WIDTH, width);
        intent.putExtra(EXTRA_CIRCLE, circle);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCropBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent intent = getIntent();
        source = intent.getStringExtra(EXTRA_SOURCE);
        float ratio = intent.getFloatExtra(EXTRA_RATIO, 1f);
        outWidth = intent.getIntExtra(EXTRA_WIDTH, 800);
        outHeight = Math.max(1, Math.round(outWidth / ratio));
        boolean circle = intent.getBooleanExtra(EXTRA_CIRCLE, false);

        Bitmap bitmap = source == null ? null : ImageStore.load(this, source);
        if (bitmap == null) {
            Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        binding.cropView.setSource(bitmap, ratio, circle);
        binding.btnCropCancel.setOnClickListener(v -> finish());
        binding.btnCropDone.setOnClickListener(v -> done());
    }

    private void done() {
        if (busy) return;
        Bitmap cropped = binding.cropView.crop(outWidth, outHeight);
        if (cropped == null) {
            Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        Context app = getApplicationContext();
        new Thread(() -> {
            String name = ImageStore.saveBitmap(app, cropped);
            runOnUiThread(() -> {
                if (name == null) {
                    busy = false;
                    Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT, name));
                finish();
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && source != null) {
            ImageStore.delete(this, source);
        }
    }
}