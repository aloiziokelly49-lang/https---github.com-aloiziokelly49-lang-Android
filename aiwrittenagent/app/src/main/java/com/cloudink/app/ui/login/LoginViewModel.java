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
            loginResult.postValue(LoginResult.FAILURE);
            return;
        }

        // Local authentication — validate phone + password
        if (isValidCredentials(phone.trim(), password)) {
            CloudInkApplication app = CloudInkApplication.getInstance();
            boolean isFirst = app.getPreferences().isFirstLogin().blockingGet();

            app.getPreferences().setLoggedIn(true, phone.trim());

            loginResult.postValue(isFirst ? LoginResult.SUCCESS_FIRST_TIME : LoginResult.SUCCESS);
        } else {
            loginResult.postValue(LoginResult.FAILURE);
        }
    }

    private boolean isValidCredentials(String phone, String pwd) {
        return phone.length() == 11 && pwd.length() >= 6;
    }

    public enum LoginResult {
        SUCCESS,
        SUCCESS_FIRST_TIME,
        FAILURE
    }
}
