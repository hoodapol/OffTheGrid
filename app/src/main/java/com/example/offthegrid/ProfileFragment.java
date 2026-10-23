package com.example.offthegrid;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.offthegrid.databinding.FragmentProfileBinding;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        binding.btnEditProfile.setOnClickListener(v -> openEditor());
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}