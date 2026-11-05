package com.example.offthegrid;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import com.example.offthegrid.databinding.DialogPostBinding;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.offthegrid.databinding.FragmentProfileBinding;
import com.example.offthegrid.databinding.ItemProfileHeaderBinding;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ItemProfileHeaderBinding header;
    private PostAdapter postAdapter;
    private boolean busy;

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
        Context context = requireContext();
        String name = Profile.getName(context);
        header.tvProfileName.setText(name);
        String bio = Profile.getBio(context);
        header.tvProfileBio.setText(bio.isEmpty() ? getString(R.string.profile_hint) : bio);
        showAvatar(context, name);
        showCover(context);
        loadPosts(context);
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

    private void showPost(Post post) {
        Context context = requireContext();
        Date date = new Date(post.timestamp);
        String when = DateFormat.getMediumDateFormat(context).format(date) + "  "
                + DateFormat.getTimeFormat(context).format(date);

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
        card.tvDialogDate.setText(when);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(card.getRoot())
                .create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        card.btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        card.btnDialogDelete.setOnClickListener(v -> {
            PostStore.delete(context, post);
            loadPosts(context);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenuInflater().inflate(R.menu.menu_profile, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_backup) {
                confirmBackup();
                return true;
            }
            if (id == R.id.action_restore) {
                askRestoreCode();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void openEditor() {
        startActivity(new Intent(requireContext(), EditProfileActivity.class));
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
        Toast.makeText(requireContext(), R.string.backup_working, Toast.LENGTH_SHORT).show();
        FirestoreBackup.backup(requireContext(), new FirestoreBackup.Callback() {
            @Override
            public void onDone(int count) {
                busy = false;
                if (!isAdded()) return;
                if (count == 0) {
                    Toast.makeText(requireContext(), R.string.backup_nothing,
                            Toast.LENGTH_LONG).show();
                } else {
                    showBackupCode(count);
                }
            }

            @Override
            public void onError() {
                busy = false;
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.firebase_failed,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showBackupCode(int count) {
        String code = Profile.getId(requireContext());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_done_title)
                .setMessage(getString(R.string.backup_done_body, count, code))
                .setPositiveButton(R.string.backup_copy, (dialog, which) -> copyCode(code))
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }

    private void copyCode(String code) {
        ClipboardManager clipboard = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("backup code", code));
        Toast.makeText(requireContext(), R.string.backup_code_copied, Toast.LENGTH_SHORT).show();
    }

    private void askRestoreCode() {
        if (busy) return;
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.restore_hint);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_title)
                .setMessage(R.string.restore_message)
                .setPositiveButton(R.string.restore_action,
                        (d, which) -> runRestore(input.getText().toString().trim()))
                .setNegativeButton(R.string.cancel, null)
                .create();
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        dialog.setView(input, pad, pad / 2, pad, 0);
        dialog.show();
    }

    private void runRestore(String code) {
        if (code.isEmpty()) {
            Toast.makeText(requireContext(), R.string.restore_code_needed,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        Toast.makeText(requireContext(), R.string.restore_working, Toast.LENGTH_SHORT).show();
        FirestoreBackup.restore(requireContext(), code, new FirestoreBackup.Callback() {
            @Override
            public void onDone(int count) {
                busy = false;
                if (!isAdded()) return;
                String message = count == 0
                        ? getString(R.string.restore_nothing)
                        : getString(R.string.restore_done, count);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError() {
                busy = false;
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.firebase_failed,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        header = null;
    }
}