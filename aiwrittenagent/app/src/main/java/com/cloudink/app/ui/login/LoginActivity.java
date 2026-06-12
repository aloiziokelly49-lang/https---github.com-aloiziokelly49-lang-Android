package com.cloudink.app.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.cloudink.app.R;
import com.cloudink.app.databinding.ActivityLoginBinding;
import com.cloudink.app.CloudInkApplication;
import com.cloudink.app.ui.home.HomeActivity;
import com.cloudink.app.ui.profile.ProfileActivity;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_login);
        binding.setViewModel(viewModel);
        binding.setLifecycleOwner(this);

        if (CloudInkApplication.getInstance().getPreferences().isLoggedIn().blockingGet()) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        observeLoginState();
    }

    private void observeLoginState() {
        viewModel.getLoginResult().observe(this, result -> {
            if (result == null) return;
            switch (result) {
                case SUCCESS:
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                    break;
                case SUCCESS_FIRST_TIME:
                    Intent profile = new Intent(this, ProfileActivity.class);
                    profile.putExtra(ProfileActivity.EXTRA_FIRST_TIME, true);
                    startActivity(profile);
                    finish();
                    break;
                case FAILURE:
                    Toast.makeText(this, "手机号或密码错误", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }
}
