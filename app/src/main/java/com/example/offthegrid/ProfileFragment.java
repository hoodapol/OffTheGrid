package com.example.offthegrid;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.offthegrid.databinding.FragmentProfileBinding;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private boolean busy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        binding.btnEditProfile.setOnClickListener(v -> openEditor());
        binding.btnBackup.setOnClickListener(v -> confirmBackup());
        binding.btnRestore.setOnClickListener(v -> askRestoreCode());
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        String name = Profile.getName(requireContext());
        binding.tvProfileName.setText(name);
        binding.tvProfileAvatar.setText(
                name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.getDefault()));
    }

    private void openEditor() {
        Intent intent = new Intent(requireContext(), SetupActivity.class);
        intent.putExtra(SetupActivity.EXTRA_EDIT, true);
        startActivity(intent);
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
        input.setText(Profile.getId(requireContext()));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_title)
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
    }
}