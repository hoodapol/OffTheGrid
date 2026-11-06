package com.example.offthegrid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.offthegrid.databinding.ActivityDiscoverBinding;
import com.google.android.material.badge.BadgeDrawable;

public class DiscoverActivity extends AppCompatActivity {

    private static final String TAG = "DiscoverActivity";

    private ActivityDiscoverBinding binding;

    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateBadge();
        }
    };

    private final ChatStateStore.Listener stateListener = this::updateBadge;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.notifications_off, Toast.LENGTH_LONG).show();
                }
            });

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Profile.isSetUp(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        Log.d(TAG, "onCreate");
        Log.d(TAG, "Profile name=" + Profile.getName(this) + " id=" + Profile.getId(this));

        binding = ActivityDiscoverBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ChatStateStore.startTracking(this);

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_discover) {
                showTab(new DiscoverFragment());
                return true;
            }
            if (id == R.id.nav_chats) {
                showTab(new ChatsFragment());
                return true;
            }
            if (id == R.id.nav_profile) {
                showTab(new ProfileFragment());
                return true;
            }
            return false;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> {
        });

        if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_discover);
        }
        askForNotificationPermission();
    }

    private void showTab(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void updateBadge() {
        if (binding == null) return;
        int total = ChatStateStore.totalUnread(this);
        BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_chats);
        if (total > 0) {
            badge.setNumber(total);
            badge.setVisible(true);
        } else {
            badge.clearNumber();
            badge.setVisible(false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        if (binding == null) return;
        ListenerService.start(this);
        getContentResolver().registerContentObserver(MessageStorage.CONTENT_URI, true, observer);
        ChatStateStore.addListener(stateListener);
        updateBadge();
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
        if (binding != null) {
            getContentResolver().unregisterContentObserver(observer);
            ChatStateStore.removeListener(stateListener);
        }
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (isFinishing() && !VisibilityPrefs.isStayVisible(this)) {
            stopService(new Intent(this, ListenerService.class));
        }
        super.onDestroy();
    }
}