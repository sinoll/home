package com.sinoll.home.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.sinoll.home.R;
import com.sinoll.home.data.ApiClient;
import com.sinoll.home.data.AuthApi;
import com.sinoll.home.databinding.ActivityLoginBinding;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvForgot.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
        binding.tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        binding.btnSubmit.setOnClickListener(v -> submit());
        binding.etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
    }

    private void submit() {
        String account = AuthForms.text(binding.etAccount);
        String password = AuthForms.text(binding.etPassword);
        Map<String, TextInputLayout> fields = fields();
        AuthForms.clearErrors(binding.tilAccount, binding.tilPassword);

        boolean valid = true;
        if (account.isEmpty()) {
            binding.tilAccount.setError(getString(R.string.error_required, getString(R.string.hint_account)));
            valid = false;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.error_required, getString(R.string.hint_password)));
            valid = false;
        }
        if (!valid) {
            return;
        }

        AuthForms.setLoading(binding.btnSubmit, binding.progress, true);
        AuthApi.login(this, account, password, response -> {
            if (binding == null) {
                return;
            }
            AuthForms.setLoading(binding.btnSubmit, binding.progress, false);
            if (response.success) {
                Toast.makeText(this, R.string.toast_login_ok, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (!AuthForms.applyFieldErrors(fields, response)) {
                Toast.makeText(this, AuthForms.messageFor(this, response), Toast.LENGTH_LONG).show();
            }
        });
    }

    private Map<String, TextInputLayout> fields() {
        Map<String, TextInputLayout> map = new HashMap<>();
        map.put("username", binding.tilAccount);
        map.put("password", binding.tilPassword);
        return map;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
