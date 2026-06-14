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

        // 自动读取登录状态，已登录则直接进入主页
        if (CloudInkApplication.getInstance().getPreferences().isLoggedIn().blockingGet()) {
            // 已登录，直接进入主页
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }
        // 监听登录状态，
        // 如果登录成功，进入主页；
        // 如果是第一次登录，进入资料完善页；
        // 如果登录失败，显示错误提示。
        observeLoginState();
    }

    private void observeLoginState() {
        // 监听登录结果
        viewModel.getLoginResult().observe(this, result -> {
            if (result == null) return;
            switch (result) {
                case SUCCESS:

                    // 登录成功，进入主页
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                    break;
                case SUCCESS_FIRST_TIME:

                    // 首次登录，进入资料完善页
                    Intent profile = new Intent(this, ProfileActivity.class);
                    profile.putExtra(ProfileActivity.EXTRA_FIRST_TIME, true);
                    startActivity(profile);
                    finish();
                    break;
                case FAILURE:
                    // 登录失败，显示错误提示
                    Toast.makeText(this, "手机号或密码错误", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }
}
