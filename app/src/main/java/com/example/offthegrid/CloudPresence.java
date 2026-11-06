package com.example.offthegrid;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public final class CloudPresence {

    private static final String TAG = "CloudPresence";
    private static final String ROOT = "online";

    private static DatabaseReference mine;

    private CloudPresence() {
    }

    public static synchronized void goOnline(Context context) {
        try {
            Context app = context.getApplicationContext();
            String id = Profile.getId(app);
            if (id == null || id.isEmpty()) return;
            DatabaseReference root = FirebaseDatabase.getInstance().getReference(ROOT);
            DatabaseReference ref = root.child(id);
            Map<String, Object> value = new HashMap<>();
            value.put("name", Profile.getName(app));
            value.put("since", ServerValue.TIMESTAMP);
            ref.onDisconnect().removeValue();
            ref.setValue(value);
            mine = ref;
            root.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int count = (int) snapshot.getChildrenCount();
                    Toast.makeText(app, app.getString(R.string.cloud_online_count, count),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Read cancelled: " + error.getMessage());
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not go online", e);
        }
    }

    public static synchronized void goOffline() {
        if (mine == null) return;
        try {
            mine.removeValue();
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not go offline", e);
        }
        mine = null;
    }
}