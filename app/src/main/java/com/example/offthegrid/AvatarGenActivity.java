package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.offthegrid.databinding.ActivityAvatarGenBinding;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AvatarGenActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT = "avatar_result";

    private static final String[] STYLES = {
            "adventurer", "avataaars", "bottts", "lorelei", "pixel-art", "planets",
            "adventurer-neutral"
    };
    private static final String[] COLORS = {
            "b6e3f4", "c0aede", "d1d4f9", "ffd5dc", "ffdfbf", "c8f7c5"
    };
    private static final int COUNT = 12;
    private static final int COLUMNS = 3;

    private ActivityAvatarGenBinding binding;
    private AvatarTileAdapter adapter;
    private final TextView[] chips = new TextView[STYLES.length];
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private ExecutorService pool;
    private int styleIndex;
    private int generation;
    private int pending;
    private int failures;
    private boolean saving;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAvatarGenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        pool = Executors.newFixedThreadPool(3);
        adapter = new AvatarTileAdapter(COUNT, this::choose);
        binding.rvAvatars.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        binding.rvAvatars.setAdapter(adapter);

        binding.btnAvatarBack.setOnClickListener(v -> finish());
        binding.btnShuffle.setOnClickListener(v -> load());
        buildChips();
        styleChips();
        load();
    }

    private void buildChips() {
        for (int i = 0; i < STYLES.length; i++) {
            int index = i;
            TextView chip = new TextView(this);
            chip.setText(label(STYLES[i]));
            chip.setTextSize(13);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setClickable(true);
            chip.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
            params.setMarginEnd(dp(8));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                styleIndex = index;
                styleChips();
                load();
            });
            chips[i] = chip;
            binding.chipRow.addView(chip);
        }
    }

    private void styleChips() {
        for (int i = 0; i < chips.length; i++) {
            boolean selected = i == styleIndex;
            chips[i].setBackgroundResource(
                    selected ? R.drawable.bg_primary_button : R.drawable.bg_input);
            chips[i].setTextColor(getColor(
                    selected ? R.color.otg_on_primary : R.color.otg_text_primary));
            chips[i].setPadding(dp(16), 0, dp(16), 0);
        }
    }

    private void load() {
        generation++;
        int current = generation;
        pending = COUNT;
        failures = 0;
        adapter.clear();
        binding.tvAvatarStatus.setVisibility(View.GONE);
        binding.rvAvatars.setVisibility(View.VISIBLE);
        String style = STYLES[styleIndex];
        for (int i = 0; i < COUNT; i++) {
            int position = i;
            String seed = Long.toString(random.nextLong() & Long.MAX_VALUE, 36);
            String color = COLORS[i % COLORS.length];
            pool.execute(() -> {
                Bitmap bitmap = AvatarApi.fetch(style, seed, color);
                main.post(() -> onTile(current, position, bitmap));
            });
        }
    }

    private void onTile(int current, int position, Bitmap bitmap) {
        if (current != generation || isFinishing() || isDestroyed()) return;
        pending--;
        if (bitmap == null) {
            failures++;
        } else {
            adapter.setBitmap(position, bitmap);
        }
        if (pending == 0 && failures == COUNT) {
            binding.rvAvatars.setVisibility(View.GONE);
            binding.tvAvatarStatus.setVisibility(View.VISIBLE);
        }
    }

    private void choose(Bitmap bitmap) {
        if (saving) return;
        saving = true;
        Context app = getApplicationContext();
        new Thread(() -> {
            String name = ImageStore.saveBitmap(app, bitmap);
            main.post(() -> {
                saving = false;
                if (name == null) {
                    Toast.makeText(app, R.string.avatar_gen_save_failed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isFinishing() || isDestroyed()) {
                    ImageStore.delete(app, name);
                    return;
                }
                Intent result = new Intent();
                result.putExtra(EXTRA_RESULT, name);
                setResult(RESULT_OK, result);
                finish();
            });
        }).start();
    }

    private String label(String style) {
        String text = style.replace('-', ' ');
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        generation++;
        pool.shutdownNow();
    }
}