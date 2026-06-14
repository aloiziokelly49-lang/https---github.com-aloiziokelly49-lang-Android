package com.cloudink.app.ui.login;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cloudink.app.CloudInkApplication;

public class LoginViewModel extends ViewModel {

    public String phone = "";
    public String password = "";

    private final MutableLiveData<LoginResult> loginResult = new MutableLiveData<>();

    public LiveData<LoginResult> getLoginResult() {
        return loginResult;
    }

    public void onLoginClick() {
        if (phone == null || phone.trim().isEmpty()
            || password == null || password.trim().isEmpty()) {
            
            //如果手机号或密码为空，直接返回登录失败
            loginResult.postValue(LoginResult.FAILURE);
            return;
        }

        if (isValidCredentials(phone.trim(), password)) {
            CloudInkApplication app = CloudInkApplication.getInstance();
            
            //先检查是否是第一次登录
            boolean isFirst = app.getPreferences().isFirstLogin().blockingGet();

            app.getPreferences().setLoggedIn(true, phone.trim());

            //根据是否第一次登录返回不同的结果，
            //登录成功，返回 SUCCESS；
            //首次登录，返回 SUCCESS_FIRST_TIME；
            loginResult.postValue(isFirst ? LoginResult.SUCCESS_FIRST_TIME : LoginResult.SUCCESS);
        } else {
            //如果验证失败，返回登录失败
            loginResult.postValue(LoginResult.FAILURE);
        }
    }

    private boolean isValidCredentials(String phone, String pwd) {
        return phone.length() == 11 && pwd.length() >= 6;
    }

    // 登录结果枚举，表示登录成功、首次登录和登录失败三种状态
    public enum LoginResult {
        SUCCESS,
        SUCCESS_FIRST_TIME,
        FAILURE
    }
}
