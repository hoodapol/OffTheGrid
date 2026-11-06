package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.offthegrid.databinding.ActivityOtherProfileBinding;
import com.example.offthegrid.databinding.DialogPostBinding;
import com.example.offthegrid.databinding.ItemOtherHeaderBinding;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OtherProfileActivity extends AppCompatActivity implements FriendStore.Listener {

    private static final class Result {
        RemoteProfile profile;
        boolean cached;
        long synced;
    }

    private ActivityOtherProfileBinding binding;
    private ItemOtherHeaderBinding header;
    private RemotePostAdapter postAdapter;
    private String peer;
    private String device;
    private int state = FriendStore.STATE_NONE;
    private boolean busy;
    private boolean cachedView;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOtherProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        peer = getIntent().getStringExtra(ChatActivity.EXTRA_PEER_NAME);
        if (peer == null) {
            finish();
            return;
        }
        resolve();
        applySecure();

        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? 2 : 1;
            }
        });
        binding.rvOther.setLayoutManager(layoutManager);
        header = ItemOtherHeaderBinding.inflate(getLayoutInflater(), binding.rvOther, false);
        postAdapter = new RemotePostAdapter(this::showPost);
        binding.rvOther.setAdapter(
                new ConcatAdapter(new SingleViewAdapter(header.getRoot()), postAdapter));

        binding.btnOtherBack.setOnClickListener(v -> finish());
        binding.btnRetry.setOnClickListener(v -> load());
        binding.btnOtherRefresh.setOnClickListener(v -> load());
        header.btnFriend.setOnClickListener(v -> onFriendClicked());
        header.btnDecline.setOnClickListener(v -> decline());
        header.btnMessage.setOnClickListener(v -> openChat());
        refreshFriendButton();

        FriendStore.addListener(this);
        load();
    }

    private void openChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, peer);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void resolve() {
        PeerRegistry.Address address = PeerRegistry.get(peer);
        FriendStore.Friend row = address != null
                ? FriendStore.get(this, address.deviceId)
                : FriendStore.findByName(this, peer);
        device = address != null ? address.deviceId : (row != null ? row.device : null);
        state = row == null ? FriendStore.STATE_NONE : row.state;
    }

    private void applySecure() {
        if (state == FriendStore.STATE_FRIEND) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void refreshFriendButton() {
        header.btnDecline.setVisibility(View.GONE);
        header.tvOtherNote.setVisibility(
                state == FriendStore.STATE_FRIEND ? View.GONE : View.VISIBLE);
        if (device == null) {
            header.btnFriend.setVisibility(View.GONE);
            return;
        }
        header.btnFriend.setVisibility(View.VISIBLE);
        header.btnFriend.setAlpha(1f);
        if (state == FriendStore.STATE_FRIEND) {
            header.btnFriend.setText(R.string.friend_friends);
        } else if (state == FriendStore.STATE_PENDING_OUT) {
            header.btnFriend.setText(R.string.friend_request_sent);
            header.btnFriend.setAlpha(0.6f);
        } else if (state == FriendStore.STATE_PENDING_IN) {
            header.btnFriend.setText(R.string.friend_accept);
            header.btnDecline.setVisibility(View.VISIBLE);
        } else {
            header.btnFriend.setText(R.string.friend_add);
        }
    }

    @Override
    public void onFriendsChanged() {
        if (isFinishing() || isDestroyed() || peer == null) return;
        boolean wasFriend = state == FriendStore.STATE_FRIEND;
        resolve();
        applySecure();
        refreshFriendButton();
        if ((state == FriendStore.STATE_FRIEND) != wasFriend) {
            load();
        }
    }

    private void onFriendClicked() {
        if (busy || device == null) return;
        if (state == FriendStore.STATE_FRIEND) {
            confirmUnfriend();
        } else if (state == FriendStore.STATE_PENDING_IN) {
            accept();
        } else if (state == FriendStore.STATE_NONE) {
            sendRequest();
        }
    }

    private void sendRequest() {
        if (!PeerRegistry.contains(peer)) {
            toast(R.string.friend_need_nearby);
            return;
        }
        busy = true;
        Context app = getApplicationContext();
        String key = peer;
        String dev = device;
        FriendStore.upsert(app, dev, key, FriendStore.STATE_PENDING_OUT);
        worker.execute(() -> {
            boolean ok = PeerClient.sendFriend(app, key, FriendActions.REQUEST);
            runOnUiThread(() -> {
                busy = false;
                if (!ok) {
                    FriendStore.remove(app, dev);
                    toast(R.string.friend_request_failed);
                }
            });
        });
    }

    private void accept() {
        if (!PeerRegistry.contains(peer)) {
            toast(R.string.friend_need_nearby);
            return;
        }
        busy = true;
        Context app = getApplicationContext();
        String key = peer;
        String dev = device;
        worker.execute(() -> {
            boolean ok = PeerClient.sendFriend(app, key, FriendActions.ACCEPT);
            runOnUiThread(() -> {
                busy = false;
                if (ok) {
                    FriendStore.upsert(app, dev, key, FriendStore.STATE_FRIEND);
                } else {
                    toast(R.string.friend_request_failed);
                }
            });
        });
    }

    private void decline() {
        if (busy || device == null) return;
        Context app = getApplicationContext();
        String key = peer;
        String dev = device;
        worker.execute(() -> PeerClient.sendFriend(app, key, FriendActions.DECLINE));
        FriendStore.remove(app, dev);
    }

    private void confirmUnfriend() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.friend_unfriend_title, peer))
                .setMessage(R.string.friend_unfriend_body)
                .setPositiveButton(R.string.friend_unfriend, (dialog, which) -> {
                    Context app = getApplicationContext();
                    String key = peer;
                    String dev = device;
                    FriendStore.remove(app, dev);
                    worker.execute(() -> PeerClient.sendFriend(app, key, FriendActions.REMOVE));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toast(int message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void load() {
        binding.rvOther.setVisibility(View.INVISIBLE);
        binding.statusBox.setVisibility(View.VISIBLE);
        binding.progress.setVisibility(View.VISIBLE);
        binding.btnRetry.setVisibility(View.GONE);
        binding.tvStatus.setText(getString(R.string.other_loading, peer));
        Context app = getApplicationContext();
        String key = peer;
        String dev = device;
        boolean friend = state == FriendStore.STATE_FRIEND;
        worker.execute(() -> {
            Result result = new Result();
            result.profile = PeerClient.fetchProfile(app, key);
            if (result.profile != null) {
                if (friend && dev != null) {
                    FriendStore.saveCache(app, dev, result.profile);
                }
            } else if (friend && dev != null) {
                FriendStore.Friend row = FriendStore.get(app, dev);
                if (row != null && row.synced > 0) {
                    result.profile = FriendStore.loadCache(app, row);
                    result.cached = true;
                    result.synced = row.synced;
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result.profile == null) {
                    showError();
                } else {
                    showProfile(result);
                }
            });
        });
    }

    private void showError() {
        binding.progress.setVisibility(View.GONE);
        binding.btnRetry.setVisibility(View.VISIBLE);
        binding.tvStatus.setText(getString(R.string.other_unreachable, peer));
    }

    private void showProfile(Result result) {
        RemoteProfile profile = result.profile;
        cachedView = result.cached;
        header.tvOtherName.setText(profile.name);
        header.tvOtherBio.setText(profile.bio);
        header.tvOtherBio.setVisibility(profile.bio.isEmpty() ? View.GONE : View.VISIBLE);
        if (profile.avatar != null) {
            header.ivOtherAvatar.setImageBitmap(profile.avatar);
            header.ivOtherAvatar.setVisibility(View.VISIBLE);
            header.tvOtherAvatar.setVisibility(View.GONE);
        } else {
            header.ivOtherAvatar.setVisibility(View.GONE);
            header.tvOtherAvatar.setVisibility(View.VISIBLE);
            header.tvOtherAvatar.setText(profile.name.isEmpty()
                    ? "" : profile.name.substring(0, 1).toUpperCase(Locale.getDefault()));
        }
        if (profile.cover != null) {
            header.ivOtherCover.setImageBitmap(profile.cover);
            header.ivOtherCover.setVisibility(View.VISIBLE);
        } else {
            header.ivOtherCover.setVisibility(View.GONE);
        }
        if (result.cached) {
            String when = DateFormat.getMediumDateFormat(this).format(new Date(result.synced))
                    + " " + DateFormat.getTimeFormat(this).format(new Date(result.synced));
            header.tvOtherOffline.setText(getString(R.string.friend_offline_banner, when));
            header.tvOtherOffline.setVisibility(View.VISIBLE);
        } else {
            header.tvOtherOffline.setVisibility(View.GONE);
        }
        postAdapter.setPosts(profile.posts);
        header.tvOtherEmpty.setVisibility(profile.posts.isEmpty() ? View.VISIBLE : View.GONE);
        binding.statusBox.setVisibility(View.GONE);
        binding.rvOther.setVisibility(View.VISIBLE);
    }

    private boolean canHeart(RemoteProfile.RemotePost post) {
        return state == FriendStore.STATE_FRIEND && !cachedView
                && post.id != null && !post.id.isEmpty();
    }

    private void bindHeart(DialogPostBinding card, RemoteProfile.RemotePost post) {
        if (!post.liked) {
            card.btnDialogDelete.setText(R.string.heart_off);
        } else if (post.hearts > 1) {
            card.btnDialogDelete.setText(getString(R.string.heart_on, post.hearts));
        } else {
            card.btnDialogDelete.setText("❤️");
        }
    }

    private void toggleHeart(DialogPostBinding card, RemoteProfile.RemotePost post) {
        if (!PeerRegistry.contains(peer)) {
            toast(R.string.friend_need_nearby);
            return;
        }
        boolean on = !post.liked;
        post.liked = on;
        post.hearts = Math.max(0, post.hearts + (on ? 1 : -1));
        bindHeart(card, post);
        Context app = getApplicationContext();
        String key = peer;
        worker.execute(() -> {
            boolean ok = PeerClient.sendReaction(app, key, post.id,
                    on ? MessageMetaStore.HEART : "", "post");
            if (!ok) {
                runOnUiThread(() -> {
                    post.liked = !on;
                    post.hearts = Math.max(0, post.hearts + (on ? -1 : 1));
                    bindHeart(card, post);
                    toast(R.string.heart_failed);
                });
            }
        });
    }

    private void showPost(RemoteProfile.RemotePost post) {
        Date date = new Date(post.timestamp);
        String when = DateFormat.getMediumDateFormat(this).format(date) + "  "
                + DateFormat.getTimeFormat(this).format(date);

        DialogPostBinding card = DialogPostBinding.inflate(LayoutInflater.from(this));
        card.btnDialogEdit.setVisibility(View.GONE);
        if (post.image != null) {
            card.ivDialogImage.setImageBitmap(post.image);
            card.ivDialogImage.setVisibility(View.VISIBLE);
            card.tvDialogText.setText(post.text);
            card.tvDialogText.setVisibility(post.text.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            card.tvDialogNote.setText(post.text);
            card.tvDialogNote.setVisibility(View.VISIBLE);
        }
        card.tvDialogDate.setText(when);
        if (canHeart(post)) {
            card.btnDialogDelete.setVisibility(View.VISIBLE);
            bindHeart(card, post);
            card.btnDialogDelete.setOnClickListener(v -> toggleHeart(card, post));
        } else {
            card.btnDialogDelete.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(card.getRoot())
                .create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (state != FriendStore.STATE_FRIEND) {
            dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
        card.btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FriendStore.removeListener(this);
        worker.shutdownNow();
    }
}