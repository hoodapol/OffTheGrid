package com.example.offthegrid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.offthegrid.databinding.DialogEditPostBinding;
import com.example.offthegrid.databinding.DialogPostBinding;
import com.example.offthegrid.databinding.DialogProgressBinding;
import com.example.offthegrid.databinding.FragmentProfileBinding;
import com.example.offthegrid.databinding.ItemProfileHeaderBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ItemProfileHeaderBinding header;
    private PostAdapter postAdapter;
    private boolean busy;
    private Runnable pendingAction;
    private AlertDialog progressDialog;
    private DialogProgressBinding progressView;

    private final ActivityResultLauncher<Intent> authLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Runnable action = pendingAction;
                pendingAction = null;
                if (result.getResultCode() == android.app.Activity.RESULT_OK && action != null
                        && isAdded()) {
                    action.run();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? 2 : 1;
            }
        });
        binding.rvProfile.setLayoutManager(layoutManager);

        header = ItemProfileHeaderBinding.inflate(inflater, binding.rvProfile, false);
        header.btnEditProfile.setOnClickListener(v -> openEditor());
        header.btnMore.setOnClickListener(this::showMenu);
        header.btnStatFriends.setOnClickListener(v -> startActivity(
                new Intent(requireContext(), FriendsActivity.class)));
        header.btnNewPost.setOnClickListener(v -> startActivity(
                new Intent(requireContext(), CreatePostActivity.class)));

        postAdapter = new PostAdapter(this::showPost);
        binding.rvProfile.setAdapter(
                new ConcatAdapter(new SingleViewAdapter(header.getRoot()), postAdapter));
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (header == null) return;
        Context context = requireContext();
        String name = Profile.getName(context);
        header.tvProfileName.setText(name);
        String bio = Profile.getBio(context);
        header.tvProfileBio.setText(bio.isEmpty() ? getString(R.string.profile_hint) : bio);
        showAvatar(context, name);
        showCover(context);
        loadPosts(context);
        header.tvStatFriends.setText(String.valueOf(FriendStore.count(context)));
    }

    private void showAvatar(Context context, String name) {
        String file = Profile.getAvatar(context);
        Bitmap bitmap = file == null ? null : ImageStore.thumbnail(context, file);
        if (bitmap != null) {
            header.ivProfileAvatar.setImageBitmap(bitmap);
            header.ivProfileAvatar.setVisibility(View.VISIBLE);
            header.tvProfileAvatar.setVisibility(View.GONE);
        } else {
            header.ivProfileAvatar.setVisibility(View.GONE);
            header.tvProfileAvatar.setVisibility(View.VISIBLE);
            header.tvProfileAvatar.setText(
                    name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.getDefault()));
        }
    }

    private void showCover(Context context) {
        String file = Profile.getCover(context);
        Bitmap bitmap = file == null ? null : ImageStore.thumbnail(context, file);
        if (bitmap != null) {
            header.ivProfileCover.setImageBitmap(bitmap);
            header.ivProfileCover.setVisibility(View.VISIBLE);
        } else {
            header.ivProfileCover.setVisibility(View.GONE);
        }
    }

    private void loadPosts(Context context) {
        List<Post> posts = PostStore.loadMine(context);
        postAdapter.setPosts(posts);
        header.tvStatPosts.setText(String.valueOf(posts.size()));
        header.tvPostsEmpty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String heartNames(Context context, String uid) {
        List<String> names = new ArrayList<>();
        for (String device : MessageMetaStore.heartedBy(context, uid)) {
            FriendStore.Friend friend = FriendStore.get(context, device);
            names.add(friend != null ? friend.name : getString(R.string.heart_someone));
        }
        return String.join(", ", names);
    }

    private void showPost(Post post) {
        Context context = requireContext();
        Date date = new Date(post.timestamp);
        String when = DateFormat.getMediumDateFormat(context).format(date) + "  "
                + DateFormat.getTimeFormat(context).format(date);
        if (PostStore.isEdited(context, post.uid)) {
            when = when + "  \u00B7  " + getString(R.string.post_edited_label);
        }

        DialogPostBinding card = DialogPostBinding.inflate(LayoutInflater.from(context));
        Bitmap bitmap = post.hasImage() ? ImageStore.thumbnail(context, post.image) : null;
        if (bitmap != null) {
            card.ivDialogImage.setImageBitmap(bitmap);
            card.ivDialogImage.setVisibility(View.VISIBLE);
            card.tvDialogText.setText(post.text);
            card.tvDialogText.setVisibility(post.text.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            card.tvDialogNote.setText(post.text);
            card.tvDialogNote.setVisibility(View.VISIBLE);
        }
        String hearted = heartNames(context, post.uid);
        card.tvDialogDate.setText(hearted.isEmpty()
                ? when
                : when + "\n" + MessageMetaStore.HEART + " " + hearted);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(card.getRoot())
                .create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        card.btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        card.btnDialogEdit.setOnClickListener(v -> {
            dialog.dismiss();
            showEdit(post);
        });
        card.btnDialogDelete.setOnClickListener(v -> {
            PostStore.delete(context, post);
            loadPosts(context);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showEdit(Post post) {
        Context context = requireContext();
        DialogEditPostBinding form = DialogEditPostBinding.inflate(LayoutInflater.from(context));
        form.etEditPost.setText(post.text);
        form.etEditPost.setSelection(post.text.length());
        int[] visibility = {post.visibility};
        Runnable paint = () -> {
            form.tvEditVisEveryone.setSelected(visibility[0] == PostStorage.VISIBILITY_EVERYONE);
            form.tvEditVisFriends.setSelected(visibility[0] == PostStorage.VISIBILITY_FRIENDS);
        };
        form.tvEditVisEveryone.setOnClickListener(v -> {
            visibility[0] = PostStorage.VISIBILITY_EVERYONE;
            paint.run();
        });
        form.tvEditVisFriends.setOnClickListener(v -> {
            visibility[0] = PostStorage.VISIBILITY_FRIENDS;
            paint.run();
        });
        paint.run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.post_edit_title)
                .setView(form.getRoot())
                .create();
        form.btnEditCancel.setOnClickListener(v -> dialog.dismiss());
        form.btnEditSave.setOnClickListener(v -> {
            String text = form.etEditPost.getText().toString().trim();
            if (text.isEmpty() && !post.hasImage()) {
                Toast.makeText(context, R.string.post_empty_error, Toast.LENGTH_SHORT).show();
                return;
            }
            boolean saved = PostStore.update(context, post, text, visibility[0]);
            dialog.dismiss();
            loadPosts(context);
            Toast.makeText(context,
                    saved ? R.string.post_edit_done : R.string.photo_failed,
                    Toast.LENGTH_SHORT).show();
        });
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        form.etEditPost.requestFocus();
    }

    private boolean signedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    private String email() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null || user.getEmail() == null ? "" : user.getEmail();
    }

    private void requireLogin(Runnable action) {
        if (signedIn()) {
            action.run();
            return;
        }
        pendingAction = action;
        authLauncher.launch(new Intent(requireContext(), AuthActivity.class));
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenuInflater().inflate(R.menu.menu_profile, menu.getMenu());
        menu.getMenu().findItem(R.id.action_visibility)
                .setChecked(VisibilityPrefs.isStayVisible(requireContext()));
        MenuItem account = menu.getMenu().findItem(R.id.action_account);
        account.setVisible(signedIn());
        if (signedIn()) {
            account.setTitle(getString(R.string.auth_signed_in, email()));
        }
        MenuItem signOut = menu.getMenu().findItem(R.id.action_signout);
        signOut.setVisible(signedIn());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_account) {
                return true;
            }
            if (id == R.id.action_visibility) {
                toggleVisibility();
                return true;
            }
            if (id == R.id.action_backup) {
                if (!busy) requireLogin(this::confirmBackup);
                return true;
            }
            if (id == R.id.action_restore) {
                if (!busy) requireLogin(this::confirmRestore);
                return true;
            }
            if (id == R.id.action_signout) {
                FirebaseAuth.getInstance().signOut();
                Toast.makeText(requireContext(), R.string.auth_signed_out,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void toggleVisibility() {
        Context context = requireContext();
        boolean on = !VisibilityPrefs.isStayVisible(context);
        VisibilityPrefs.setStayVisible(context, on);
        ListenerService.start(context);
        Toast.makeText(context, on ? R.string.visibility_on : R.string.visibility_off,
                Toast.LENGTH_LONG).show();
    }

    private void openEditor() {
        startActivity(new Intent(requireContext(), EditProfileActivity.class));
    }

    private FirestoreBackup.Progress openProgress(int title) {
        DialogProgressBinding view = DialogProgressBinding.inflate(
                LayoutInflater.from(requireContext()));
        view.tvProgressStep.setText(R.string.progress_prepare);
        view.barProgress.setProgress(0);
        view.tvProgressPercent.setText(getString(R.string.progress_percent, 0));
        progressView = view;
        progressDialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(view.getRoot())
                .setCancelable(false)
                .create();
        progressDialog.show();
        return (percent, label) -> {
            if (view != progressView || !isAdded()) return;
            view.barProgress.setProgress(percent);
            view.tvProgressPercent.setText(getString(R.string.progress_percent, percent));
            view.tvProgressStep.setText(label);
        };
    }

    private void closeProgress() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        progressView = null;
    }

    private void confirmBackup() {
        if (busy) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_confirm_title)
                .setMessage(R.string.backup_confirm_body)
                .setPositiveButton(R.string.backup_action, (dialog, which) -> runBackup())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void runBackup() {
        busy = true;
        FirestoreBackup.Progress progress = openProgress(R.string.progress_backup_title);
        FirestoreBackup.backup(requireContext(), progress, new FirestoreBackup.Callback() {
            @Override
            public void onDone(int count) {
                busy = false;
                closeProgress();
                if (!isAdded()) return;
                if (count == 0) {
                    Toast.makeText(requireContext(), R.string.backup_nothing,
                            Toast.LENGTH_LONG).show();
                } else {
                    showBackupDone(count);
                }
            }

            @Override
            public void onError() {
                busy = false;
                closeProgress();
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.firebase_failed,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showBackupDone(int count) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_done_title)
                .setMessage(getString(R.string.backup_done_body, count, email()))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void confirmRestore() {
        if (busy) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_title)
                .setMessage(getString(R.string.restore_message, email()))
                .setPositiveButton(R.string.restore_action, (dialog, which) -> runRestore())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void runRestore() {
        busy = true;
        FirestoreBackup.Progress progress = openProgress(R.string.progress_restore_title);
        FirestoreBackup.restore(requireContext(), progress, new FirestoreBackup.Callback() {
            @Override
            public void onDone(int count) {
                busy = false;
                closeProgress();
                if (!isAdded()) return;
                String message = count == 0
                        ? getString(R.string.restore_nothing)
                        : getString(R.string.restore_done, count);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                if (count > 0) {
                    refresh();
                }
            }

            @Override
            public void onError() {
                busy = false;
                closeProgress();
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.firebase_failed,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        closeProgress();
        binding = null;
        header = null;
    }
}